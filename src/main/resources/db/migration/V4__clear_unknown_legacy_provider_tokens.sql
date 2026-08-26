UPDATE runbook_embeddings
SET provider_input_tokens = NULL
WHERE provider_input_tokens = 0
  AND input_characters > 0;
