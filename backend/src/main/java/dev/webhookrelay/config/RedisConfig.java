package dev.webhookrelay.config;

import dev.webhookrelay.relay.RedisEventRelay;
import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfig {

    /** Redis channel used to broadcast captured webhook events to all app instances. */
    public static final String EVENTS_CHANNEL = "webhookrelay.events";

    /**
     * Enables Lettuce command-level metrics so the dashboard can chart Redis operation
     * throughput/latency ({@code lettuce_command_completion_seconds_*}). Spring Boot's
     * Lettuce auto-config picks up this {@link ClientResources} bean automatically.
     */
    @Bean(destroyMethod = "shutdown")
    public ClientResources lettuceClientResources(MeterRegistry meterRegistry) {
        return DefaultClientResources.builder()
                .commandLatencyRecorder(
                        new MicrometerCommandLatencyRecorder(meterRegistry, MicrometerOptions.create()))
                .build();
    }

    @Bean
    public ChannelTopic eventsTopic() {
        return new ChannelTopic(EVENTS_CHANNEL);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    @Bean
    public MessageListenerAdapter eventsListenerAdapter(RedisEventRelay relay) {
        // Invokes RedisEventRelay#onMessage(String) for every payload on the channel.
        return new MessageListenerAdapter(relay, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory cf,
            MessageListenerAdapter eventsListenerAdapter,
            ChannelTopic eventsTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        container.addMessageListener(eventsListenerAdapter, eventsTopic);
        return container;
    }
}
