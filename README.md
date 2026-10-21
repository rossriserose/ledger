# Ledger

Small in-memory ledger service. It moves money between accounts with double entry, is safe under
concurrent requests and supports idempotency keys. Java 17, Gradle, Javalin.

## Run it

You only need a JDK 17 or newer.

```
./gradlew run     # server on http://localhost:8080 (set PORT to change it)
./gradlew test    # all tests
```

## API

Amounts are integers in minor units (cents): `10000` with `EUR` is 100.00 EUR. Floats like `10.5` are rejected.

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/accounts` | 201 | 400 bad input, 409 duplicate id |
| POST | `/transfers` (needs `Idempotency-Key` header) | 201, 200 on retry | 400, 404 unknown account, 409 key reused with other body, 422 rejected |
| GET | `/accounts/{id}` | 200 | 404 |
| GET | `/accounts/{id}/entries` | 200 | 404 |

Errors look like `{"error":"INSUFFICIENT_FUNDS","message":"insufficient funds on alice"}`.

```
curl -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
  -d '{"id":"alice","initialBalance":10000,"currency":"EUR"}'
curl -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
  -d '{"id":"bob","initialBalance":0,"currency":"EUR"}'

curl -i -X POST localhost:8080/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pay-1' -d '{"from":"alice","to":"bob","amount":2500}'
# 201 Created

# same request again: 200 with exactly the same body, money moves only once
curl -i -X POST localhost:8080/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pay-1' -d '{"from":"alice","to":"bob","amount":2500}'
# 200 OK

curl localhost:8080/accounts/alice/entries   # one entry of -2500
```

## Guarantees

- A transfer is atomic: both balances and both entries change, or nothing changes.
- A balance never goes below zero.
- One idempotency key is applied at most once, also when retries come while the first request is running.
- Transfers on different accounts run in parallel. There is no global lock.
- Deadlock is not possible, because locks are always taken in the same order.
- **No durability.** State is in memory and is lost on restart, on purpose.

## Design

`core` is plain Java with no HTTP. `api` only turns JSON into commands and results into status codes.

The storage seam is one atomic operation, not `get` / `put`:

```java
<T> T inTransaction(Set<AccountId> accounts, TransactionalWork<T> work);
```

The in-memory store locks one `ReentrantLock` per account, sorted by id, and applies the writes only if the
work succeeds. A PostgreSQL store would do `SELECT ... ORDER BY id FOR UPDATE`, the work, then `COMMIT`.
The service does not change.

Idempotency is claim before execute: the first request puts an in-flight marker for the key, and only that
request runs the transfer. Retries wait for its result. The key is claimed before any account lock.

## Trade-offs

- **Locks, not CAS.** A transfer changes two accounts, and two CAS operations are not atomic together.
- **Failures.** A business rejection is the stored result for the key. An unexpected error frees the key, so
  the client can retry.
- **Waiting retries time out** after 10 seconds with 503 and `Retry-After`.
- **Idempotency records are never removed.** A real system would delete them after about 24 hours.
- **`long` cents, not `BigDecimal`.** Exact and simple, and overflow is checked.
- **Single node only.** Several instances would need a shared database, not more locks.

The concurrency tests (contention, double spend, in-flight retries, deadlock, parallelism) run 20 times each.
