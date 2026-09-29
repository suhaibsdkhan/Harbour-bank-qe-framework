package dev.suhaib.qe.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.suhaib.qe.data.TestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("api")
@Tag("concurrency")
@Epic("Banking API")
@Feature("Concurrency")
class ConcurrencyTest {

    private final BankApi api = new BankApi();

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Parallel withdrawals never overdraw an account")
    @Description("40 transfers of $10 race against a $300 balance. Exactly 30 may succeed and the balance must end at $0.00.")
    void no_overdraft_under_contention() throws Exception {
        String from = api.openFundedAccount("300.00");
        String to = api.openFundedAccount("0.00");

        List<Integer> statuses = runInParallel(40, i -> () -> api.transfer(from, to, TestData.cad("10.00")).statusCode());

        Map<Integer, Long> byStatus = statuses.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Allure.addAttachment("Status code counts", byStatus.toString());
        assertThat(byStatus).containsEntry(201, 30L).containsEntry(422, 10L);
        assertThat(api.balanceOf(from)).isEqualByComparingTo("0.00");
        assertThat(api.balanceOf(to)).isEqualByComparingTo("300.00");
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Opposite transfers between the same two accounts do not deadlock and conserve money")
    void opposite_transfers_conserve_money() throws Exception {
        String a = api.openFundedAccount("1000.00");
        String b = api.openFundedAccount("1000.00");

        List<Integer> statuses = runInParallel(40, i -> () -> (i % 2 == 0
                ? api.transfer(a, b, TestData.cad("7.00"))
                : api.transfer(b, a, TestData.cad("3.00"))).statusCode());

        assertThat(statuses).containsOnly(201);
        BigDecimal total = api.balanceOf(a).add(api.balanceOf(b));
        assertThat(total).isEqualByComparingTo("2000.00");
        assertThat(api.balanceOf(a)).isEqualByComparingTo("920.00");
    }

    private static <T> List<T> runInParallel(int count, Function<Integer, Callable<T>> task) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(task.apply(i)));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }
}
