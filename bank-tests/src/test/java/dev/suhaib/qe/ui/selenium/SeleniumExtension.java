package dev.suhaib.qe.ui.selenium;

import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/** A fresh WebDriver per test, injected as a parameter; screenshot and page source are attached on failure. */
public class SeleniumExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(SeleniumExtension.class);

    @Override
    public void beforeEach(ExtensionContext ctx) {
        ctx.getStore(NS).put(WebDriver.class, DriverFactory.create());
    }

    @Override
    public void afterEach(ExtensionContext ctx) {
        WebDriver driver = ctx.getStore(NS).remove(WebDriver.class, WebDriver.class);
        if (driver == null) {
            return;
        }
        try {
            if (ctx.getExecutionException().isPresent()) {
                byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                Allure.addAttachment("Screenshot", "image/png", new ByteArrayInputStream(png), "png");
                Allure.addAttachment("Page source", "text/html", driver.getPageSource(), "html");
            }
        } finally {
            driver.quit();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext param, ExtensionContext ctx) {
        return param.getParameter().getType() == WebDriver.class;
    }

    @Override
    public Object resolveParameter(ParameterContext param, ExtensionContext ctx) {
        return ctx.getStore(NS).get(WebDriver.class, WebDriver.class);
    }
}
