package ledger.domain;

public enum RejectionReason {
    SELF_TRANSFER,
    NON_POSITIVE_AMOUNT,
    UNKNOWN_ACCOUNT,
    CURRENCY_MISMATCH,
    INSUFFICIENT_FUNDS,
    BALANCE_OVERFLOW
}
