package com.suncorp.securehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserSummary user;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserSummary {
        private Long id;
        private String username;
        private String email;
        private java.util.Set<String> roles;
    }
}
