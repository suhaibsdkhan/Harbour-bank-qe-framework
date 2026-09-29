package dev.suhaib.qe.ui.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import dev.suhaib.qe.config.TestConfig;
import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * One browser per JVM, a fresh context per test. Tests declare a {@link Page} parameter to get one.
 * On failure the extension attaches a full-page screenshot and a Playwright trace to Allure.
 */
public class PlaywrightExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(PlaywrightExtension.class);

    private static Playwright playwright;
    private static Browser browser;

    static synchronized Browser browser() {
        if (browser == null) {
            playwright = Playwright.create();
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(TestConfig.headless());
            TestConfig.value("playwright.chromium.path").map(Path::of).ifPresent(options::setExecutablePath);
            browser = playwright.chromium().launch(options);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                browser.close();
                playwright.close();
            }));
        }
        return browser;
    }

    public static BrowserContext newContext() {
        BrowserContext context = browser().newContext(new Browser.NewContextOptions()
                .setBaseURL(TestConfig.requireBaseUrl())
                .setViewportSize(1280, 900));
        context.setDefaultTimeout(TestConfig.uiTimeoutMillis());
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        return context;
    }

    /** Attaches a screenshot and trace of {@code page} to the current Allure test, then closes its context. */
    public static void finish(Page page, boolean failed) {
        BrowserContext context = page.context();
        try {
            if (failed) {
                Allure.addAttachment("Screenshot", "image/png",
                        new ByteArrayInputStream(page.screenshot(new Page.ScreenshotOptions().setFullPage(true))), "png");
                Path trace = Files.createTempFile("trace", ".zip");
                context.tracing().stop(new Tracing.StopOptions().setPath(trace));
                Allure.addAttachment("Playwright trace (open at trace.playwright.dev)", "application/zip",
                        Files.newInputStream(trace), "zip");
            } else {
                context.tracing().stop();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            context.close();
        }
    }

    @Override
    public void beforeEach(ExtensionContext ctx) {
        ctx.getStore(NS).put(Page.class, newContext().newPage());
    }

    @Override
    public void afterEach(ExtensionContext ctx) {
        Page page = ctx.getStore(NS).remove(Page.class, Page.class);
        if (page != null) {
            finish(page, ctx.getExecutionException().isPresent());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext param, ExtensionContext ctx) {
        return param.getParameter().getType() == Page.class;
    }

    @Override
    public Object resolveParameter(ParameterContext param, ExtensionContext ctx) {
        return ctx.getStore(NS).get(Page.class, Page.class);
    }
}
