package ledger.port;

@FunctionalInterface
public interface TransactionalWork<T> {

    T apply(LedgerTransaction tx);
}
