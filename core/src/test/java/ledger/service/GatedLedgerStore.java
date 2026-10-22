package ledger.service;

import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.LedgerEntry;
import ledger.port.LedgerStore;
import ledger.port.TransactionalWork;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Stops a transaction on the gated account inside the lock, until release() is called.
final class GatedLedgerStore implements LedgerStore {

    private final LedgerStore delegate;
    private final AccountId gatedAccount;
    private final CyclicBarrier barrier = new CyclicBarrier(2);
    private final AtomicInteger transactions = new AtomicInteger();

    GatedLedgerStore(LedgerStore delegate, AccountId gatedAccount) {
        this.delegate = delegate;
        this.gatedAccount = gatedAccount;
    }

    @Override
    public <T> T inTransaction(Set<AccountId> accounts, TransactionalWork<T> work) {
        return delegate.inTransaction(accounts, tx -> {
            transactions.incrementAndGet();
            if (accounts.contains(gatedAccount)) {
                meet();
                meet();
            }
            return work.apply(tx);
        });
    }

    void awaitWorkerInside() {
        meet();
    }

    void release() {
        meet();
    }

    int transactions() {
        return transactions.get();
    }

    private void meet() {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("gate did not open", e);
        }
    }

    @Override
    public Optional<Account> find(AccountId id) {
        return delegate.find(id);
    }

    @Override
    public void createAccount(Account account) {
        delegate.createAccount(account);
    }

    @Override
    public List<LedgerEntry> entries(AccountId id) {
        return delegate.entries(id);
    }
}
