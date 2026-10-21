package ledger.domain;

public sealed interface TransferResult {

    record Applied(Transfer transfer) implements TransferResult {
    }

    record Rejected(RejectionReason reason, String message) implements TransferResult {
    }
}
