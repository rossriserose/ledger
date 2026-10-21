package ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountIdTest {

    @Test
    void blankIdIsRejected() {
        assertThatThrownBy(() -> AccountId.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sortsByValue() {
        List<AccountId> ids = new ArrayList<>(List.of(AccountId.of("c"), AccountId.of("a"), AccountId.of("b")));

        Collections.sort(ids);

        assertThat(ids).containsExactly(AccountId.of("a"), AccountId.of("b"), AccountId.of("c"));
    }

    @Test
    void randomTransferIdsAreUnique() {
        assertThat(TransferId.random()).isNotEqualTo(TransferId.random());
    }
}
