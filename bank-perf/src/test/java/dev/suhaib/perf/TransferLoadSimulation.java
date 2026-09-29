package dev.suhaib.perf;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.UUID;

/**
 * Load test for the money-movement path with pass/fail SLAs.
 *
 * <p>Each virtual customer opens a funded account and a payee, sends three $25.00 transfers (each with a
 * fresh Idempotency-Key), retries one of them, attempts an overdraft that must be refused with 422, then
 * checks that the balance is exactly $925.00. So the run checks correctness under load, not just speed.
 */
public class TransferLoadSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final int USERS_PER_SEC = Integer.getInteger("users", 20);
    private static final int DURATION = Integer.getInteger("durationSeconds", 60);

    private final HttpProtocolBuilder protocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .userAgentHeader("gatling/harbour-bank-perf");

    private static ChainBuilder openAccount(String name, String type, String deposit, String saveAs) {
        return exec(http(name)
                .post("/api/accounts")
                .body(StringBody("{\"ownerName\":\"Load Test\",\"type\":\"" + type + "\",\"initialDeposit\":" + deposit + "}"))
                .check(status().is(201))
                .check(jsonPath("$.accountNumber").saveAs(saveAs)));
    }

    private static ChainBuilder exec(io.gatling.javaapi.core.ActionBuilder action) {
        return io.gatling.javaapi.core.CoreDsl.exec(action);
    }

    private final ScenarioBuilder customer = scenario("Customer moves money")
            .exec(openAccount("Open payer account", "CHEQUING", "1000.00", "payer"))
            .exec(openAccount("Open payee account", "SAVINGS", "0.00", "payee"))
            .exec(repeat(3).on(
                    io.gatling.javaapi.core.CoreDsl.exec(session -> session.set("key", UUID.randomUUID().toString()))
                            .exec(http("Transfer")
                                    .post("/api/transfers")
                                    .header("Idempotency-Key", "#{key}")
                                    .body(StringBody("{\"fromAccount\":\"#{payer}\",\"toAccount\":\"#{payee}\",\"amount\":25.00}"))
                                    .check(status().is(201)))))
            .exec(http("Retry last transfer (idempotent)")
                    .post("/api/transfers")
                    .header("Idempotency-Key", "#{key}")
                    .body(StringBody("{\"fromAccount\":\"#{payer}\",\"toAccount\":\"#{payee}\",\"amount\":25.00}"))
                    .check(status().is(200)))
            .exec(http("Overdraft attempt (must be refused)")
                    .post("/api/transfers")
                    .body(StringBody("{\"fromAccount\":\"#{payee}\",\"toAccount\":\"#{payer}\",\"amount\":5000.00}"))
                    .check(status().is(422))
                    .check(jsonPath("$.code").is("INSUFFICIENT_FUNDS")))
            .exec(http("Check payer balance")
                    .get("/api/accounts/#{payer}")
                    .check(status().is(200))
                    .check(jsonPath("$.balance").ofDouble().is(925.0)));

    {
        setUp(customer.injectOpen(
                        rampUsersPerSec(1).to(USERS_PER_SEC).during(15),
                        constantUsersPerSec(USERS_PER_SEC).during(DURATION)))
                .protocols(protocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile(95.0).lt(500),
                        details("Transfer").responseTime().percentile(99.0).lt(800),
                        global().requestsPerSec().gt(50.0));
    }
}
