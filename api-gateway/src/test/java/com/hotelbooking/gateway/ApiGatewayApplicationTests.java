package com.hotelbooking.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routesAreLoaded() {
        List<String> ids = routeLocator.getRoutes().map(r -> r.getId()).collectList().block();
        assertThat(ids).contains("booking-service", "hotel-service-hotels", "hotel-service-rooms");
    }
}
