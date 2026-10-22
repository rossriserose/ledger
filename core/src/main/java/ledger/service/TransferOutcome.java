package ledger.service;

import ledger.domain.TransferResult;

public record TransferOutcome(TransferResult result, boolean replayed) {
}
