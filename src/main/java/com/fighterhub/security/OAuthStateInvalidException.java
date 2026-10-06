package com.fighterhub.security;

// OAuth stateの署名・形式・purposeが不正な場合の例外。
// APIレスポンスへは直接公開せず、DiscordOAuthServiceがcallbackのredirect reasonへ変換する。
public class OAuthStateInvalidException extends RuntimeException {

    public OAuthStateInvalidException(String message) {
        super(message);
    }
}
