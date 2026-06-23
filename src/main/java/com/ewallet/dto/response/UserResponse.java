package com.ewallet.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private boolean active;
    private Instant createdAt;
}
