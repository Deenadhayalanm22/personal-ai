package com.apps.deen_sa.v2.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Log4j2
public class WhatsAppWebhookLoggingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/webhook/whatsapp".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        log.info("WhatsApp webhook HTTP request received: method={}, contentType={}, contentLength={}, remote={}",
                request.getMethod(), request.getContentType(), request.getContentLengthLong(), request.getRemoteAddr());
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            log.info("WhatsApp webhook HTTP request completed: method={}, status={}, elapsedMs={}",
                    request.getMethod(), response.getStatus(), elapsedMs);
        }
    }
}
