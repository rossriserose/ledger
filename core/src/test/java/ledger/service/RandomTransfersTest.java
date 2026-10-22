package ledger.service;

import ledger.domain.AccountId;
import ledger.domain.Money;
import ledger.memory.InMemoryIdempotencyStore;
import ledger.memory.InMemoryLedgerStore;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class RandomTransfersTest {

    private static final int THREADS = 8;
    private static final int COMMANDS_PER_THREAD = 500;

    @RepeatedTest(20)
    void invariantsHoldForRandomCommands(RepetitionInfo repetition) throws Exception {
        long seed = repetition.getCurrentRepetition();
        var service = new LedgerService(new InMemoryLedgerStore(), new InMemoryIdempotencyStore());
        Random setup = new Random(seed);
        Map<AccountId, Long> opening = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            AccountId id = AccountId.of("acc-" + i);
            long balance = setup.nextInt(5_000);
            service.openAccount(id, Money.of(balance, "EUR"));
            opening.put(id, balance);
        }
        List<AccountId> ids = new ArrayList<>(opening.keySet());
        ids.add(AccountId.of("missing"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> workers = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Random random = new Random(seed * 1_000 + t);
            workers.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < COMMANDS_PER_THREAD; i++) {
                    AccountId from = ids.get(random.nextInt(ids.size()));
                    AccountId to = ids.get(random.nextInt(ids.size()));
                    long amount = random.nextInt(1_200) - 100;
                    String key = from + "-" + to + "-" + amount + "-" + random.nextInt(50);
                    service.transfer(new TransferCommand(key, from, to, amount));
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> worker : workers) {
            worker.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Invariants.check(service, opening);
    }
}
