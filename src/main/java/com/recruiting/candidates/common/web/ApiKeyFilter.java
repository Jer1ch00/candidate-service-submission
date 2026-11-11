package com.recruiting.candidates.common.web;

import com.recruiting.candidates.common.error.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Service-to-service authentication with a static API key in the {@code X-API-Key} header.
 *
 * <p>Several keys can be active at once so a key can be rotated without downtime (add the new key, move clients,
 * remove the old key). Keys are compared as SHA-256 digests with {@link MessageDigest#isEqual}, which runs in constant
 * time and does not leak the key length; every configured key is checked so timing does not reveal which one matched.
 *
 * <p>Per-client identities, scopes and signed requests are future scope (see DESIGN.md).
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final List<byte[]> acceptedKeyDigests;
    private final HandlerExceptionResolver exceptionResolver;

    public ApiKeyFilter(Collection<String> acceptedKeys, HandlerExceptionResolver exceptionResolver) {
        this.acceptedKeyDigests =
                acceptedKeys.stream().map(ApiKeyFilter::sha256).toList();
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isAccepted(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey header=\"" + HEADER + "\"");
        exceptionResolver.resolveException(request, response, null, new UnauthorizedException());
    }

    boolean isAccepted(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return false;
        }
        byte[] presentedDigest = sha256(presentedKey);
        boolean accepted = false;
        for (byte[] acceptedDigest : acceptedKeyDigests) {
            accepted |= MessageDigest.isEqual(acceptedDigest, presentedDigest);
        }
        return accepted;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
