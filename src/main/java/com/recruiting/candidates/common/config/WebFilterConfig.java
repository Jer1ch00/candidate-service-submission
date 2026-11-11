package com.recruiting.candidates.common.config;

import com.recruiting.candidates.common.web.AccessLogFilter;
import com.recruiting.candidates.common.web.ApiKeyFilter;
import com.recruiting.candidates.common.web.RequestIdFilter;
import com.recruiting.candidates.common.web.RequestSizeLimitFilter;
import com.recruiting.candidates.common.web.SecurityHeadersFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Servlet filter chain, in execution order:
 *
 * <ol>
 *   <li>{@link RequestIdFilter} (all paths): correlation id for logs and error bodies.
 *   <li>{@link AccessLogFilter} (all paths): wraps everything below, so rejected requests are logged too.
 *   <li>{@link SecurityHeadersFilter} ({@code /api/*}): no-store / nosniff.
 *   <li>{@link RequestSizeLimitFilter} ({@code /api/*}): cheap size check before authentication.
 *   <li>{@link ApiKeyFilter} ({@code /api/*}): authentication.
 * </ol>
 *
 * Filters that reject a request hand their exception to the MVC {@link HandlerExceptionResolver}, so filter errors
 * use exactly the same error body as controller errors.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ApiSecurityProperties.class, RequestLimitsProperties.class})
public class WebFilterConfig {

    private static final String API_PATTERN = "/api/*";

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        return register(new RequestIdFilter(), Ordered.HIGHEST_PRECEDENCE, "/*");
    }

    @Bean
    FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
        return register(new AccessLogFilter(), Ordered.HIGHEST_PRECEDENCE + 1, "/*");
    }

    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        return register(new SecurityHeadersFilter(), Ordered.HIGHEST_PRECEDENCE + 2, API_PATTERN);
    }

    @Bean
    FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(
            RequestLimitsProperties limits,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        RequestSizeLimitFilter filter =
                new RequestSizeLimitFilter(limits.maxRequestBodySize().toBytes(), exceptionResolver);
        return register(filter, Ordered.HIGHEST_PRECEDENCE + 3, API_PATTERN);
    }

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(
            ApiSecurityProperties security,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        return register(
                new ApiKeyFilter(security.apiKeys(), exceptionResolver), Ordered.HIGHEST_PRECEDENCE + 4, API_PATTERN);
    }

    private static <F extends jakarta.servlet.Filter> FilterRegistrationBean<F> register(
            F filter, int order, String urlPattern) {
        FilterRegistrationBean<F> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(order);
        registration.addUrlPatterns(urlPattern);
        return registration;
    }
}
