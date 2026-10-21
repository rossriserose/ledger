package ledger.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TransferTest {

    private static final AccountId A = AccountId.of("a");
    private static final AccountId B = AccountId.of("b");

    @Test
    void producesDebitAndCreditThatSumToZero() {
        Transfer transfer = new Transfer(TransferId.random(), A, B, Money.of(250, "EUR"), Instant.EPOCH);

        assertThat(transfer.entries())
                .extracting(LedgerEntry::accountId, LedgerEntry::signedMinorUnits)
                .containsExactly(tuple(A, -250L), tuple(B, 250L));
        assertThat(transfer.entries().stream().mapToLong(LedgerEntry::signedMinorUnits).sum()).isZero();
    }
}
