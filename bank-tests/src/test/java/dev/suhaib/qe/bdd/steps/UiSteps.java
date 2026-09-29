package dev.suhaib.qe.bdd.steps;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import dev.suhaib.qe.bdd.ScenarioContext;
import dev.suhaib.qe.ui.playwright.BankHomePage;
import dev.suhaib.qe.ui.playwright.PlaywrightExtension;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/** UI steps for scenarios tagged @ui, driven by Playwright. */
public class UiSteps {

    private final ScenarioContext ctx;
    private Page page;
    private BankHomePage home;

    public UiSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Before("@ui")
    public void openBrowser() {
        page = PlaywrightExtension.newContext().newPage();
        home = new BankHomePage(page);
    }

    @After("@ui")
    public void closeBrowser(Scenario scenario) {
        if (page != null) {
            if (scenario.isFailed()) {
                scenario.attach(page.screenshot(), "image/png", "Screenshot");
            }
            PlaywrightExtension.finish(page, false);
        }
    }

    @Given("{word} is on the online banking page")
    public void onHomePage(String alias) {
        home.open();
    }

    @When("{word} opens a {word} account online with ${word}")
    public void opensOnline(String alias, String type, String deposit) {
        ctx.rememberAccount(alias, home.openAccount(alias + " Online", type.toUpperCase(), deposit));
    }

    @When("{word} sends ${word} to {word} online with memo {string}")
    public void transfersOnline(String from, String amount, String to, String memo) {
        home.transfer(ctx.account(from), ctx.account(to), amount, memo);
    }

    @Then("the page should confirm {string}")
    public void confirms(String text) {
        assertThat(home.alert()).hasAttribute("data-kind", "success");
        assertThat(home.alert()).containsText(text);
    }

    @Then("the page should show the error {string}")
    public void showsError(String text) {
        assertThat(home.alert()).hasAttribute("data-kind", "error");
        assertThat(home.alert()).containsText(text);
    }

    @Then("the page should list {word}'s balance as {string}")
    public void listedBalance(String alias, String balance) {
        assertThat(home.balanceOf(ctx.account(alias))).hasText(balance);
    }
}
