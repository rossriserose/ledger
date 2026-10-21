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
        return from + "->" + to + ":" + amount;
    }
}
