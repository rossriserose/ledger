package ledger.service;

import ledger.domain.AccountId;
import ledger.domain.Money;
import ledger.memory.InMemoryIdempotencyStore;
import ledger.memory.InMemoryLedgerStore;
import org.junit.jupiter.api.RepeatedTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DeadlockTest {

    private static final AccountId A = AccountId.of("a");
    private static final AccountId B = AccountId.of("b");
    private static final int THREADS_PER_DIRECTION = 4;
    private static final int ITERATIONS = 2_000;

    @RepeatedTest(20)
    void oppositeDirectionsDoNotDeadlock() {
        var service = new LedgerService(new InMemoryLedgerStore(), new InMemoryIdempotencyStore());
        service.openAccount(A, Money.of(1_000_000, "EUR"));
        service.openAccount(B, Money.of(1_000_000, "EUR"));

        ExecutorService forward = Executors.newFixedThreadPool(THREADS_PER_DIRECTION);
        ExecutorService backward = Executors.newFixedThreadPool(THREADS_PER_DIRECTION);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> workers = new ArrayList<>();
        for (int i = 0; i < THREADS_PER_DIRECTION; i++) {
            workers.add(forward.submit(() -> loop(service, start, A, B)));
            workers.add(backward.submit(() -> loop(service, start, B, A)));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            start.countDown();
            for (Future<?> worker : workers) {
                worker.get();
            }
        });
        forward.shutdownNow();
        backward.shutdownNow();

        Invariants.check(service, Map.of(A, 1_000_000L, B, 1_000_000L));
    }

    private static Void loop(LedgerService service, CountDownLatch start, AccountId from, AccountId to)
            throws InterruptedException {
        start.await();
        for (int i = 0; i < ITERATIONS; i++) {
            service.transfer(new TransferCommand(UUID.randomUUID().toString(), from, to, 1));
        }
        return null;
    }
}
