package ledger.port;

import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.LedgerEntry;

import java.util.Optional;

public interface LedgerTransaction {

    Optional<Account> load(AccountId id);

    void save(Account account);

    void append(LedgerEntry entry);
}
