package ledger.domain;

import java.util.Objects;
import java.util.UUID;

public record TransferId(String value) {

    public TransferId {
        Objects.requireNonNull(value, "transfer id");
    }

    public static TransferId random() {
        return new TransferId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
