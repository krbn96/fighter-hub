package com.fighterhub.discord;

// DiscordのOAuth token endpoint / /users/@me呼び出しが失敗した場合の例外。
// APIレスポンスへは直接公開せず、DiscordOAuthServiceがcallbackのredirect reason(provider_error)
// へ変換する。Discordから返されたerror_description等の詳細はメッセージへ含めない。
public class DiscordProviderException extends RuntimeException {

    public DiscordProviderException(String message) {
        super(message);
    }

    public DiscordProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
