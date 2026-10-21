# Ledger

Small in-memory ledger service. It keeps accounts and moves money between them with double entry,
safe under concurrent requests and with idempotency keys. Java 17, Gradle, Javalin for HTTP.

## Run it

You need only a JDK 17 or newer. The Gradle wrapper downloads everything else.

```
./gradlew run
```

The server listens on `http://localhost:8080` (set `PORT` to change it).

Run all tests:

```
./gradlew test
```

## API

All amounts are integers in minor units (cents). `10000` with `EUR` means 100.00 EUR.
Floats like `10.5` or strings like `"100"` are rejected with 400.

| Method | Path | Body / header | Success | Errors |
|---|---|---|---|---|
| POST | `/accounts` | `{"id":"acc-1","initialBalance":10000,"currency":"EUR"}` | 201 `{id, balance, currency}` | 400 malformed, 409 duplicate id |
| POST | `/transfers` | header `Idempotency-Key`, body `{"from","to","amount"}` | 201 `{transferId, status:"APPLIED", ...}`, 200 on retry | 400 malformed or no key, 404 unknown account, 409 key reused with other body, 422 insufficient funds / self transfer / amount not positive / different currencies |
| GET | `/accounts/{id}` | | 200 `{id, balance, currency}` | 404 |
| GET | `/accounts/{id}/entries` | | 200 list of ledger entries | 404 |

Errors always look like `{"error":"INSUFFICIENT_FUNDS","message":"insufficient funds on alice"}`.

Try it:

```
curl -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
  -d '{"id":"alice","initialBalance":10000,"currency":"EUR"}'

curl -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
  -d '{"id":"bob","initialBalance":0,"currency":"EUR"}'

curl -i -X POST localhost:8080/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f1c2a' -d '{"from":"alice","to":"bob","amount":2500}'
# HTTP/1.1 201 Created
# {"transferId":"a9005174-...","status":"APPLIED","from":"alice","to":"bob","amount":2500,"currency":"EUR","occurredAt":"..."}

# same key again: 200 and exactly the same body, money is not moved again
curl -i -X POST localhost:8080/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f1c2a' -d '{"from":"alice","to":"bob","amount":2500}'
# HTTP/1.1 200 OK

curl localhost:8080/accounts/alice           # {"id":"alice","balance":7500,"currency":"EUR"}
curl localhost:8080/accounts/alice/entries   # [{"transferId":"a9005174-...","accountId":"alice","amount":-2500,...}]
```

Every account has one currency. Transfers between different currencies are rejected with 422, there is no FX.

## Guarantees

- A transfer is atomic. Both balances change and both ledger entries are written, or nothing changes.
- A balance never goes below zero. The check and the debit happen under the same lock.
- Every transfer writes exactly two entries, `-amount` and `+amount`. The sum of all entries is always zero,
  and each balance is its opening balance plus its own entries. Tests check this.
- One idempotency key is applied at most once. This holds also when the retry comes while the first request
  is still running.
- Transfers on different accounts run in parallel. There is no global lock.
- Deadlock is not possible, because locks are always taken in the same order.
- **No durability.** Everything is in memory and is lost on restart. This is on purpose, the task asked for it.

## Design

```
core/   plain Java, no HTTP and no framework
  domain/    Money, Account, LedgerEntry, Transfer, TransferResult
  port/      LedgerStore, LedgerTransaction, IdempotencyStore
  service/   LedgerService, TransferCommand, idempotency registry
  memory/    in-memory implementations of the ports
api/    Javalin adapter: JSON to commands, results and errors to status codes
```

`core` does not know about `api`, and `service` only uses the port interfaces. The in-memory stores are
given to `LedgerService` in the constructor (see `api/.../Main.java`).

**The storage seam.** A simple `get(id)` / `put(account)` interface is not enough. Then the service must do
the locking itself, and a SQL store can not use its own transactions. So the store gives atomicity as one
operation:

```java
<T> T inTransaction(Set<AccountId> accounts, TransactionalWork<T> work);
```

The service only says "do this work over these accounts, atomically". It never touches a lock.

- In memory: one `ReentrantLock` per account. Lock all requested accounts sorted by id, run the work,
  unlock in reverse order. Writes are staged and applied only if the work finishes without exception.
- PostgreSQL would be: `BEGIN; SELECT ... FROM accounts WHERE id IN (...) ORDER BY id FOR UPDATE;`
  run the work, then `UPDATE` balances, `INSERT` the entries and `COMMIT`. On error, `ROLLBACK`.
  `LedgerService` would stay the same.

Sorting is why deadlock can not happen. If thread 1 locks A then B and thread 2 locks B then A, they can
wait for each other forever. With sorting, both lock A first, so one simply waits for the other.
It works the same for three or more accounts.

**Idempotency** is claim before execute. The first request does `putIfAbsent(key, InFlight(future))` and
becomes the owner. Only the owner opens the transaction. Other requests with the same key either read the
settled result, or wait on the owner's future. The key is claimed before any account lock is taken, so a
waiting retry never holds a lock. Same key with another `from`/`to`/`amount` gives 409, like Stripe does.

## Trade-offs

- **Locks and not CAS.** An `AtomicReference` per account with a retry loop works for one account, but a
  transfer changes two accounts, and two separate CAS operations are not atomic together. You need a lock
  or some two-phase protocol anyway. Locks are simpler here.
- **Which failures are remembered.** Business rejections (insufficient funds, unknown account, and so on)
  are the result for the key. A retry gets the same rejection, even if the account has money now. An
  unexpected exception frees the key, so the client can try again. In that small window it is at least once
  and not exactly once.
- **Waiting retries have a timeout.** A retry waits up to 10 seconds for the first request. After that it
  gets 503 with `Retry-After`. If the first request fails with an unexpected error, the waiting retries get 500.
- **Idempotency records are never removed.** The map grows without limit. A real system keeps them for
  around 24 hours and then deletes them.
- **Retry status is 200, not 201.** The body is the same. 200 tells the client nothing new was created.
- **`long` cents and not `BigDecimal`.** It is exact, fast and simple. `double` is not an option, because
  0.10 can not be stored exactly. Overflow is checked with `Math.addExact`, and a credit that would overflow
  is rejected with 422 instead of wrapping to a negative number.
- **Single node only.** The locks are in one JVM. Running several instances needs a shared database
  (like above) or consensus, not more locks.
- **Reads are not a snapshot.** `GET /accounts/{id}` reads one account without a lock. It always sees a
  committed balance, but two GETs in a row can be from different moments.

## Tests

`./gradlew test` runs about 160 tests. The concurrency tests run 20 times each (`@RepeatedTest`), because a
race that fails once in 30 runs passes a single run.

| Test | What it shows |
|---|---|
| `ContentionTest` | 32 threads, random transfers over 5 accounts: no money lost, nothing negative, entries sum to zero |
| `DoubleSpendTest` | 100 on the account, 50 parallel transfers of 10: exactly 10 applied, 40 rejected |
| `IdempotencyInFlightTest` | 8 requests with the same key while the first is blocked inside the transaction: one debit, same result for all |
| `DeadlockTest` | A to B and B to A at the same time, must finish in 10 seconds |
| `ParallelismTest` | A to B is blocked inside its transaction, C to D still finishes |
| `RandomTransfersTest` | seeded random commands, also bad ones: invariants hold after each run |
| `LedgerServiceTest` | single cases: insufficient funds, self transfer, zero amount, unknown account, overflow, currency, key reuse |
| `IdempotencyRegistryTest` | replay without running again, conflict, failure frees the key, timeout of a waiting retry |
| domain and store tests | money arithmetic and overflow, account rules, two entries per transfer, store rollback |
| `AccountsApiTest`, `TransfersApiTest` | HTTP status codes, short error messages, identical retry response |

I also broke each guarantee on purpose to check the tests really catch it. Shuffled lock order: the deadlock
test times out. A global lock: the parallelism test fails. No atomic claim: the in-flight idempotency test
fails. Balance check outside the lock: the double spend test fails.
