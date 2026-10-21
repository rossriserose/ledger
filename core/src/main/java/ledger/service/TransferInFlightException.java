package ledger.service;

public class TransferInFlightException extends RuntimeException {

    public TransferInFlightException(String key) {
        super("request with idempotency key " + key + " is still in progress, try again later");
    }
}
