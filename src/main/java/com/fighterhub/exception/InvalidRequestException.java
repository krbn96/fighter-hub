package com.fighterhub.exception;

// リクエスト内容がビジネスルール上不正な場合の共通例外
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
