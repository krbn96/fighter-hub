package com.fighterhub.dto;

import java.time.LocalDateTime;

import com.fighterhub.entity.RecruitmentApplicationStatus;

public record RecruitmentApplicationResponse(
    Long id,
    Long teamId,
    Long userId,
    String userName,
    String message,
    RecruitmentApplicationStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
