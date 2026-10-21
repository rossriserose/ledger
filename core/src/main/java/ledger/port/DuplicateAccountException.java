package ledger.port;

import ledger.domain.AccountId;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(AccountId id) {
        super("account already exists: " + id);
    }
}
