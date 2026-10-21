package ledger.api;

import ledger.domain.Account;
import ledger.domain.LedgerEntry;

final class Dto {

    private Dto() {
    }

    record CreateAccountRequest(String id, Long initialBalance, String currency) {
    }

    record AccountResponse(String id, long balance, String currency) {

        static AccountResponse of(Account account) {
            return new AccountResponse(account.id().value(), account.balance().minorUnits(),
                    account.balance().currency().getCurrencyCode());
        }
    }

    record EntryResponse(String transferId, String accountId, long amount, String occurredAt) {

        static EntryResponse of(LedgerEntry entry) {
            return new EntryResponse(entry.transferId().value(), entry.accountId().value(),
                    entry.signedMinorUnits(), entry.occurredAt().toString());
        }
    }

    record ErrorResponse(String error, String message) {
    }
}
