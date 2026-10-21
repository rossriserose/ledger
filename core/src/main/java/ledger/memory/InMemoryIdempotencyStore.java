package ledger.memory;

import ledger.port.IdempotencyRecord;
import ledger.port.IdempotencyStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> putIfAbsent(String key, IdempotencyRecord record) {
        return Optional.ofNullable(records.putIfAbsent(key, record));
    }

    @Override
    public void put(String key, IdempotencyRecord record) {
        records.put(key, record);
    }

    @Override
    public void remove(String key) {
        records.remove(key);
    }
}
