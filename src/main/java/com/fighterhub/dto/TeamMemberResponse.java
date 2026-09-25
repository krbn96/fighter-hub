package com.fighterhub.dto;

import java.time.LocalDateTime;

public record TeamMemberResponse(
    Long userId,
    String userName,
    LocalDateTime joinedAt
) {
}
