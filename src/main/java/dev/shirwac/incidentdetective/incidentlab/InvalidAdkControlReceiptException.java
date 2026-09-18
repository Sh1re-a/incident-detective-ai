package dev.shirwac.incidentdetective.incidentlab;

/** Raised when an ADK result lacks proof of the Incident Lab safety boundary. */
public final class InvalidAdkControlReceiptException extends RuntimeException {

    InvalidAdkControlReceiptException() {
        super("Incident Lab received a missing or unsafe ADK control receipt");
    }
}
