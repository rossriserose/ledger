package ledger.service;

import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.Money;
import ledger.domain.RejectionReason;
import ledger.domain.TransferResult;
import ledger.domain.TransferResult.Applied;
import ledger.domain.TransferResult.Rejected;
import ledger.memory.InMemoryIdempotencyStore;
import ledger.memory.InMemoryLedgerStore;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceTest {

    private static final AccountId A = AccountId.of("a");
    private static final AccountId B = AccountId.of("b");

    private final InMemoryLedgerStore store = new InMemoryLedgerStore();
    private final LedgerService service = new LedgerService(store, new InMemoryIdempotencyStore());

    @Test
    void transferMovesMoneyAndWritesTwoEntries() {
        open(A, 1000);
        open(B, 0);

        TransferOutcome outcome = transfer(A, B, 300);

        assertThat(outcome.result()).isInstanceOf(Applied.class);
        assertThat(outcome.replayed()).isFalse();
        assertThat(balance(A)).isEqualTo(700);
        assertThat(balance(B)).isEqualTo(300);
        assertThat(service.entries(A)).singleElement().extracting("signedMinorUnits").isEqualTo(-300L);
        assertThat(service.entries(B)).singleElement().extracting("signedMinorUnits").isEqualTo(300L);
    }

    @Test
    void insufficientFundsIsRejected() {
        open(A, 100);
        open(B, 0);

        assertRejected(transfer(A, B, 101), RejectionReason.INSUFFICIENT_FUNDS);
        assertThat(balance(A)).isEqualTo(100);
        assertThat(service.entries(A)).isEmpty();
    }

    @Test
    void wholeBalanceCanBeSent() {
        open(A, 100);
        open(B, 0);

        assertThat(transfer(A, B, 100).result()).isInstanceOf(Applied.class);
        assertThat(balance(A)).isZero();
    }

    @Test
    void selfTransferIsRejected() {
        open(A, 100);

        assertRejected(transfer(A, A, 10), RejectionReason.SELF_TRANSFER);
    }

    @Test
    void zeroAndNegativeAmountsAreRejected() {
        open(A, 100);
        open(B, 0);

        assertRejected(transfer(A, B, 0), RejectionReason.NON_POSITIVE_AMOUNT);
        assertRejected(transfer(A, B, -5), RejectionReason.NON_POSITIVE_AMOUNT);
        assertThat(balance(A)).isEqualTo(100);
    }

    @Test
    void unknownAccountIsRejected() {
        open(A, 100);

        assertRejected(transfer(A, B, 10), RejectionReason.UNKNOWN_ACCOUNT);
        assertRejected(transfer(B, A, 10), RejectionReason.UNKNOWN_ACCOUNT);
        assertThat(balance(A)).isEqualTo(100);
    }

    @Test
    void creditThatWouldOverflowIsRejected() {
        open(A, 100);
        open(B, Long.MAX_VALUE);

        assertRejected(transfer(A, B, 1), RejectionReason.BALANCE_OVERFLOW);
        assertThat(balance(A)).isEqualTo(100);
        assertThat(balance(B)).isEqualTo(Long.MAX_VALUE);
        assertThat(service.entries(A)).isEmpty();
    }

    @Test
    void crossCurrencyTransferIsRejected() {
        open(A, 100);
        service.openAccount(B, Money.of(0, "USD"));

        assertRejected(transfer(A, B, 10), RejectionReason.CURRENCY_MISMATCH);
        assertThat(balance(A)).isEqualTo(100);
    }

    @Test
    void negativeOpeningBalanceIsNotAllowed() {
        assertThatThrownBy(() -> open(A, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameKeyTwiceReturnsSameTransferAndMovesMoneyOnce() {
        open(A, 1000);
        open(B, 0);
        var command = new TransferCommand("key-1", A, B, 100);

        TransferOutcome first = service.transfer(command);
        TransferOutcome second = service.transfer(command);

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.result()).isEqualTo(first.result());
        assertThat(balance(A)).isEqualTo(900);
        assertThat(service.entries(A)).hasSize(1);
    }

    @Test
    void rejectionIsRememberedForTheKey() {
        open(A, 50);
        open(B, 0);
        var command = new TransferCommand("key-1", A, B, 100);

        TransferOutcome first = service.transfer(command);
        service.openAccount(AccountId.of("rich"), Money.of(1000, "EUR"));
        transfer(AccountId.of("rich"), A, 1000);
        TransferOutcome retry = service.transfer(command);

        assertRejected(first, RejectionReason.INSUFFICIENT_FUNDS);
        assertThat(retry.result()).isEqualTo(first.result());
        assertThat(balance(A)).isEqualTo(1050);
    }

    @Test
    void sameKeyWithDifferentPayloadIsAConflict() {
        open(A, 1000);
        open(B, 0);
        service.transfer(new TransferCommand("key-1", A, B, 100));

        assertThatThrownBy(() -> service.transfer(new TransferCommand("key-1", A, B, 200)))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(balance(A)).isEqualTo(900);
    }

    private void open(AccountId id, long balance) {
        service.openAccount(id, Money.of(balance, "EUR"));
    }

    private TransferOutcome transfer(AccountId from, AccountId to, long amount) {
        return service.transfer(new TransferCommand(UUID.randomUUID().toString(), from, to, amount));
    }

    private long balance(AccountId id) {
        return service.account(id).map(Account::balance).map(Money::minorUnits).orElseThrow();
    }

    private static void assertRejected(TransferOutcome outcome, RejectionReason reason) {
        TransferResult result = outcome.result();
        assertThat(result).isInstanceOf(Rejected.class);
        assertThat(((Rejected) result).reason()).isEqualTo(reason);
    }
}
