package ledger.memory;

import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.LedgerEntry;
import ledger.domain.Money;
import ledger.domain.TransferId;
import ledger.port.DuplicateAccountException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryLedgerStoreTest {

    private static final AccountId A = AccountId.of("a");
    private static final AccountId B = AccountId.of("b");

    private final InMemoryLedgerStore store = new InMemoryLedgerStore();

    @Test
    void savesBecomeVisibleAfterCommit() {
        store.createAccount(new Account(A, Money.of(100, "EUR")));

        store.inTransaction(Set.of(A), tx -> {
            tx.save(tx.load(A).orElseThrow().credit(Money.of(5, "EUR")));
            tx.append(new LedgerEntry(TransferId.random(), A, 5, Instant.EPOCH));
            return null;
        });

        assertThat(store.find(A)).contains(new Account(A, Money.of(105, "EUR")));
        assertThat(store.entries(A)).hasSize(1);
    }

    @Test
    void nothingIsWrittenWhenWorkFails() {
        store.createAccount(new Account(A, Money.of(100, "EUR")));

        assertThatThrownBy(() -> store.inTransaction(Set.of(A), tx -> {
            tx.save(tx.load(A).orElseThrow().credit(Money.of(5, "EUR")));
            tx.append(new LedgerEntry(TransferId.random(), A, 5, Instant.EPOCH));
            throw new IllegalStateException("boom");
        })).hasMessage("boom");

        assertThat(store.find(A)).contains(new Account(A, Money.of(100, "EUR")));
        assertThat(store.entries(A)).isEmpty();
    }

    @Test
    void unknownAccountLoadsAsEmpty() {
        Optional<Account> loaded = store.inTransaction(Set.of(A), tx -> tx.load(A));

        assertThat(loaded).isEmpty();
    }

    @Test
    void workCanNotTouchAccountsOutsideTheTransaction() {
        store.createAccount(new Account(A, Money.of(100, "EUR")));
        store.createAccount(new Account(B, Money.of(100, "EUR")));

        assertThatThrownBy(() -> store.inTransaction(Set.of(A), tx -> tx.load(B)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.inTransaction(Set.of(A), tx -> {
            tx.save(new Account(B, Money.of(0, "EUR")));
            return null;
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicateAccountIsRejected() {
        store.createAccount(new Account(A, Money.of(100, "EUR")));

        assertThatThrownBy(() -> store.createAccount(new Account(A, Money.of(1, "EUR"))))
                .isInstanceOf(DuplicateAccountException.class);
    }
}
