package com.hotelbooking.booking.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки вызовов hotel-service: адрес, тайм-аут и повторы.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "hotel")
public class HotelClientProperties {

    // имя сервиса в Eureka (или обычный адрес, если use-discovery=false)
    private String url = "http://hotel-service";
    private boolean useDiscovery = true;
    // тайм-аут на соединение и на чтение ответа
    private long timeoutMs = 3000;
    // всего попыток, включая первую
    private int maxAttempts = 3;
    // пауза перед вторым вызовом, дальше умножается на multiplier
    private long backoffMs = 500;
    private double backoffMultiplier = 2.0;
}
