package dev.webhookrelay.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionUrlEnvironmentPostProcessorTest {

    private final ConnectionUrlEnvironmentPostProcessor processor =
            new ConnectionUrlEnvironmentPostProcessor();
    private final SpringApplication app = new SpringApplication();

    @Test
    void mapsRenderDatabaseUrlToJdbcAndCredentials() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DATABASE_URL", "postgres://relay:secret@dpg-abc:5432/webhookrelay");

        processor.postProcessEnvironment(env, app);

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dpg-abc:5432/webhookrelay");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("relay");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("secret");
    }

    @Test
    void defaultsPortWhenMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DATABASE_URL", "postgres://u:p@host/db");

        processor.postProcessEnvironment(env, app);

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://host:5432/db");
    }

    @Test
    void mapsRedisUrlNatively() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("REDIS_URL", "redis://:pw@redis-host:6380");

        processor.postProcessEnvironment(env, app);

        assertThat(env.getProperty("spring.data.redis.url")).isEqualTo("redis://:pw@redis-host:6380");
    }

    @Test
    void ignoresAlreadyJdbcUrl() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DATABASE_URL", "jdbc:postgresql://host:5432/db");

        processor.postProcessEnvironment(env, app);

        assertThat(env.getProperty("spring.datasource.username")).isNull();
        assertThat(env.getProperty("spring.datasource.password")).isNull();
    }

    @Test
    void noopWhenNoUrlsPresent() {
        MockEnvironment env = new MockEnvironment();

        processor.postProcessEnvironment(env, app);

        assertThat(env.getProperty("spring.datasource.url")).isNull();
        assertThat(env.getProperty("spring.data.redis.url")).isNull();
    }
}
