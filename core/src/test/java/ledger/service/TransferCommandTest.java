package ledger.service;

import ledger.domain.AccountId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferCommandTest {

    private static final AccountId A = AccountId.of("a");
    private static final AccountId B = AccountId.of("b");

    @Test
    void blankKeyIsRejected() {
        assertThatThrownBy(() -> new TransferCommand(" ", A, B, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fingerprintDependsOnPayloadOnly() {
        String fingerprint = new TransferCommand("k1", A, B, 10).fingerprint();

        assertThat(new TransferCommand("k2", A, B, 10).fingerprint()).isEqualTo(fingerprint);
        assertThat(new TransferCommand("k1", A, B, 11).fingerprint()).isNotEqualTo(fingerprint);
        assertThat(new TransferCommand("k1", B, A, 10).fingerprint()).isNotEqualTo(fingerprint);
    }
}
