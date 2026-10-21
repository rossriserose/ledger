package ledger.port;

import ledger.domain.TransferResult;

import java.util.concurrent.CompletableFuture;

public sealed interface IdempotencyRecord {

    String fingerprint();

    record InFlight(CompletableFuture<TransferResult> result, String fingerprint) implements IdempotencyRecord {
    }

    record Settled(TransferResult result, String fingerprint) implements IdempotencyRecord {
    }
}
