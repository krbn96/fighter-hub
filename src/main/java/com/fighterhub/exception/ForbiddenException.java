package com.fighterhub.exception;

// 認証済みだが操作を許可されていない場合の共通例外
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
