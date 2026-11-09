package com.hotelbooking.hotel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Кладёт bookingId и requestId из заголовков в MDC, чтобы они попадали в каждую строку лога.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String BOOKING_ID_HEADER = "X-Booking-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bookingId = request.getHeader(BOOKING_ID_HEADER);
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        try {
            if (bookingId != null) {
                MDC.put("bookingId", bookingId);
            }
            if (requestId != null) {
                MDC.put("requestId", requestId);
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove("bookingId");
            MDC.remove("requestId");
        }
    }
}
