package ledger.service;

import ledger.domain.AccountId;

import java.util.Objects;

public record TransferCommand(String idempotencyKey, AccountId from, AccountId to, long amount) {

    public TransferCommand {
        Objects.requireNonNull(idempotencyKey, "idempotency key");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }

    String fingerprint() {
        // Length prefix, so ids that contain "->" or ":" can not give the same text.
        return from.value().length() + ":" + from + "->" + to.value().length() + ":" + to + ":" + amount;
    }
}
