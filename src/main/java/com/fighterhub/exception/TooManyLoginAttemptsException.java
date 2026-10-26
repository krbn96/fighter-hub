package com.fighterhub.exception;

// ログインのレート制限(IP単位またはIP+email単位)に達した場合の例外。
// どちらの制限が原因で発生したかを外部(レスポンス)へ一切公開しない
// (原因によって応答が変わると、email存在有無等の推測材料になり得るため)。
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Too many login attempts. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
