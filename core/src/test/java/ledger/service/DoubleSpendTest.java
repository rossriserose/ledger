package ledger.service;

import ledger.domain.AccountId;
import ledger.domain.Money;
import ledger.domain.RejectionReason;
import ledger.domain.TransferResult;
import ledger.domain.TransferResult.Applied;
import ledger.domain.TransferResult.Rejected;
import ledger.memory.InMemoryIdempotencyStore;
import ledger.memory.InMemoryLedgerStore;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DoubleSpendTest {

    private static final AccountId SOURCE = AccountId.of("source");
    private static final AccountId TARGET = AccountId.of("target");

    @RepeatedTest(20)
    void balanceIsNeverSpentTwice() throws Exception {
        var service = new LedgerService(new InMemoryLedgerStore(), new InMemoryIdempotencyStore());
        service.openAccount(SOURCE, Money.of(100, "EUR"));
        service.openAccount(TARGET, Money.of(0, "EUR"));

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong lowestSeen = new AtomicLong(Long.MAX_VALUE);
        Thread observer = new Thread(() -> {
            while (running.get()) {
                lowestSeen.accumulateAndGet(Invariants.balance(service, SOURCE), Math::min);
            }
        });
        observer.start();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<TransferResult>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            var command = new TransferCommand("spend-" + i, SOURCE, TARGET, 10);
            futures.add(pool.submit(() -> {
                start.await();
                return service.transfer(command).result();
            }));
        }
        start.countDown();

        int applied = 0;
        int insufficient = 0;
        for (Future<TransferResult> future : futures) {
            TransferResult result = future.get(10, TimeUnit.SECONDS);
            if (result instanceof Applied) {
                applied++;
            } else if (result instanceof Rejected rejected && rejected.reason() == RejectionReason.INSUFFICIENT_FUNDS) {
                insufficient++;
            }
        }
        pool.shutdown();
        running.set(false);
        observer.join();

        assertThat(applied).isEqualTo(10);
        assertThat(insufficient).isEqualTo(40);
        assertThat(Invariants.balance(service, SOURCE)).isZero();
        assertThat(lowestSeen.get()).isNotNegative();
        Invariants.check(service, Map.of(SOURCE, 100L, TARGET, 0L));
    }
}
