package ledger.service;

import ledger.domain.AccountId;
import ledger.domain.Money;
import ledger.domain.TransferResult.Applied;
import ledger.memory.InMemoryIdempotencyStore;
import ledger.memory.InMemoryLedgerStore;
import org.junit.jupiter.api.RepeatedTest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ContentionTest {

    private static final int THREADS = 32;
    private static final int TRANSFERS_PER_THREAD = 300;

    @RepeatedTest(20)
    void noLostUpdatesUnderContention() throws Exception {
        var service = new LedgerService(new InMemoryLedgerStore(), new InMemoryIdempotencyStore());
        Map<AccountId, Long> opening = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            AccountId id = AccountId.of("acc-" + i);
            service.openAccount(id, Money.of(10_000, "EUR"));
            opening.put(id, 10_000L);
        }
        List<AccountId> ids = List.copyOf(opening.keySet());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Integer>> workers = IntStream.range(0, THREADS)
                .mapToObj(t -> pool.submit(() -> {
                    start.await();
                    var random = ThreadLocalRandom.current();
                    int applied = 0;
                    for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                        AccountId from = ids.get(random.nextInt(ids.size()));
                        AccountId to = ids.get(random.nextInt(ids.size()));
                        if (from.equals(to)) {
                            continue;
                        }
                        var command = new TransferCommand(UUID.randomUUID().toString(), from, to, random.nextLong(1, 500));
                        if (service.transfer(command).result() instanceof Applied) {
                            applied++;
                        }
                    }
                    return applied;
                }))
                .toList();

        start.countDown();
        int applied = 0;
        for (Future<Integer> worker : workers) {
            applied += worker.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(applied).isPositive();
        assertThat(Invariants.entryCount(service, ids)).isEqualTo(2L * applied);
        Invariants.check(service, opening);
    }
}
