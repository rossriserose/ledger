package ledger.service;

import ledger.domain.TransferResult;
import ledger.port.IdempotencyRecord;
import ledger.port.IdempotencyRecord.InFlight;
import ledger.port.IdempotencyRecord.Settled;
import ledger.port.IdempotencyStore;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

final class IdempotencyRegistry {

    private final IdempotencyStore store;
    private final Duration inFlightTimeout;

    IdempotencyRegistry(IdempotencyStore store, Duration inFlightTimeout) {
        this.store = store;
        this.inFlightTimeout = inFlightTimeout;
    }

    TransferOutcome execute(String key, String fingerprint, Supplier<TransferResult> work) {
        var claim = new InFlight(new CompletableFuture<>(), fingerprint);
        Optional<IdempotencyRecord> existing = store.putIfAbsent(key, claim);
        if (existing.isEmpty()) {
            return new TransferOutcome(runAsOwner(key, claim, work), false);
        }

        IdempotencyRecord record = existing.get();
        if (!record.fingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(key);
        }
        if (record instanceof Settled settled) {
            return new TransferOutcome(settled.result(), true);
        }
        return new TransferOutcome(awaitOwner(key, (InFlight) record), true);
    }

    private TransferResult runAsOwner(String key, InFlight claim, Supplier<TransferResult> work) {
        try {
            TransferResult result = work.get();
            store.put(key, new Settled(result, claim.fingerprint()));
            claim.result().complete(result);
            return result;
        } catch (RuntimeException | Error e) {
            store.remove(key);
            claim.result().completeExceptionally(e);
            throw e;
        }
    }

    private TransferResult awaitOwner(String key, InFlight inFlight) {
        try {
            return inFlight.result().get(inFlightTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new TransferInFlightException(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransferInFlightException(key);
        } catch (ExecutionException e) {
            throw new IllegalStateException("original request with key " + key + " failed", e.getCause());
        }
    }
}
