package ledger.api;

import ledger.memory.InMemoryIdempotencyStore;
import ledger.memory.InMemoryLedgerStore;
import ledger.service.LedgerService;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        var service = new LedgerService(new InMemoryLedgerStore(), new InMemoryIdempotencyStore());
        LedgerApi.create(service).start(port);
    }
}
