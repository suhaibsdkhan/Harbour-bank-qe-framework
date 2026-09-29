# Test Strategy: Harbour Bank

This is the test strategy behind the framework, written the way a QE lead would write it for a real payments
feature. It explains what is tested, why at each layer, and what has to be true before a release.

## 1. Scope

In scope: the account, deposit and transfer APIs, the double-entry ledger, the online banking web page and
the behaviour of all of these under concurrent load.

Out of scope for this demo: authentication and authorization, multi-currency, interest, statements and
external payment rails. Section 7 lists how each would be added.

## 2. Risks, ranked

Tests are prioritised by what would hurt a bank most. Severity labels in the Allure report follow this table.

| # | Risk | Impact | Where it is covered |
|---|---|---|---|
| R1 | Money created or destroyed (ledger does not reconcile) | Critical: regulatory, financial | SQL reconciliation, double-entry checks, post-load reconciliation |
| R2 | Double charge on retry | Critical: customer harm | Idempotency tests (unit, API, BDD, load) |
| R3 | Overdraft under concurrent requests | Critical: credit loss | Concurrency test (40 parallel withdrawals), load test overdraft check |
| R4 | Business rules not enforced (limit, frozen account, same account) | High: fraud, compliance | Boundary-value API tests, BDD scenario outline, unit tests |
| R5 | Rejected transfers leave no audit trail | High: compliance | API audit lookup, SQL row and reason checks |
| R6 | API contract drift breaks consumers | Medium | JSON Schema validation, Postman/Newman collection |
| R7 | UI shows wrong balances or hides errors | Medium | Playwright and Selenium journeys |
| R8 | UI not usable with assistive technology | Medium: legal (AODA/ADA) | axe-core WCAG 2.1 AA scans |
| R9 | Slow responses at peak | Medium | Gatling load test with SLA assertions |

## 3. Test pyramid

| Layer | Tooling | Count | Runs | Why at this layer |
|---|---|---|---|---|
| Unit | JUnit 5, Mockito | 8 | every build, < 1 s | Transfer rules and lock ordering in isolation; fastest feedback |
| Web slice | Spring `@WebMvcTest` | 4 | every build | HTTP status and error mapping without a database |
| API | Rest Assured, JSON Schema | 35 | every build | The contract consumers depend on, with real persistence |
| Data | SQL over JDBC | 8 | every build and after load | Invariants the API cannot show (ledger rows, audit rows) |
| BDD | Cucumber | 14 | every build | Business-readable acceptance criteria; living documentation |
| UI | Playwright, Selenium (Chrome, Firefox), axe-core | 10 + 3 | every build | Customer journeys, cross-browser, accessibility |
| Contract | Postman + Newman | 18 requests | every build | Tool-independent regression pack that other teams can run |
| Performance | Gatling | 1 simulation | every build on main, nightly | Latency and throughput SLAs, correctness under load |

Most checks sit at the API and data layers. That is where the money rules live. UI tests cover only the
journeys a customer sees, and they set up their data through the API.

## 4. Test design techniques used

- **Boundary value analysis.** The $10,000 limit is tested at 9,999.99, 10,000.00 and 10,000.01. Overdraft is
  tested at one cent over the balance. The exact-balance transfer must leave 0.00.
- **Equivalence partitioning.** Invalid amounts: zero, negative, too many decimals.
- **Decision table.** Rejection reasons (insufficient funds, limit, frozen, same account) in one scenario outline.
- **State transition.** An account goes from ACTIVE to FROZEN, and frozen accounts refuse deposits and transfers.
- **Concurrency and race conditions.** 40 parallel transfers against a balance that covers 30. Opposite-direction
  transfers are checked for deadlocks.
- **Invariant (property) checks.** Balance equals the sum of the ledger for every account in the database, not
  just for the test's own data.

## 5. Traceability: requirement to tests

| Requirement | Unit | API | BDD | SQL | UI | Postman | Load |
|---|---|---|---|---|---|---|---|
| Open account with optional deposit | | `AccountApiTest` | accounts.feature | | Playwright | ✓ | ✓ |
| Deposit must be positive, 2 decimals | | `AccountApiTest` | accounts.feature | | Selenium | ✓ | |
| Transfer moves money and books both sides | `TransferServiceTest` | `TransferApiTest` | transfers.feature | `DataIntegrityTest` | both | ✓ | ✓ |
| Single transfer limit $10,000 | `TransferServiceTest` | boundary test | transfers.feature | | | ✓ | |
| No overdraft | `TransferServiceTest` | `TransferApiTest`, `ConcurrencyTest` | transfers.feature | negative-balance check | both | ✓ | ✓ |
| Frozen accounts cannot move money | `TransferServiceTest` | `TransferApiTest` | both features | | Selenium | | |
| Idempotent retries | `TransferServiceTest`, `TransferControllerTest` | `TransferApiTest` | transfers.feature | | | ✓ | ✓ |
| Rejections are audited | `TransferServiceTest` | `TransferApiTest` | transfers.feature | `DataIntegrityTest` | | ✓ | |
| Ledger always reconciles | | | transfers.feature | `DataIntegrityTest` | | | post-load |
| Accessible UI (WCAG 2.1 AA) | | | | | `AccessibilityTest` | | |
| p95 < 500 ms, < 1% errors at 20 new customers/s | | | | | | | `TransferLoadSimulation` |

## 6. Entry and exit criteria

A change can merge when:

- every CI job is green: unit, API, BDD, SQL, UI (Chrome and Firefox), accessibility, Postman and load;
- bank-app code coverage from unit plus end-to-end tests is at least 90% of lines and 85% of branches
  (enforced in CI; currently about 99% and 92%);
- no open defect is rated Critical or High;
- the Allure report shows no new failure category under "Money integrity defects".

## 7. Known gaps and next steps

- **Security.** Add OAuth2/JWT, then test authorization per account (IDOR), rate limiting and the OWASP API Top 10
  with ZAP in CI.
- **Contract testing.** Add Pact consumer-driven contracts if a second service consumes this API.
- **Test data at scale.** Seed realistic volumes and anonymised production-like data for performance runs.
- **Resilience.** Inject database latency and failures (Toxiproxy) and check that transfers stay atomic.
- **Mutation testing.** Run PIT on `TransferService` to check that the unit tests actually catch broken rules.

## 8. Defects found by this framework

| Defect | Found by | Fix |
|---|---|---|
| The web page sent transfers without `Content-Type: application/json` (HTTP 415). The fetch options object overwrote the default headers. | Selenium and Playwright transfer journeys | Merge the per-request headers into the defaults in `app.js` |
| A Cucumber runner filtered down to zero scenarios failed the whole build | Running `-Dgroups=selenium` locally | `@Suite(failIfNoTests = false)` |
| Tests ran against an empty H2 database while the app wrote to another one | SQL checks | Pass datasource settings as command-line args so they override `application.yml` |
