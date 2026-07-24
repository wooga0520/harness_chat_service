package com.example.chatservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-key-must-be-at-least-32-bytes-long";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = newProvider(SECRET, 60_000L);
    }

    private JwtTokenProvider newProvider(String secret, long expirationMs) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "secret", secret);
        ReflectionTestUtils.setField(p, "expirationMs", expirationMs);
        ReflectionTestUtils.invokeMethod(p, "init");
        return p;
    }

    @Test
    void roundTripsUsernameThroughToken() {
        String token = provider.generateToken("alice");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsername(token)).isEqualTo("alice");
    }

    @Test
    void expirationIsInTheFuture() {
        Date before = new Date();

        String token = provider.generateToken("alice");

        assertThat(provider.getExpiration(token)).isAfter(before);
    }

    @Test
    void rejectsTokenSignedWithADifferentKey() {
        JwtTokenProvider otherProvider = newProvider("a-completely-different-secret-key-32bytes+", 60_000L);

        String tokenFromOtherKey = otherProvider.generateToken("alice");

        assertThat(provider.validateToken(tokenFromOtherKey)).isFalse();
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtTokenProvider shortLived = newProvider(SECRET, 1L);
        String token = shortLived.generateToken("alice");

        Thread.sleep(20);

        assertThat(shortLived.validateToken(token)).isFalse();
    }
}
