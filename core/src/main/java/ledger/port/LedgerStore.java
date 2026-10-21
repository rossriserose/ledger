package ledger.port;

import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.LedgerEntry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LedgerStore {

    /**
     * Runs the work atomically, with exclusive access to exactly these accounts.
     * Either all saves and appends of the work become visible, or none of them.
     * <p>
     * In memory: per-account locks taken in sorted order.
     * In SQL: BEGIN; SELECT ... FOR UPDATE ordered by id; work; COMMIT.
     */
    <T> T inTransaction(Set<AccountId> accounts, TransactionalWork<T> work);

    Optional<Account> find(AccountId id);

    /**
     * @throws DuplicateAccountException when an account with this id already exists
     */
    void createAccount(Account account);

    List<LedgerEntry> entries(AccountId id);
}
