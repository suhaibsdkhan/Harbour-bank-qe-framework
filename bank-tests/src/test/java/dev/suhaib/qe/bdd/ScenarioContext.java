package dev.suhaib.qe.bdd;

import dev.suhaib.qe.api.BankApi;
import dev.suhaib.qe.db.BankDb;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;

/** Per-scenario state shared between step classes; PicoContainer creates one per scenario. */
public class ScenarioContext {

    public final BankApi api = new BankApi();
    public final BankDb db = new BankDb();

    /** Customer alias used in the feature file (e.g. "Alice") to real account number. */
    private final Map<String, String> accounts = new HashMap<>();

    public Response lastResponse;
    public String lastTransferReference;

    public void rememberAccount(String alias, String accountNumber) {
        accounts.put(alias, accountNumber);
    }

    public String account(String alias) {
        String number = accounts.get(alias);
        if (number == null) {
            throw new IllegalArgumentException("No account opened for '" + alias + "' in this scenario");
        }
        return number;
    }
}
