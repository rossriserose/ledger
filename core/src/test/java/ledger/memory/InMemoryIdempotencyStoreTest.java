package ledger.memory;

import ledger.domain.RejectionReason;
import ledger.domain.TransferResult.Rejected;
import ledger.port.IdempotencyRecord;
import ledger.port.IdempotencyRecord.Settled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyStoreTest {

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final IdempotencyRecord first = new Settled(new Rejected(RejectionReason.SELF_TRANSFER, "first"), "f1");
    private final IdempotencyRecord second = new Settled(new Rejected(RejectionReason.SELF_TRANSFER, "second"), "f2");

    @Test
    void onlyFirstClaimWins() {
        assertThat(store.putIfAbsent("k", first)).isEmpty();
        assertThat(store.putIfAbsent("k", second)).contains(first);
    }

    @Test
    void removedKeyCanBeClaimedAgain() {
        store.putIfAbsent("k", first);
        store.remove("k");

        assertThat(store.putIfAbsent("k", second)).isEmpty();
    }
}
