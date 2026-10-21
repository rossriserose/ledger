package ledger.api;

import ledger.domain.Account;
import ledger.domain.LedgerEntry;
import ledger.domain.Transfer;

final class Dto {

    private Dto() {
    }

    record CreateAccountRequest(String id, Long initialBalance, String currency) {
    }

    record TransferRequest(String from, String to, Long amount) {
    }

    record AccountResponse(String id, long balance, String currency) {

        static AccountResponse of(Account account) {
            return new AccountResponse(account.id().value(), account.balance().minorUnits(),
                    account.balance().currency().getCurrencyCode());
        }
    }

    record TransferResponse(String transferId, String status, String from, String to,
                            long amount, String currency, String occurredAt) {

        static TransferResponse of(Transfer transfer) {
            return new TransferResponse(transfer.id().value(), "APPLIED", transfer.from().value(),
                    transfer.to().value(), transfer.amount().minorUnits(),
                    transfer.amount().currency().getCurrencyCode(), transfer.occurredAt().toString());
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
