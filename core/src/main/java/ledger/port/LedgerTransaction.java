package ledger.port;

import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.LedgerEntry;

import java.util.Optional;

/**
 * Handle given to the work inside {@link LedgerStore#inTransaction}.
 * Only the accounts named for the transaction can be loaded or saved.
 */
public interface LedgerTransaction {

    Optional<Account> load(AccountId id);

    void save(Account account);

    void append(LedgerEntry entry);
}
