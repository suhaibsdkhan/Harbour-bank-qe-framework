package dev.suhaib.qe.ui.playwright;

import static org.assertj.core.api.Assertions.assertThat;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Page;
import dev.suhaib.qe.api.BankApi;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("ui")
@Tag("a11y")
@Epic("Online banking UI")
@Feature("Accessibility (WCAG 2.1 AA)")
@ExtendWith(PlaywrightExtension.class)
class AccessibilityTest {

    private static final List<String> WCAG_AA = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");

    private final BankApi api = new BankApi();

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Online banking page has no WCAG 2.1 AA violations")
    @Description("Runs axe-core against the page with accounts listed, an alert shown and the transaction history open.")
    void page_meets_wcag_aa(Page page) {
        String from = api.openFundedAccount("100.00");
        String to = api.openFundedAccount("0.00");
        BankHomePage home = new BankHomePage(page).open();
        home.transfer(from, to, "10.00", "Accessibility check");
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(home.alert()).containsText("completed");

        assertNoViolations(page);
    }

    @Test
    @DisplayName("Error messages are accessible too")
    void error_state_meets_wcag_aa(Page page) {
        BankHomePage home = new BankHomePage(page).open();
        page.getByTestId("open-account-submit").click();
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(home.alert()).hasAttribute("data-kind", "error");

        assertNoViolations(page);
    }

    private static void assertNoViolations(Page page) {
        AxeResults results = new AxeBuilder(page).withTags(WCAG_AA).analyze();
        List<Rule> violations = results.getViolations();
        String summary = violations.stream()
                .map(v -> "[" + v.getImpact() + "] " + v.getId() + ": " + v.getHelp() + " (" + v.getNodes().size() + " nodes)")
                .collect(Collectors.joining("\n"));
        Allure.addAttachment("axe-core violations", violations.isEmpty() ? "none" : summary);
        assertThat(violations).as(summary).isEmpty();
    }
}
