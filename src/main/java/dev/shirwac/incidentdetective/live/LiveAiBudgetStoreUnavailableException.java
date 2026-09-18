package dev.shirwac.incidentdetective.live;

import org.springframework.dao.DataAccessException;

/** Fails live AI closed when the shared monetary guard cannot be reached. */
public final class LiveAiBudgetStoreUnavailableException
        extends RuntimeException {

    LiveAiBudgetStoreUnavailableException(DataAccessException cause) {
        super("The shared live AI budget store is unavailable", cause);
    }
}
