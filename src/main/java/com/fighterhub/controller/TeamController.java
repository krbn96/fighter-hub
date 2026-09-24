package com.fighterhub.controller;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fighterhub.dto.ErrorResponse;
import com.fighterhub.dto.TeamCreateRequest;
import com.fighterhub.dto.TeamCreateResponse;
import com.fighterhub.dto.TeamResponse;
import com.fighterhub.dto.TeamUpdateRequest;
import com.fighterhub.service.TeamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @Operation(
        summary = "チーム作成",
        description = "認証済みユーザーをownerとして新しいチームを作成します。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "作成成功",
            content = @Content(
                schema = @Schema(implementation = TeamCreateResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "リクエストが不正、またはBean Validationエラー",
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
            description = "指定したtournamentIdが存在しない",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "同一Tournament内ですでにいずれかのTeamへ所属している",
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
    @PostMapping
    public TeamCreateResponse createTeam(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TeamCreateRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        return teamService.createTeam(userId, request);
    }

    @Operation(
        summary = "チーム一覧取得",
        description = "有効なチームの一覧を取得します。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "取得成功"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "サーバーエラー",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping
    public List<TeamResponse> findAllTeams() {
        return teamService.findAllTeams();
    }

    @Operation(
        summary = "チーム詳細取得",
        description = "指定したIDのチームを取得します。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "取得成功",
            content = @Content(
                schema = @Schema(implementation = TeamResponse.class)
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
    @GetMapping("/{id}")
    public TeamResponse findTeamById(@PathVariable Long id) {
        return teamService.findTeamById(id);
    }

    @Operation(
        summary = "チーム更新",
        description = "Team ownerが自身のチーム情報を部分更新します。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "更新成功",
            content = @Content(
                schema = @Schema(implementation = TeamResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "リクエストが不正、またはBean Validationエラー",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "認証されていない、または無効なJWT"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Team ownerではないユーザーによる更新",
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
    @PatchMapping("/{id}")
    public TeamResponse updateTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody TeamUpdateRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        return teamService.updateTeam(userId, id, request);
    }

    @Operation(
        summary = "自分が所属しているチーム一覧取得",
        description = "認証済みユーザーがTeamMemberとして所属しているチームの一覧を取得します。"
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "取得成功"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "認証されていない、または無効なJWT"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "JWTのsubに対応するユーザーが存在しない",
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
    @GetMapping("/my")
    public List<TeamResponse> findMyTeams(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return teamService.findMyTeams(userId);
    }
}
