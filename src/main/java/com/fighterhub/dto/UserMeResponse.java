package com.fighterhub.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.fighterhub.entity.UserRole;

public record UserMeResponse(
    Long id,
    String name,
    List<UserCharacterResponse> characters,
    LocalTime playTimeStart,
    LocalTime playTimeEnd,
    String message,
    String email,
    String xId,
    String discordUsername,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    // Frontendが本人のADMIN権限を判定するために必要(ADMIN専用大会管理画面等)。
    // クライアントからroleを指定・変更できるAPIは提供しない(読み取り専用)。
    UserRole role
) {
}
