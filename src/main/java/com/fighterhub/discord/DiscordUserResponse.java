package com.fighterhub.discord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Discord /users/@me のレスポンスのうち、内部識別子(id)と公開用表示名(username)のみを保持する。
// email・avatar・mfa_enabled等の個人情報・不要なフィールドは一切保持しない。
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscordUserResponse(
    String id,
    String username
) {
}
