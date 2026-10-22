package ledger.domain;

import java.time.Instant;

public record LedgerEntry(
        TransferId transferId,
        AccountId accountId,
        long signedMinorUnits,
        Instant occurredAt) {
}
