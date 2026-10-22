package ledger.port;

import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.LedgerEntry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LedgerStore {

    // All saves and appends of the work become visible, or none of them.
    <T> T inTransaction(Set<AccountId> accounts, TransactionalWork<T> work);

    Optional<Account> find(AccountId id);

    void createAccount(Account account);

    List<LedgerEntry> entries(AccountId id);
}
