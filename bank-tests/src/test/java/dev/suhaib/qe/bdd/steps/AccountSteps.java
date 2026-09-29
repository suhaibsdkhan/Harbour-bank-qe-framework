package dev.suhaib.qe.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import dev.suhaib.qe.bdd.ScenarioContext;
import dev.suhaib.qe.data.TestData;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AccountSteps {

    private final ScenarioContext ctx;

    public AccountSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("{word} has a {word} account with a balance of ${bigdecimal}")
    public void hasAccount(String alias, String type, BigDecimal balance) {
        String number = ctx.api.openAccount(TestData.ownerName(), type.toUpperCase(), balance)
                .then().statusCode(201).extract().path("accountNumber");
        ctx.rememberAccount(alias, number);
    }

    @Given("the following customers:")
    public void customers(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            hasAccount(row.get("customer"), row.get("type"), new BigDecimal(row.get("balance")));
        }
    }

    @Given("{word}'s account is frozen")
    public void frozen(String alias) {
        ctx.api.freeze(ctx.account(alias)).then().statusCode(200);
    }

    @When("{word} opens a {word} account with an initial deposit of ${bigdecimal}")
    public void opensAccount(String alias, String type, BigDecimal deposit) {
        ctx.lastResponse = ctx.api.openAccount(alias + " " + TestData.ownerName(), type.toUpperCase(), deposit);
        if (ctx.lastResponse.statusCode() == 201) {
            ctx.rememberAccount(alias, ctx.lastResponse.path("accountNumber"));
        }
    }

    @When("{word} deposits ${bigdecimal}")
    public void deposits(String alias, BigDecimal amount) {
        ctx.lastResponse = ctx.api.deposit(ctx.account(alias), amount);
    }

    @Then("{word}'s balance should be ${bigdecimal}")
    public void balanceShouldBe(String alias, BigDecimal expected) {
        assertThat(ctx.api.balanceOf(ctx.account(alias))).isEqualByComparingTo(expected);
    }

    @Then("{word}'s account should be {word}")
    public void accountStatus(String alias, String status) {
        ctx.api.getAccount(ctx.account(alias)).then().statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo(status));
    }

    @Then("{word}'s latest transaction should be a {word} of ${bigdecimal}")
    public void latestTransaction(String alias, String type, BigDecimal amount) {
        var history = ctx.api.transactions(ctx.account(alias)).then().statusCode(200).extract();
        assertThat((String) history.path("[0].type")).isEqualTo(type);
        assertThat((BigDecimal) history.path("[0].amount")).isEqualByComparingTo(amount);
    }
}
