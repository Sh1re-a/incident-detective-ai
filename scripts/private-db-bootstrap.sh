#!/usr/bin/env bash

set -Eeuo pipefail

readonly REQUIRED_ENV=(
  BOOTSTRAP_STAGE
  PGHOST
  PGPORT
  PGDATABASE
  DB_ADMIN_PASSWORD
  MIGRATOR_PASSWORD
  RUNTIME_PASSWORD
)

for variable in "${REQUIRED_ENV[@]}"; do
  if [[ -z "${!variable:-}" ]]; then
    printf 'BOOTSTRAP_ERROR=missing_%s\n' "${variable}" >&2
    exit 64
  fi
done

readonly MIGRATOR_ROLE="incident_detective_migrator"
readonly RUNTIME_ROLE="incident_detective_runtime"
readonly MIGRATOR_USER="incident_detective_migrator_login"
readonly RUNTIME_USER="incident_detective_runtime_login"
readonly APP_SCHEMA="incident_detective"

readonly PSQL_BASE=(
  psql
  --no-psqlrc
  --no-password
  --set=ON_ERROR_STOP=1
  --host="${PGHOST}"
  --port="${PGPORT}"
  --dbname="${PGDATABASE}"
)

admin_psql() {
  PGPASSWORD="${DB_ADMIN_PASSWORD}" "${PSQL_BASE[@]}" \
    --username=postgres "$@"
}

migrator_psql() {
  PGPASSWORD="${MIGRATOR_PASSWORD}" "${PSQL_BASE[@]}" \
    --username="${MIGRATOR_USER}" "$@"
}

runtime_psql() {
  PGPASSWORD="${RUNTIME_PASSWORD}" "${PSQL_BASE[@]}" \
    --username="${RUNTIME_USER}" "$@"
}

scalar() {
  "$@" --tuples-only --no-align --quiet
}

assert_equal() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    printf 'BOOTSTRAP_ERROR=%s_expected_%s_got_%s\n' \
      "${label}" "${expected}" "${actual}" >&2
    exit 65
  fi
}

expect_runtime_denied() {
  local label="$1"
  local sql="$2"
  local ignored_output
  if ignored_output="$(runtime_psql --command="${sql}" 2>&1)"; then
    printf 'BOOTSTRAP_ERROR=%s_unexpectedly_allowed\n' "${label}" >&2
    exit 66
  fi
  printf 'DENIED_TEST_%s=PASS\n' "${label}"
}

verify_role_boundaries() {
  admin_psql <<'SQL'
DO $bootstrap$
DECLARE
  role_row RECORD;
BEGIN
  FOR role_row IN
    SELECT expected.name,
           expected.can_login,
           roles.rolname,
           roles.rolcanlogin,
           roles.rolsuper,
           roles.rolcreatedb,
           roles.rolcreaterole,
           roles.rolreplication,
           roles.rolbypassrls
      FROM (
        VALUES
          ('incident_detective_migrator', false),
          ('incident_detective_runtime', false),
          ('incident_detective_migrator_login', true),
          ('incident_detective_runtime_login', true)
      ) AS expected(name, can_login)
      LEFT JOIN pg_roles AS roles ON roles.rolname = expected.name
  LOOP
    IF role_row.rolname IS NULL THEN
      RAISE EXCEPTION 'required role is missing';
    END IF;
    IF role_row.rolcanlogin IS DISTINCT FROM role_row.can_login
       OR role_row.rolsuper
       OR role_row.rolcreatedb
       OR role_row.rolcreaterole
       OR role_row.rolreplication
       OR role_row.rolbypassrls THEN
      RAISE EXCEPTION 'role boundary verification failed';
    END IF;
  END LOOP;

  IF NOT pg_has_role(
      'incident_detective_migrator_login',
      'incident_detective_migrator',
      'MEMBER'
  ) OR pg_has_role(
      'incident_detective_migrator_login',
      'incident_detective_runtime',
      'MEMBER'
  ) THEN
    RAISE EXCEPTION 'migrator role membership verification failed';
  END IF;

  IF NOT pg_has_role(
      'incident_detective_runtime_login',
      'incident_detective_runtime',
      'MEMBER'
  ) OR pg_has_role(
      'incident_detective_runtime_login',
      'incident_detective_migrator',
      'MEMBER'
  ) THEN
    RAISE EXCEPTION 'runtime role membership verification failed';
  END IF;

  IF pg_has_role(
      'incident_detective_migrator_login',
      'cloudsqlsuperuser',
      'MEMBER'
  ) OR pg_has_role(
      'incident_detective_runtime_login',
      'cloudsqlsuperuser',
      'MEMBER'
  ) THEN
    RAISE EXCEPTION 'a workload login belongs to cloudsqlsuperuser';
  END IF;
END
$bootstrap$;
SQL
}

prepare_database() {
  admin_psql <<'SQL'
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

DO $bootstrap$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'incident_detective_migrator'
  ) THEN
    CREATE ROLE incident_detective_migrator
      NOLOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE
      NOREPLICATION NOBYPASSRLS;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'incident_detective_runtime'
  ) THEN
    CREATE ROLE incident_detective_runtime
      NOLOGIN INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE
      NOREPLICATION NOBYPASSRLS;
  END IF;
END
$bootstrap$;

GRANT incident_detective_migrator TO postgres;
CREATE SCHEMA IF NOT EXISTS incident_detective
  AUTHORIZATION incident_detective_migrator;
ALTER SCHEMA incident_detective OWNER TO incident_detective_migrator;

REVOKE CONNECT, TEMPORARY ON DATABASE incident_detective FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE incident_detective
  TO incident_detective_migrator;
GRANT CONNECT ON DATABASE incident_detective
  TO incident_detective_runtime;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA incident_detective FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA incident_detective
  TO incident_detective_migrator;
GRANT USAGE ON SCHEMA incident_detective
  TO incident_detective_runtime;
GRANT USAGE ON SCHEMA public
  TO incident_detective_migrator, incident_detective_runtime;
REVOKE incident_detective_migrator FROM postgres;
SQL

  printf 'BOOTSTRAP_STAGE=USER_CREATION_REQUIRED\n'
}

configure_logins() {
  admin_psql <<'SQL'
ALTER ROLE incident_detective_migrator_login
  IN DATABASE incident_detective
  SET search_path TO incident_detective, public;
ALTER ROLE incident_detective_runtime_login
  IN DATABASE incident_detective
  SET search_path TO incident_detective, public;
SQL

  verify_role_boundaries

  migrator_psql <<'SQL'
ALTER DEFAULT PRIVILEGES IN SCHEMA incident_detective
  REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA incident_detective
  GRANT SELECT ON TABLES TO incident_detective_runtime;
ALTER DEFAULT PRIVILEGES IN SCHEMA incident_detective
  REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA incident_detective
  REVOKE ALL ON SEQUENCES FROM incident_detective_runtime;
SQL

  local migrator_path
  local runtime_path
  migrator_path="$(scalar migrator_psql --command='SHOW search_path')"
  runtime_path="$(scalar runtime_psql --command='SHOW search_path')"
  assert_equal "migrator_search_path" "incident_detective, public" \
    "${migrator_path}"
  assert_equal "runtime_search_path" "incident_detective, public" \
    "${runtime_path}"

  printf 'BOOTSTRAP_STAGE=MIGRATION_REQUIRED\n'
}

verify_runtime() {
  migrator_psql <<'SQL'
REVOKE ALL PRIVILEGES
  ON ALL TABLES IN SCHEMA incident_detective
  FROM incident_detective_runtime;
GRANT SELECT
  ON ALL TABLES IN SCHEMA incident_detective
  TO incident_detective_runtime;
GRANT INSERT, UPDATE
  ON TABLE incident_detective.global_live_daily_quota
  TO incident_detective_runtime;
REVOKE ALL PRIVILEGES
  ON ALL SEQUENCES IN SCHEMA incident_detective
  FROM incident_detective_runtime;
SQL

  verify_role_boundaries

  local flyway_version
  local failed_migrations
  local embedding_rows
  local vector_distance
  local runtime_schema_create
  local runtime_corpus_update
  local runtime_quota_insert
  local runtime_quota_update
  local runtime_quota_delete

  flyway_version="$(scalar runtime_psql --command="
    SELECT max(version::integer)
    FROM incident_detective.flyway_schema_history
    WHERE success
  ")"
  failed_migrations="$(scalar runtime_psql --command="
    SELECT count(*)
    FROM incident_detective.flyway_schema_history
    WHERE NOT success
  ")"
  embedding_rows="$(scalar runtime_psql --command="
    SELECT count(*) FROM incident_detective.runbook_embeddings
  ")"
  vector_distance="$(scalar runtime_psql --command="
    SELECT ('[1,0]'::vector(2) <=> '[1,0]'::vector(2))::text
  ")"

  assert_equal "flyway_version" "7" "${flyway_version}"
  assert_equal "failed_migrations" "0" "${failed_migrations}"
  assert_equal "embedding_rows" "40" "${embedding_rows}"
  assert_equal "vector_distance" "0" "${vector_distance}"

  runtime_psql <<'SQL'
BEGIN;
INSERT INTO incident_detective.global_live_daily_quota (
  quota_day,
  consumed_starts,
  consumed_micro_usd,
  updated_at
) VALUES (
  DATE '2099-12-31',
  0,
  0,
  CURRENT_TIMESTAMP
)
ON CONFLICT (quota_day) DO UPDATE
SET updated_at = EXCLUDED.updated_at;
ROLLBACK;
SQL

  runtime_schema_create="$(scalar runtime_psql --command="
    SELECT has_schema_privilege(
      current_user,
      'incident_detective',
      'CREATE'
    )
  ")"
  runtime_corpus_update="$(scalar runtime_psql --command="
    SELECT has_table_privilege(
      current_user,
      'incident_detective.runbook_embeddings',
      'UPDATE'
    )
  ")"
  runtime_quota_insert="$(scalar runtime_psql --command="
    SELECT has_table_privilege(
      current_user,
      'incident_detective.global_live_daily_quota',
      'INSERT'
    )
  ")"
  runtime_quota_update="$(scalar runtime_psql --command="
    SELECT has_table_privilege(
      current_user,
      'incident_detective.global_live_daily_quota',
      'UPDATE'
    )
  ")"
  runtime_quota_delete="$(scalar runtime_psql --command="
    SELECT has_table_privilege(
      current_user,
      'incident_detective.global_live_daily_quota',
      'DELETE'
    )
  ")"

  assert_equal "runtime_schema_create" "f" "${runtime_schema_create}"
  assert_equal "runtime_corpus_update" "f" "${runtime_corpus_update}"
  assert_equal "runtime_quota_insert" "t" "${runtime_quota_insert}"
  assert_equal "runtime_quota_update" "t" "${runtime_quota_update}"
  assert_equal "runtime_quota_delete" "f" "${runtime_quota_delete}"

  expect_runtime_denied "SCHEMA_CREATE" \
    'CREATE TABLE incident_detective.runtime_must_not_create (id integer)'
  expect_runtime_denied "CORPUS_UPDATE" \
    'UPDATE incident_detective.runbook_embeddings SET title = title WHERE false'
  expect_runtime_denied "QUOTA_DELETE" \
    'DELETE FROM incident_detective.global_live_daily_quota WHERE false'

  local forbidden_table
  forbidden_table="$(scalar migrator_psql --command="
    SELECT to_regclass(
      'incident_detective.runtime_must_not_create'
    ) IS NOT NULL
  ")"
  assert_equal "forbidden_table_exists" "f" "${forbidden_table}"

  printf '%s\n' \
    'BOOTSTRAP_STAGE=COMPLETE' \
    'VECTOR_EXTENSION=READY' \
    'FLYWAY_VERSION=7' \
    'EMBEDDING_ROWS=40' \
    'MIGRATOR_CLOUDSQLSUPERUSER=false' \
    'RUNTIME_CLOUDSQLSUPERUSER=false' \
    'RUNTIME_CORPUS_WRITE=false' \
    'RUNTIME_QUOTA_WRITE=true' \
    'RUNTIME_SCHEMA_CREATE=false'
}

verify_daily_quota() {
  local quota_record
  local quota_day
  local quota_starts
  local quota_micro_usd

  quota_record="$(scalar runtime_psql --command="
    SELECT concat(
      requested.quota_day,
      '|',
      COALESCE(quota.consumed_starts, 0),
      '|',
      COALESCE(quota.consumed_micro_usd, 0)
    )
    FROM (
      SELECT timezone('UTC', CURRENT_TIMESTAMP)::date AS quota_day
    ) AS requested
    LEFT JOIN incident_detective.global_live_daily_quota AS quota
      ON quota.quota_day = requested.quota_day
  ")"
  IFS='|' read -r quota_day quota_starts quota_micro_usd \
    <<<"${quota_record}"

  if [[ -n "${EXPECTED_QUOTA_STARTS:-}" ]]; then
    assert_equal "quota_starts" "${EXPECTED_QUOTA_STARTS}" "${quota_starts}"
  fi
  if [[ -n "${EXPECTED_QUOTA_MICRO_USD:-}" ]]; then
    assert_equal \
      "quota_micro_usd" \
      "${EXPECTED_QUOTA_MICRO_USD}" \
      "${quota_micro_usd}"
  fi

  printf 'QUOTA_DAY=%s\n' "${quota_day}"
  printf 'QUOTA_CONSUMED_STARTS=%s\n' "${quota_starts}"
  printf 'QUOTA_CONSUMED_MICRO_USD=%s\n' "${quota_micro_usd}"
}

case "${BOOTSTRAP_STAGE}" in
  prepare)
    prepare_database
    ;;
  configure)
    configure_logins
    ;;
  verify)
    verify_runtime
    ;;
  quota)
    verify_daily_quota
    ;;
  *)
    printf 'BOOTSTRAP_ERROR=unknown_stage\n' >&2
    exit 64
    ;;
esac
