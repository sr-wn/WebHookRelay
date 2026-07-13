package dev.webhookrelay.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Translates platform-provided connection strings into the property names Spring Boot
 * expects, so the app can be deployed to Render (and Heroku-style platforms) with zero
 * dashboard fiddling.
 *
 * <p>Render exposes a single {@code DATABASE_URL} ({@code postgres://user:pass@host:port/db})
 * and {@code REDIS_URL} ({@code redis://:pass@host:port}). Spring's datasource needs a
 * {@code jdbc:} URL plus separate username/password, so we parse {@code DATABASE_URL} into
 * those three properties. {@code REDIS_URL} is handed straight to
 * {@code spring.data.redis.url}, which Spring Boot parses natively (auth + TLS included).
 *
 * <p>Only activates when the vars are present, so local/compose/test runs (which set
 * {@code SPRING_DATASOURCE_URL} etc. directly) are untouched.
 */
public class ConnectionUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "webhookrelay-connection-urls";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        Map<String, Object> props = new HashMap<>();

        String databaseUrl = env.getProperty("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank()
                && !databaseUrl.startsWith("jdbc:")) {
            applyDatabaseUrl(databaseUrl.trim(), props);
        }

        String redisUrl = env.getProperty("REDIS_URL");
        if (redisUrl != null && !redisUrl.isBlank()) {
            props.put("spring.data.redis.url", redisUrl.trim());
        }

        if (!props.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        }
    }

    private void applyDatabaseUrl(String databaseUrl, Map<String, Object> props) {
        URI uri = URI.create(databaseUrl);
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            String[] parts = userInfo.split(":", 2);
            props.put("spring.datasource.username", parts[0]);
            if (parts.length > 1) {
                props.put("spring.datasource.password", parts[1]);
            }
        }
        String scheme = "postgres".equals(uri.getScheme()) ? "postgresql" : uri.getScheme();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String path = uri.getPath() == null ? "" : uri.getPath();
        props.put("spring.datasource.url",
                "jdbc:%s://%s:%d%s".formatted(scheme, uri.getHost(), port, path));
    }
}
