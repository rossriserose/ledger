package ledger.service;

import ledger.domain.AccountId;
import ledger.domain.Money;
import ledger.domain.TransferResult.Applied;
import ledger.memory.InMemoryIdempotencyStore;
import ledger.memory.InMemoryLedgerStore;
import org.junit.jupiter.api.RepeatedTest;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ParallelismTest {

    private static final AccountId A = AccountId.of("a");
    private static final AccountId B = AccountId.of("b");
    private static final AccountId C = AccountId.of("c");
    private static final AccountId D = AccountId.of("d");

    @RepeatedTest(20)
    void transferOnOtherAccountsFinishesWhileFirstOneIsHeld() throws Exception {
        var gated = new GatedLedgerStore(new InMemoryLedgerStore(), A);
        var service = new LedgerService(gated, new InMemoryIdempotencyStore());
        for (AccountId id : new AccountId[] {A, B, C, D}) {
            service.openAccount(id, Money.of(1000, "EUR"));
        }

        var held = CompletableFuture.supplyAsync(() -> service.transfer(new TransferCommand("k1", A, B, 100)));
        gated.awaitWorkerInside();

        TransferOutcome other = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> service.transfer(new TransferCommand("k2", C, D, 100)));

        assertThat(other.result()).isInstanceOf(Applied.class);
        assertThat(held).as("A->B is still inside its transaction").isNotDone();

        gated.release();
        assertThat(held.get(5, TimeUnit.SECONDS).result()).isInstanceOf(Applied.class);
    }
}
