package com.fighterhub.dto;

import jakarta.validation.constraints.Size;

public record RecruitmentApplicationCreateRequest(
    @Size(max = 1000)
    String message
) {
}
