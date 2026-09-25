package com.fighterhub.controller;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fighterhub.dto.ErrorResponse;
import com.fighterhub.dto.RecruitmentApplicationCreateRequest;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.service.RecruitmentApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/teams")
public class RecruitmentApplicationController {

    private final RecruitmentApplicationService recruitmentApplicationService;

    public RecruitmentApplicationController(RecruitmentApplicationService recruitmentApplicationService) {
        this.recruitmentApplicationService = recruitmentApplicationService;
    }

    @Operation(
        summary = "チーム参加申請",
        description = "認証済みユーザーが指定したチームへの参加を申請します。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "申請作成成功",
            content = @Content(
                schema = @Schema(implementation = RecruitmentApplicationResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "自分がownerのチームへ申請した場合など",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "認証されていない、または無効なJWT"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "指定したチームが存在しない",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "同一大会内ですでにいずれかのチームへ所属している、またはこのチームへPENDING状態の申請がすでに存在する",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "サーバーエラー",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{teamId}/applications")
    public RecruitmentApplicationResponse createApplication(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId,
            @Valid @RequestBody RecruitmentApplicationCreateRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        return recruitmentApplicationService.createApplication(userId, teamId, request);
    }

    @Operation(
        summary = "チーム参加申請一覧取得",
        description = "Team ownerが、自身のチームに届いた参加申請一覧を取得します。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "申請一覧取得成功"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "認証されていない、または無効なJWT"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Team ownerではないユーザーによる取得",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "指定したチームが存在しない",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "サーバーエラー",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/{teamId}/applications")
    public List<RecruitmentApplicationResponse> findTeamApplications(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId) {
        Long userId = Long.valueOf(jwt.getSubject());
        return recruitmentApplicationService.findTeamApplications(userId, teamId);
    }
}
