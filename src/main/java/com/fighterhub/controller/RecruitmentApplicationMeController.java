package com.fighterhub.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fighterhub.dto.ErrorResponse;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.service.RecruitmentApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

// RecruitmentApplicationControllerは/api/teams配下のためGET /api/applications/meを
// 表現できず、責務(本人の申請一覧取得)も異なるため別Controllerとして分離する。
@RestController
@RequestMapping("/api/applications")
public class RecruitmentApplicationMeController {

    private final RecruitmentApplicationService recruitmentApplicationService;

    public RecruitmentApplicationMeController(RecruitmentApplicationService recruitmentApplicationService) {
        this.recruitmentApplicationService = recruitmentApplicationService;
    }

    @Operation(
        summary = "自分の参加申請一覧取得",
        description = "認証済みユーザー本人が送った参加申請の一覧を取得します。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "自分の申請一覧取得成功"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "認証されていない、または無効なJWT"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "サーバーエラー",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/me")
    public List<RecruitmentApplicationResponse> findMyApplications(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return recruitmentApplicationService.findMyApplications(userId);
    }
}
