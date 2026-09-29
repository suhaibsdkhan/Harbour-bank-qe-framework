package dev.suhaib.qe.api;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;

import dev.suhaib.qe.data.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("api")
@Epic("Banking API")
@Feature("Accounts")
class AccountApiTest {

    private final BankApi api = new BankApi();

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Opening an account returns 201, a Location header and a valid account document")
    void open_account() {
        String owner = TestData.ownerName();

        Response response = api.openAccount(owner, "SAVINGS", TestData.cad("250.00"));

        response.then()
                .statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/account.json"))
                .body("ownerName", equalTo(owner))
                .body("type", equalTo("SAVINGS"))
                .body("status", equalTo("ACTIVE"))
                .body("accountNumber", matchesPattern("\\d{10}"));
        String accountNumber = response.path("accountNumber");
        assertThat(response.header("Location")).isEqualTo("/api/accounts/" + accountNumber);
        assertThat(api.balanceOf(accountNumber)).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("Initial deposit is optional and defaults the balance to zero")
    void open_account_without_initial_deposit() {
        String accountNumber = api.openAccount(TestData.ownerName(), "CHEQUING", null)
                .then().statusCode(201).extract().path("accountNumber");

        assertThat(api.balanceOf(accountNumber)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
            blank owner name          | ' '        | CHEQUING | 10.00   | ownerName
            missing account type      | Jane Doe   |          | 10.00   | type
            negative initial deposit  | Jane Doe   | SAVINGS  | -0.01   | initialDeposit
            three decimal places      | Jane Doe   | SAVINGS  | 10.005  | initialDeposit
            """)
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Invalid account requests are rejected with a field-level 400")
    void invalid_open_account_requests(String scenario, String owner, String type, String deposit, String field) {
        api.openAccount(owner, type, new BigDecimal(deposit))
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/problem.json"))
                .body("code", equalTo("VALIDATION_FAILED"))
                .body("errors.field", hasItem(field));
    }

    @Test
    @DisplayName("Owner name longer than 100 characters is rejected")
    void owner_name_too_long() {
        api.openAccount("x".repeat(101), "CHEQUING", BigDecimal.ONE)
                .then().statusCode(400).body("errors.field", hasItem("ownerName"));
    }

    @Test
    @DisplayName("Unknown account type is a malformed request")
    void unknown_account_type() {
        api.postRaw("/api/accounts", "{\"ownerName\":\"Jane\",\"type\":\"PLATINUM\"}")
                .then().statusCode(400).body("code", equalTo("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Looking up an account that does not exist returns 404")
    void unknown_account() {
        api.getAccount("0000000000")
                .then()
                .statusCode(404)
                .body(matchesJsonSchemaInClasspath("schemas/problem.json"))
                .body("code", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("A deposit increases the balance and appears in the transaction history")
    void deposit() {
        String account = api.openFundedAccount("100.00");

        api.deposit(account, TestData.cad("49.99"))
                .then().statusCode(200).body("balance", equalTo(TestData.cad("149.99")));

        api.transactions(account).then()
                .statusCode(200)
                .body("[0].type", equalTo("CREDIT"))
                .body("[0].amount", equalTo(TestData.cad("49.99")))
                .body("[0].balanceAfter", equalTo(TestData.cad("149.99")));
    }

    @ParameterizedTest(name = "deposit of {0} is rejected")
    @CsvSource({"0.00", "-5.00", "0.001"})
    @DisplayName("Deposits must be positive with at most two decimals")
    void invalid_deposits(String amount) {
        String account = api.openFundedAccount("10.00");

        api.deposit(account, new BigDecimal(amount))
                .then().statusCode(400).body("errors.field", hasItem("amount"));
        assertThat(api.balanceOf(account)).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("A frozen account refuses deposits")
    void frozen_account_refuses_deposit() {
        String account = api.openFundedAccount("10.00");
        api.freeze(account).then().statusCode(200).body("status", equalTo("FROZEN"));

        api.deposit(account, TestData.cad("5.00"))
                .then().statusCode(422).body("code", equalTo("ACCOUNT_FROZEN"));
    }
}
