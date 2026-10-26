package com.fighterhub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fighterhub.dto.AuthLoginRequest;
import com.fighterhub.dto.AuthLoginResponse;
import com.fighterhub.dto.ErrorResponse;
import com.fighterhub.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
        summary = "ログイン",
        description = "メールアドレスとパスワードで認証し、JWT Access Tokenを発行します。"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "認証成功",
            content = @Content(
                schema = @Schema(implementation = AuthLoginResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "リクエストが不正",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "メールアドレスまたはパスワードが正しくない",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "ログイン試行回数の上限に達した(IP単位またはIP+email単位)。"
                + "Retry-Afterヘッダーに解除までの残り秒数を返す。",
            content = @Content(
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @PostMapping("/login")
    public AuthLoginResponse login(
            @Valid @RequestBody AuthLoginRequest request,
            HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest.getRemoteAddr());
    }
}
