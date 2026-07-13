package dev.webhookrelay;

import dev.webhookrelay.config.WebhookRelayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(WebhookRelayProperties.class)
public class WebhookRelayApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebhookRelayApplication.class, args);
    }
}
