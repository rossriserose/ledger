package ledger.memory;

import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.LedgerEntry;
import ledger.port.DuplicateAccountException;
import ledger.port.LedgerStore;
import ledger.port.LedgerTransaction;
import ledger.port.TransactionalWork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class InMemoryLedgerStore implements LedgerStore {

    private final ConcurrentHashMap<AccountId, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AccountId, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AccountId, Queue<LedgerEntry>> entries = new ConcurrentHashMap<>();

    @Override
    public <T> T inTransaction(Set<AccountId> ids, TransactionalWork<T> work) {
        // Accounts are never deleted, so an unknown id stays unknown and needs no lock.
        List<AccountId> ordered = ids.stream().filter(accounts::containsKey).sorted().toList();
        List<ReentrantLock> held = new ArrayList<>(ordered.size());
        try {
            for (AccountId id : ordered) {
                ReentrantLock lock = lockFor(id);
                lock.lock();
                held.add(lock);
            }
            var tx = new MemoryTransaction(Set.copyOf(ids), Set.copyOf(ordered));
            T result = work.apply(tx);
            tx.commit();
            return result;
        } finally {
            for (int i = held.size() - 1; i >= 0; i--) {
                held.get(i).unlock();
            }
        }
    }

    @Override
    public Optional<Account> find(AccountId id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public void createAccount(Account account) {
        if (accounts.putIfAbsent(account.id(), account) != null) {
            throw new DuplicateAccountException(account.id());
        }
    }

    @Override
    public List<LedgerEntry> entries(AccountId id) {
        Queue<LedgerEntry> list = entries.get(id);
        return list == null ? List.of() : List.copyOf(list);
    }

    private ReentrantLock lockFor(AccountId id) {
        return locks.computeIfAbsent(id, k -> new ReentrantLock());
    }

    private final class MemoryTransaction implements LedgerTransaction {

        private final Set<AccountId> requested;
        private final Set<AccountId> locked;
        private final Map<AccountId, Account> saved = new HashMap<>();
        private final List<LedgerEntry> appended = new ArrayList<>();

        private MemoryTransaction(Set<AccountId> requested, Set<AccountId> locked) {
            this.requested = requested;
            this.locked = locked;
        }

        @Override
        public Optional<Account> load(AccountId id) {
            if (!requested.contains(id)) {
                throw new IllegalStateException("account " + id + " is not part of this transaction");
            }
            if (!locked.contains(id)) {
                return Optional.empty();
            }
            return Optional.of(saved.getOrDefault(id, accounts.get(id)));
        }

        @Override
        public void save(Account account) {
            requireLocked(account.id());
            saved.put(account.id(), account);
        }

        @Override
        public void append(LedgerEntry entry) {
            requireLocked(entry.accountId());
            appended.add(entry);
        }

        private void requireLocked(AccountId id) {
            if (!locked.contains(id)) {
                throw new IllegalStateException("account " + id + " is not locked by this transaction");
            }
        }

        private void commit() {
            for (LedgerEntry entry : appended) {
                entries.computeIfAbsent(entry.accountId(), k -> new ConcurrentLinkedQueue<>()).add(entry);
            }
            accounts.putAll(saved);
        }
    }
}
