package ledger.service;

import ledger.domain.RejectionReason;
import ledger.domain.TransferResult;
import ledger.domain.TransferResult.Rejected;
import ledger.memory.InMemoryIdempotencyStore;
import ledger.port.IdempotencyRecord.InFlight;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyRegistryTest {

    private static final TransferResult RESULT = new Rejected(RejectionReason.INSUFFICIENT_FUNDS, "no money");

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final IdempotencyRegistry registry = new IdempotencyRegistry(store, Duration.ofMillis(200));

    @Test
    void secondCallReplaysWithoutRunningWorkAgain() {
        AtomicInteger runs = new AtomicInteger();
        Supplier<TransferResult> work = () -> {
            runs.incrementAndGet();
            return RESULT;
        };

        TransferOutcome first = registry.execute("k", "f", work);
        TransferOutcome second = registry.execute("k", "f", work);

        assertThat(first).isEqualTo(new TransferOutcome(RESULT, false));
        assertThat(second).isEqualTo(new TransferOutcome(RESULT, true));
        assertThat(runs).hasValue(1);
    }

    @Test
    void differentFingerprintIsAConflict() {
        registry.execute("k", "f1", () -> RESULT);

        assertThatThrownBy(() -> registry.execute("k", "f2", () -> RESULT))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void unexpectedFailureFreesTheKey() {
        assertThatThrownBy(() -> registry.execute("k", "f", () -> {
            throw new IllegalStateException("db down");
        })).hasMessage("db down");

        assertThat(registry.execute("k", "f", () -> RESULT)).isEqualTo(new TransferOutcome(RESULT, false));
    }

    @Test
    void errorAlsoFreesTheKey() {
        assertThatThrownBy(() -> registry.execute("k", "f", () -> {
            throw new StackOverflowError();
        })).isInstanceOf(StackOverflowError.class);

        assertThat(registry.execute("k", "f", () -> RESULT)).isEqualTo(new TransferOutcome(RESULT, false));
    }

    @Test
    void retryGivesUpWhenOriginalTakesTooLong() {
        store.putIfAbsent("k", new InFlight(new CompletableFuture<>(), "f"));

        assertThatThrownBy(() -> registry.execute("k", "f", () -> RESULT))
                .isInstanceOf(TransferInFlightException.class);
    }
}
