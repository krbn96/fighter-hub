package com.fighterhub.security;

// OAuth stateの有効期限(5分)が過ぎている場合の例外。
// APIレスポンスへは直接公開せず、DiscordOAuthServiceがcallbackのredirect reasonへ変換する。
public class OAuthStateExpiredException extends RuntimeException {

    public OAuthStateExpiredException(String message) {
        super(message);
    }
}
