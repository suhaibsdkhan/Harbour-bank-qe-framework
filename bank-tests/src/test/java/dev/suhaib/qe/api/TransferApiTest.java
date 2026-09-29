package dev.suhaib.qe.api;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import dev.suhaib.qe.data.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag("api")
@Epic("Banking API")
@Feature("Transfers")
class TransferApiTest {

    private final BankApi api = new BankApi();

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @Story("Happy path")
    @DisplayName("A transfer moves money between two accounts and records both sides")
    void successful_transfer() {
        String from = api.openFundedAccount("500.00");
        String to = api.openFundedAccount("20.00");

        Response response = api.transfer(from, to, TestData.cad("120.50"), "Dinner", null);

        response.then()
                .statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/transfer.json"))
                .body("status", equalTo("COMPLETED"))
                .body("amount", equalTo(TestData.cad("120.50")))
                .body("description", equalTo("Dinner"));
        assertThat(api.balanceOf(from)).isEqualByComparingTo("379.50");
        assertThat(api.balanceOf(to)).isEqualByComparingTo("140.50");

        String reference = response.path("reference");
        api.getTransfer(reference).then().statusCode(200).body("reference", equalTo(reference));
        api.transactions(to).then().body("[0].transferReference", equalTo(reference)).body("[0].type", equalTo("CREDIT"));
    }

    @Test
    @Story("Boundary values")
    @DisplayName("Transferring the full balance leaves exactly zero")
    void transfer_entire_balance() {
        String from = api.openFundedAccount("75.25");
        String to = api.openFundedAccount("0.00");

        api.transfer(from, to, TestData.cad("75.25")).then().statusCode(201);

        assertThat(api.balanceOf(from)).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest(name = "{0} -> HTTP {1} {2}")
    @CsvSource({
            "9999.99,  201, COMPLETED",
            "10000.00, 201, COMPLETED",
            "10000.01, 422, LIMIT_EXCEEDED",
    })
    @Severity(SeverityLevel.CRITICAL)
    @Story("Boundary values")
    @DisplayName("Single transfer limit is inclusive at 10,000.00")
    void transfer_limit_boundaries(String amount, int status, String outcome) {
        String from = api.openFundedAccount("20000.00");
        String to = api.openFundedAccount("0.00");

        Response response = api.transfer(from, to, new BigDecimal(amount));

        response.then().statusCode(status);
        if (status == 201) {
            response.then().body("status", equalTo(outcome));
        } else {
            response.then().body("code", equalTo(outcome));
            assertThat(api.balanceOf(from)).isEqualByComparingTo("20000.00");
        }
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @Story("Business rules")
    @DisplayName("Overdrawing by one cent is rejected and no money moves")
    void insufficient_funds() {
        String from = api.openFundedAccount("100.00");
        String to = api.openFundedAccount("0.00");

        api.transfer(from, to, TestData.cad("100.01"))
                .then()
                .statusCode(422)
                .body(matchesJsonSchemaInClasspath("schemas/problem.json"))
                .body("code", equalTo("INSUFFICIENT_FUNDS"))
                .body("transferReference", notNullValue());

        assertThat(api.balanceOf(from)).isEqualByComparingTo("100.00");
        assertThat(api.balanceOf(to)).isEqualByComparingTo("0.00");
    }

    @Test
    @Story("Business rules")
    @DisplayName("A rejected transfer is still retrievable for audit with its failure reason")
    void rejected_transfer_is_audited() {
        String from = api.openFundedAccount("1.00");
        String to = api.openFundedAccount("0.00");

        String reference = api.transfer(from, to, TestData.cad("2.00")).then().statusCode(422)
                .extract().path("transferReference");

        api.getTransfer(reference).then()
                .statusCode(200)
                .body("status", equalTo("REJECTED"))
                .body("failureReason", equalTo("INSUFFICIENT_FUNDS"));
    }

    @Test
    @Story("Business rules")
    @DisplayName("Transferring to the same account is rejected")
    void same_account() {
        String account = api.openFundedAccount("50.00");

        api.transfer(account, account, TestData.cad("5.00"))
                .then().statusCode(422).body("code", equalTo("SAME_ACCOUNT"));
    }

    @Test
    @Story("Business rules")
    @DisplayName("Transfers out of or into a frozen account are rejected")
    void frozen_accounts() {
        String active = api.openFundedAccount("50.00");
        String frozen = api.openFundedAccount("50.00");
        api.freeze(frozen).then().statusCode(200);

        api.transfer(frozen, active, TestData.cad("5.00")).then().statusCode(422).body("code", equalTo("ACCOUNT_FROZEN"));
        api.transfer(active, frozen, TestData.cad("5.00")).then().statusCode(422).body("code", equalTo("ACCOUNT_FROZEN"));
        assertThat(api.balanceOf(active)).isEqualByComparingTo("50.00");
    }

    @Test
    @Story("Validation")
    @DisplayName("Transfers from or to unknown accounts return 404")
    void unknown_accounts() {
        String account = api.openFundedAccount("50.00");

        api.transfer("0000000000", account, TestData.cad("1.00")).then().statusCode(404).body("code", equalTo("ACCOUNT_NOT_FOUND"));
        api.transfer(account, "0000000000", TestData.cad("1.00")).then().statusCode(404).body("code", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @ParameterizedTest(name = "amount {0} is invalid")
    @CsvSource({"0", "-10.00", "0.001", "12.345"})
    @Story("Validation")
    @DisplayName("Amounts must be positive with at most two decimals")
    void invalid_amounts(String amount) {
        String from = api.openFundedAccount("50.00");
        String to = api.openFundedAccount("0.00");

        api.transfer(from, to, new BigDecimal(amount))
                .then().statusCode(400).body("code", equalTo("VALIDATION_FAILED")).body("errors[0].field", equalTo("amount"));
    }

    @Test
    @Story("Validation")
    @DisplayName("A body that is not JSON is a malformed request")
    void malformed_body() {
        api.postRaw("/api/transfers", "{not json").then().statusCode(400).body("code", equalTo("MALFORMED_REQUEST"));
    }

    @Test
    @Story("Validation")
    @DisplayName("Unknown transfer reference returns 404")
    void unknown_transfer() {
        api.getTransfer(UUID.randomUUID().toString()).then().statusCode(404).body("code", equalTo("TRANSFER_NOT_FOUND"));
    }

    @Nested
    @Story("Idempotency")
    @DisplayName("Idempotency-Key")
    class Idempotency {

        @Test
        @Severity(SeverityLevel.BLOCKER)
        @DisplayName("Replaying a request with the same key returns the original transfer and debits once")
        void replay_is_safe() {
            String from = api.openFundedAccount("300.00");
            String to = api.openFundedAccount("0.00");
            String key = UUID.randomUUID().toString();

            String first = api.transfer(from, to, TestData.cad("100.00"), null, key)
                    .then().statusCode(201).extract().path("reference");
            String second = api.transfer(from, to, TestData.cad("100.00"), null, key)
                    .then().statusCode(200).extract().path("reference");

            assertThat(second).isEqualTo(first);
            assertThat(api.balanceOf(from)).isEqualByComparingTo("200.00");
            assertThat(api.balanceOf(to)).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Reusing a key with a different amount is a 409 conflict")
        void key_reuse_with_different_payload() {
            String from = api.openFundedAccount("300.00");
            String to = api.openFundedAccount("0.00");
            String key = UUID.randomUUID().toString();

            api.transfer(from, to, TestData.cad("100.00"), null, key).then().statusCode(201);

            api.transfer(from, to, TestData.cad("150.00"), null, key)
                    .then().statusCode(409).body("code", equalTo("IDEMPOTENCY_CONFLICT"));
            assertThat(api.balanceOf(from)).isEqualByComparingTo("200.00");
        }

        @Test
        @DisplayName("Replaying a rejected transfer returns the same rejection")
        void replay_of_rejection() {
            String from = api.openFundedAccount("10.00");
            String to = api.openFundedAccount("0.00");
            String key = UUID.randomUUID().toString();

            String ref1 = api.transfer(from, to, TestData.cad("50.00"), null, key)
                    .then().statusCode(422).extract().path("transferReference");
            String ref2 = api.transfer(from, to, TestData.cad("50.00"), null, key)
                    .then().statusCode(422).extract().path("transferReference");

            assertThat(ref2).isEqualTo(ref1);
        }
    }
}
