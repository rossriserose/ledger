package ledger.service;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("idempotency key " + key + " was already used with a different request");
    }
}
