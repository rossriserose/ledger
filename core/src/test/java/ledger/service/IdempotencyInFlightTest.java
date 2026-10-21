package ledger.service;

import ledger.domain.AccountId;
import ledger.domain.Money;
import ledger.domain.TransferResult.Applied;
import ledger.memory.InMemoryIdempotencyStore;
import ledger.memory.InMemoryLedgerStore;
import ledger.port.IdempotencyRecord;
import ledger.port.IdempotencyStore;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyInFlightTest {

    private static final AccountId A = AccountId.of("a");
    private static final AccountId B = AccountId.of("b");
    private static final int REQUESTS = 8;

    @RepeatedTest(20)
    void retriesThatArriveWhileOriginalIsRunningApplyOnce() throws Exception {
        var gated = new GatedLedgerStore(new InMemoryLedgerStore(), A);
        var idempotency = new InFlightCountingStore(new InMemoryIdempotencyStore(), REQUESTS - 1);
        var service = new LedgerService(gated, idempotency);
        service.openAccount(A, Money.of(1000, "EUR"));
        service.openAccount(B, Money.of(0, "EUR"));
        var command = new TransferCommand("same-key", A, B, 100);

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        List<Future<TransferOutcome>> futures = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            futures.add(pool.submit(() -> service.transfer(command)));
        }

        gated.awaitWorkerInside();
        assertThat(idempotency.retriesSawInFlight.await(5, TimeUnit.SECONDS))
                .as("all retries arrived while the original was in flight")
                .isTrue();
        gated.release();

        List<TransferOutcome> outcomes = new ArrayList<>();
        for (Future<TransferOutcome> future : futures) {
            outcomes.add(future.get(5, TimeUnit.SECONDS));
        }
        pool.shutdown();

        TransferOutcome original = outcomes.stream().filter(o -> !o.replayed()).findFirst().orElseThrow();
        assertThat(original.result()).isInstanceOf(Applied.class);
        assertThat(outcomes).filteredOn(TransferOutcome::replayed).hasSize(REQUESTS - 1);
        assertThat(outcomes).extracting(TransferOutcome::result).containsOnly(original.result());
        assertThat(gated.transactions()).isEqualTo(1);
        assertThat(service.entries(A)).hasSize(1);
        assertThat(Invariants.balance(service, A)).isEqualTo(900);
        assertThat(Invariants.balance(service, B)).isEqualTo(100);
        Invariants.check(service, Map.of(A, 1000L, B, 0L));
    }

    private static final class InFlightCountingStore implements IdempotencyStore {

        private final IdempotencyStore delegate;
        private final CountDownLatch retriesSawInFlight;

        InFlightCountingStore(IdempotencyStore delegate, int expectedRetries) {
            this.delegate = delegate;
            this.retriesSawInFlight = new CountDownLatch(expectedRetries);
        }

        @Override
        public Optional<IdempotencyRecord> putIfAbsent(String key, IdempotencyRecord record) {
            Optional<IdempotencyRecord> existing = delegate.putIfAbsent(key, record);
            if (existing.isPresent() && existing.get() instanceof IdempotencyRecord.InFlight) {
                retriesSawInFlight.countDown();
            }
            return existing;
        }

        @Override
        public void put(String key, IdempotencyRecord record) {
            delegate.put(key, record);
        }

        @Override
        public void remove(String key) {
            delegate.remove(key);
        }
    }
}
