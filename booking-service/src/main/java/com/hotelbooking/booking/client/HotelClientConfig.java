package com.hotelbooking.booking.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class HotelClientConfig {

    // адрес hotel-service берётся из Eureka (http://hotel-service)
    @Bean
    @LoadBalanced
    @ConditionalOnProperty(name = "hotel.use-discovery", havingValue = "true", matchIfMissing = true)
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    // без Eureka - в тестах, где вместо hotel-service заглушка WireMock
    @Bean
    @ConditionalOnProperty(name = "hotel.use-discovery", havingValue = "false")
    public RestClient.Builder plainRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient hotelRestClient(RestClient.Builder builder, HotelClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        return builder
                .baseUrl(properties.getUrl())
                .requestFactory(factory)
                .build();
    }
}
