package com.suncorp.securehub.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(
                "thisIsAVeryLongSecretKeyForJWTSigningThatIsAtLeast256BitsLongForHS256Algorithm",
                86400000L
        );
    }

    @Test
    void generateToken_thenValidate_shouldReturnTrue() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                new org.springframework.security.core.userdetails.User(
                        "testuser", "pass", List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        String token = tokenProvider.generateToken(auth);
        assertThat(token).isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void getUsernameFromToken_shouldReturnCorrectUsername() {
        String token = tokenProvider.generateTokenFromUsername("john");
        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo("john");
    }

    @Test
    void validateToken_withTamperedToken_shouldReturnFalse() {
        assertThat(tokenProvider.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void constructor_withBlankSecret_shouldThrow() {
        assertThatThrownBy(() -> new JwtTokenProvider("   ", 86400000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be configured and non-empty");
    }

    @Test
    void constructor_withTooShortSecret_shouldThrow() {
        assertThatThrownBy(() -> new JwtTokenProvider("short-secret", 86400000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be at least 32 characters");
    }
}
