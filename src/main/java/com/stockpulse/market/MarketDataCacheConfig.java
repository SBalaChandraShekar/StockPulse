package com.stockpulse.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class MarketDataCacheConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                              ObjectMapper objectMapper,
                              MarketDataProperties marketDataProperties) {
        Jackson2JsonRedisSerializer<MarketQuote> quoteSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, MarketQuote.class);

        RedisCacheConfiguration defaultCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(marketDataProperties.quoteTtlSeconds()));

        RedisCacheConfiguration quotesCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(quoteSerializer))
                .entryTtl(Duration.ofSeconds(marketDataProperties.quoteTtlSeconds()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultCacheConfiguration)
                .withCacheConfiguration("quotes", quotesCacheConfiguration)
                .build();
    }
}
