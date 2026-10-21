package ledger.service;

import ledger.domain.AccountId;
import ledger.domain.LedgerEntry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class Invariants {

    private Invariants() {
    }

    /**
     * Money is conserved, nothing is negative, all entries sum to zero and every
     * balance equals its opening balance plus its own entries.
     */
    static void check(LedgerService service, Map<AccountId, Long> openingBalances) {
        long expectedTotal = openingBalances.values().stream().mapToLong(Long::longValue).sum();
        long total = 0;
        long entriesSum = 0;

        for (var opening : openingBalances.entrySet()) {
            long balance = balance(service, opening.getKey());
            long ownEntries = service.entries(opening.getKey()).stream()
                    .mapToLong(LedgerEntry::signedMinorUnits)
                    .sum();

            assertThat(balance).as("balance of %s", opening.getKey()).isNotNegative();
            assertThat(balance).as("balance of %s vs its entries", opening.getKey())
                    .isEqualTo(opening.getValue() + ownEntries);
            total += balance;
            entriesSum += ownEntries;
        }

        assertThat(total).as("total money").isEqualTo(expectedTotal);
        assertThat(entriesSum).as("sum of all entries").isZero();
    }

    static long balance(LedgerService service, AccountId id) {
        return service.account(id).orElseThrow().balance().minorUnits();
    }

    static long entryCount(LedgerService service, Iterable<AccountId> ids) {
        long count = 0;
        for (AccountId id : ids) {
            count += service.entries(id).size();
        }
        return count;
    }
}
