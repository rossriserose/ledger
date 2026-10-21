package ledger.service;

import ledger.domain.TransferResult;

/**
 * @param replayed true when the result comes from an earlier request with the same idempotency key
 */
public record TransferOutcome(TransferResult result, boolean replayed) {
}
