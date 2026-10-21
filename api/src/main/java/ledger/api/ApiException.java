package ledger.api;

class ApiException extends RuntimeException {

    private final int status;
    private final String error;

    ApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }

    static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    int status() {
        return status;
    }

    String error() {
        return error;
    }
}
