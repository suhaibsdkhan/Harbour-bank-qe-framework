package dev.suhaib.qe.support;

import dev.suhaib.bank.BankApplication;
import dev.suhaib.qe.config.TestConfig;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots the bank once per test run, before JUnit or Cucumber discover anything.
 *
 * <p>If {@code base.url} is already set the tests target that environment and nothing is started.
 * Otherwise the app runs in-process on a random port, backed by {@code db.url} when given
 * (Postgres in CI) or an in-memory H2 database, and both URLs are published as system properties.
 */
public class AppLauncher implements LauncherSessionListener {

    private static final String DEFAULT_DB_URL = "jdbc:h2:mem:bank-tests;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

    private ConfigurableApplicationContext app;

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        if (TestConfig.baseUrl().isPresent() || app != null) {
            return;
        }
        String dbUrl = TestConfig.dbUrl().orElse(DEFAULT_DB_URL);

        // Passed as command-line args so they win over the app's own application.yml.
        String[] args = {
                "--server.port=0",
                "--spring.datasource.url=" + dbUrl,
                "--spring.datasource.username=" + TestConfig.dbUser(),
                "--spring.datasource.password=" + TestConfig.dbPassword(),
                "--spring.main.banner-mode=off",
                "--logging.level.root=WARN",
        };
        app = new SpringApplicationBuilder(BankApplication.class).run(args);
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();

        System.setProperty("base.url", "http://localhost:" + port);
        System.setProperty("db.url", dbUrl);
        System.out.println("[AppLauncher] Bank started at http://localhost:" + port + " using " + dbUrl);
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        if (app != null) {
            app.close();
            app = null;
        }
    }
}
