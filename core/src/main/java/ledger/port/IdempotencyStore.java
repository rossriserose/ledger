package ledger.port;

import java.util.Optional;

public interface IdempotencyStore {

    /**
     * Atomic claim of the key. Returns empty when the caller now owns the key,
     * otherwise the record that was already there.
     */
    Optional<IdempotencyRecord> putIfAbsent(String key, IdempotencyRecord record);

    void put(String key, IdempotencyRecord record);

    void remove(String key);
}
