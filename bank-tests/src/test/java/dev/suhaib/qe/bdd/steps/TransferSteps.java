package dev.suhaib.qe.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import dev.suhaib.qe.bdd.ScenarioContext;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.UUID;

public class TransferSteps {

    private final ScenarioContext ctx;
    private String idempotencyKey;

    public TransferSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @When("{word} transfers ${bigdecimal} to {word}")
    public void transfers(String from, BigDecimal amount, String to) {
        ctx.lastResponse = ctx.api.transfer(ctx.account(from), ctx.account(to), amount);
        rememberReference();
    }

    @When("{word} transfers ${bigdecimal} to {word} with memo {string}")
    public void transfersWithMemo(String from, BigDecimal amount, String to, String memo) {
        ctx.lastResponse = ctx.api.transfer(ctx.account(from), ctx.account(to), amount, memo, null);
        rememberReference();
    }

    @When("{word} sends ${bigdecimal} to {word} with a new idempotency key")
    public void transfersIdempotent(String from, BigDecimal amount, String to) {
        idempotencyKey = UUID.randomUUID().toString();
        ctx.lastResponse = ctx.api.transfer(ctx.account(from), ctx.account(to), amount, null, idempotencyKey);
        rememberReference();
    }

    @When("the same request is retried {int} times")
    public void retried(int times) {
        String firstReference = ctx.lastTransferReference;
        for (int i = 0; i < times; i++) {
            ctx.lastResponse = ctx.api.transfer(
                    ctx.lastResponse.path("fromAccount"), ctx.lastResponse.path("toAccount"),
                    ctx.lastResponse.path("amount"), null, idempotencyKey);
            assertThat(ctx.lastResponse.statusCode()).isEqualTo(200);
            assertThat((String) ctx.lastResponse.path("reference")).isEqualTo(firstReference);
        }
    }

    @Then("the transfer should be completed")
    public void completed() {
        assertThat(ctx.lastResponse.statusCode()).isEqualTo(201);
        assertThat((String) ctx.lastResponse.path("status")).isEqualTo("COMPLETED");
    }

    @Then("the request should fail with status {int} and code {word}")
    public void failsWith(int status, String code) {
        assertThat(ctx.lastResponse.statusCode()).isEqualTo(status);
        assertThat((String) ctx.lastResponse.path("code")).isEqualTo(code);
    }

    private void rememberReference() {
        String ref = ctx.lastResponse.statusCode() < 300
                ? ctx.lastResponse.path("reference")
                : ctx.lastResponse.path("transferReference");
        if (ref != null) {
            ctx.lastTransferReference = ref;
        }
    }
}
