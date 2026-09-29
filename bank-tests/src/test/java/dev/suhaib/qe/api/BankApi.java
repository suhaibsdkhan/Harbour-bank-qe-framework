package dev.suhaib.qe.api;

import static io.restassured.RestAssured.given;

import dev.suhaib.qe.config.TestConfig;
import dev.suhaib.qe.data.TestData;
import io.qameta.allure.Step;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.JsonConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin, typed client over the bank's REST API. Every call is logged to Allure with its full
 * request and response, and returns the raw {@link Response} so tests assert status codes themselves.
 */
public class BankApi {

    private final RequestSpecification spec;

    public BankApi() {
        this.spec = new RequestSpecBuilder()
                .setBaseUri(TestConfig.requireBaseUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(RestAssuredConfig.config()
                        .jsonConfig(JsonConfig.jsonConfig().numberReturnType(JsonPathConfig.NumberReturnType.BIG_DECIMAL)))
                .addFilter(new AllureRestAssured())
                .log(LogDetail.URI)
                .build();
    }

    private RequestSpecification request() {
        return given().spec(spec);
    }

    @Step("Open {type} account for {ownerName} with initial deposit {initialDeposit}")
    public Response openAccount(String ownerName, String type, BigDecimal initialDeposit) {
        Map<String, Object> body = new HashMap<>();
        body.put("ownerName", ownerName);
        body.put("type", type);
        body.put("initialDeposit", initialDeposit);
        return request().body(body).post("/api/accounts");
    }

    /** Opens a chequing account with the given balance and returns its account number. */
    public String openFundedAccount(String balance) {
        return openAccount(TestData.ownerName(), "CHEQUING", TestData.cad(balance))
                .then().statusCode(201)
                .extract().path("accountNumber");
    }

    @Step("Get account {accountNumber}")
    public Response getAccount(String accountNumber) {
        return request().get("/api/accounts/{n}", accountNumber);
    }

    public BigDecimal balanceOf(String accountNumber) {
        return getAccount(accountNumber).then().statusCode(200).extract().path("balance");
    }

    @Step("Deposit {amount} into {accountNumber}")
    public Response deposit(String accountNumber, Object amount) {
        return request().body(Map.of("amount", amount)).post("/api/accounts/{n}/deposits", accountNumber);
    }

    @Step("Freeze account {accountNumber}")
    public Response freeze(String accountNumber) {
        return request().post("/api/accounts/{n}/freeze", accountNumber);
    }

    @Step("Get transaction history of {accountNumber}")
    public Response transactions(String accountNumber) {
        return request().get("/api/accounts/{n}/transactions", accountNumber);
    }

    @Step("Transfer {amount} from {from} to {to}")
    public Response transfer(String from, String to, Object amount) {
        return transfer(from, to, amount, null, null);
    }

    @Step("Transfer {amount} from {from} to {to} (Idempotency-Key {idempotencyKey})")
    public Response transfer(String from, String to, Object amount, String description, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("fromAccount", from);
        body.put("toAccount", to);
        body.put("amount", amount);
        body.put("description", description);
        RequestSpecification r = request().body(body);
        if (idempotencyKey != null) {
            r.header("Idempotency-Key", idempotencyKey);
        }
        return r.post("/api/transfers");
    }

    @Step("Get transfer {reference}")
    public Response getTransfer(String reference) {
        return request().get("/api/transfers/{ref}", reference);
    }

    /** Raw POST for malformed-payload tests. */
    @Step("POST raw body to {path}")
    public Response postRaw(String path, String body) {
        return request().body(body).post(path);
    }
}
