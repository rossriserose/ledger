package ledger.domain;

import java.time.Instant;
import java.util.List;

public record Transfer(TransferId id, AccountId from, AccountId to, Money amount, Instant occurredAt) {

    public List<LedgerEntry> entries() {
        return List.of(
                new LedgerEntry(id, from, -amount.minorUnits(), occurredAt),
                new LedgerEntry(id, to, amount.minorUnits(), occurredAt));
    }
}
