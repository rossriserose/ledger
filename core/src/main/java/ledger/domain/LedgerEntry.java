package ledger.domain;

import java.time.Instant;

/**
 * One side of a transfer. Negative amount is a debit, positive is a credit.
 * Every transfer writes exactly two entries and they sum to zero.
 */
public record LedgerEntry(
        TransferId transferId,
        AccountId accountId,
        long signedMinorUnits,
        Instant occurredAt) {
}
