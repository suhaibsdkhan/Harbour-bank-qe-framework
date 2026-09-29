package dev.suhaib.bank.common;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bank")
public record BankProperties(BigDecimal transferLimit) {
}
