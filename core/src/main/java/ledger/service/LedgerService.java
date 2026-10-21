package ledger.service;

import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.LedgerEntry;
import ledger.domain.Money;
import ledger.domain.RejectionReason;
import ledger.domain.Transfer;
import ledger.domain.TransferId;
import ledger.domain.TransferResult;
import ledger.domain.TransferResult.Applied;
import ledger.domain.TransferResult.Rejected;
import ledger.port.IdempotencyStore;
import ledger.port.LedgerStore;
import ledger.port.LedgerTransaction;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LedgerService {

    private static final Duration DEFAULT_IN_FLIGHT_TIMEOUT = Duration.ofSeconds(10);

    private final LedgerStore store;
    private final IdempotencyRegistry idempotency;
    private final Clock clock;

    public LedgerService(LedgerStore store, IdempotencyStore idempotencyStore) {
        this(store, idempotencyStore, Clock.systemUTC(), DEFAULT_IN_FLIGHT_TIMEOUT);
    }

    public LedgerService(LedgerStore store, IdempotencyStore idempotencyStore, Clock clock, Duration inFlightTimeout) {
        this.store = store;
        this.idempotency = new IdempotencyRegistry(idempotencyStore, inFlightTimeout);
        this.clock = clock;
    }

    public Account openAccount(AccountId id, Money initialBalance) {
        Account account = new Account(id, initialBalance);
        store.createAccount(account);
        return account;
    }

    public Optional<Account> account(AccountId id) {
        return store.find(id);
    }

    public List<LedgerEntry> entries(AccountId id) {
        return store.entries(id);
    }

    public TransferOutcome transfer(TransferCommand command) {
        return idempotency.execute(command.idempotencyKey(), command.fingerprint(), () -> apply(command));
    }

    private TransferResult apply(TransferCommand command) {
        if (command.from().equals(command.to())) {
            return reject(RejectionReason.SELF_TRANSFER, "can not transfer to the same account");
        }
        if (command.amount() <= 0) {
            return reject(RejectionReason.NON_POSITIVE_AMOUNT, "amount must be positive");
        }
        return store.inTransaction(Set.of(command.from(), command.to()), tx -> move(tx, command));
    }

    private TransferResult move(LedgerTransaction tx, TransferCommand command) {
        Optional<Account> from = tx.load(command.from());
        if (from.isEmpty()) {
            return reject(RejectionReason.UNKNOWN_ACCOUNT, "unknown account " + command.from());
        }
        Optional<Account> to = tx.load(command.to());
        if (to.isEmpty()) {
            return reject(RejectionReason.UNKNOWN_ACCOUNT, "unknown account " + command.to());
        }

        Money amount = new Money(command.amount(), from.get().balance().currency());
        if (!to.get().balance().currency().equals(amount.currency())) {
            return reject(RejectionReason.CURRENCY_MISMATCH, "accounts have different currencies");
        }
        if (from.get().balance().isLessThan(amount)) {
            return reject(RejectionReason.INSUFFICIENT_FUNDS, "insufficient funds on " + command.from());
        }

        Account credited;
        try {
            credited = to.get().credit(amount);
        } catch (ArithmeticException e) {
            return reject(RejectionReason.BALANCE_OVERFLOW, "balance of " + command.to() + " would overflow");
        }

        tx.save(from.get().debit(amount));
        tx.save(credited);
        Transfer transfer = new Transfer(TransferId.random(), command.from(), command.to(), amount, clock.instant());
        transfer.entries().forEach(tx::append);
        return new Applied(transfer);
    }

    private static Rejected reject(RejectionReason reason, String message) {
        return new Rejected(reason, message);
    }
}
