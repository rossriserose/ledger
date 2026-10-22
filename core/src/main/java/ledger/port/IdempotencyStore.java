package ledger.port;

import java.util.Optional;

public interface IdempotencyStore {

    // Empty means the caller now owns the key. Otherwise it gets the record that was there.
    Optional<IdempotencyRecord> putIfAbsent(String key, IdempotencyRecord record);

    void put(String key, IdempotencyRecord record);

    void remove(String key);
}
