package ledger.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import ledger.api.Dto.AccountResponse;
import ledger.api.Dto.CreateAccountRequest;
import ledger.api.Dto.EntryResponse;
import ledger.api.Dto.ErrorResponse;
import ledger.api.Dto.TransferRequest;
import ledger.api.Dto.TransferResponse;
import ledger.domain.Account;
import ledger.domain.AccountId;
import ledger.domain.Money;
import ledger.domain.TransferResult.Applied;
import ledger.domain.TransferResult.Rejected;
import ledger.port.DuplicateAccountException;
import ledger.service.IdempotencyConflictException;
import ledger.service.LedgerService;
import ledger.service.TransferCommand;
import ledger.service.TransferInFlightException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LedgerApi {

    private static final Logger log = LoggerFactory.getLogger(LedgerApi.class);
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final LedgerService service;
    private final ObjectMapper mapper;

    private LedgerApi(LedgerService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    public static Javalin create(LedgerService service) {
        ObjectMapper mapper = strictMapper();
        var api = new LedgerApi(service, mapper);
        return Javalin.create(config -> {
                    config.showJavalinBanner = false;
                    config.jsonMapper(new JavalinJackson(mapper, false));
                })
                .post("/accounts", api::createAccount)
                .get("/accounts/{id}", api::getAccount)
                .get("/accounts/{id}/entries", api::getEntries)
                .post("/transfers", api::transfer)
                .exception(ApiException.class, (e, ctx) -> error(ctx, e.status(), e.error(), e.getMessage()))
                .exception(IllegalArgumentException.class, (e, ctx) -> error(ctx, 400, "BAD_REQUEST", e.getMessage()))
                .exception(DuplicateAccountException.class, (e, ctx) -> error(ctx, 409, "DUPLICATE_ACCOUNT", e.getMessage()))
                .exception(IdempotencyConflictException.class, (e, ctx) -> error(ctx, 409, "IDEMPOTENCY_KEY_REUSED", e.getMessage()))
                .exception(TransferInFlightException.class, (e, ctx) -> {
                    ctx.header("Retry-After", "1");
                    error(ctx, 503, "TRANSFER_IN_PROGRESS", e.getMessage());
                })
                .exception(Exception.class, (e, ctx) -> {
                    log.error("unexpected error on {} {}", ctx.method(), ctx.path(), e);
                    error(ctx, 500, "INTERNAL_ERROR", "unexpected error");
                });
    }

    private void createAccount(Context ctx) {
        CreateAccountRequest request = read(ctx, CreateAccountRequest.class);
        if (request.id() == null || request.initialBalance() == null || request.currency() == null) {
            throw ApiException.badRequest("id, initialBalance and currency are required");
        }
        Account account = service.openAccount(AccountId.of(request.id()),
                Money.of(request.initialBalance(), request.currency()));
        ctx.status(201).json(AccountResponse.of(account));
    }

    private void getAccount(Context ctx) {
        ctx.json(AccountResponse.of(existingAccount(ctx)));
    }

    private void getEntries(Context ctx) {
        Account account = existingAccount(ctx);
        ctx.json(service.entries(account.id()).stream().map(EntryResponse::of).toList());
    }

    private void transfer(Context ctx) {
        String key = ctx.header(IDEMPOTENCY_KEY);
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest(IDEMPOTENCY_KEY + " header is required");
        }
        TransferRequest request = read(ctx, TransferRequest.class);
        if (request.from() == null || request.to() == null || request.amount() == null) {
            throw ApiException.badRequest("from, to and amount are required");
        }

        var command = new TransferCommand(key, AccountId.of(request.from()), AccountId.of(request.to()), request.amount());
        var outcome = service.transfer(command);
        if (outcome.result() instanceof Applied applied) {
            ctx.status(outcome.replayed() ? 200 : 201).json(TransferResponse.of(applied.transfer()));
        } else {
            Rejected rejected = (Rejected) outcome.result();
            int status = switch (rejected.reason()) {
                case UNKNOWN_ACCOUNT -> 404;
                case SELF_TRANSFER, NON_POSITIVE_AMOUNT, CURRENCY_MISMATCH, INSUFFICIENT_FUNDS, BALANCE_OVERFLOW -> 422;
            };
            error(ctx, status, rejected.reason().name(), rejected.message());
        }
    }

    private Account existingAccount(Context ctx) {
        String id = ctx.pathParam("id");
        return service.account(AccountId.of(id))
                .orElseThrow(() -> ApiException.notFound("unknown account " + id));
    }

    private <T> T read(Context ctx, Class<T> type) {
        try {
            T value = mapper.readValue(ctx.body(), type);
            if (value == null) {
                throw ApiException.badRequest("request body is required");
            }
            return value;
        } catch (UnrecognizedPropertyException e) {
            throw ApiException.badRequest("unknown field " + e.getPropertyName());
        } catch (MismatchedInputException e) {
            throw ApiException.badRequest(wrongTypeMessage(e));
        } catch (JsonMappingException e) {
            throw ApiException.badRequest(e.getCause() instanceof InputCoercionException
                    ? outOfRangeMessage(e)
                    : "malformed json");
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("malformed json");
        }
    }

    private static String wrongTypeMessage(MismatchedInputException e) {
        if (e.getPath().isEmpty()) {
            return "malformed json";
        }
        String field = e.getPath().get(e.getPath().size() - 1).getFieldName();
        return e.getTargetType() == Long.class
                ? field + " must be an integer in minor units"
                : field + " has the wrong type";
    }

    private static String outOfRangeMessage(JsonMappingException e) {
        if (e.getPath().isEmpty()) {
            return "number is out of range";
        }
        return e.getPath().get(e.getPath().size() - 1).getFieldName() + " is out of range";
    }

    private static void error(Context ctx, int status, String error, String message) {
        ctx.status(status).json(new ErrorResponse(error, message));
    }

    private static ObjectMapper strictMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }
}
