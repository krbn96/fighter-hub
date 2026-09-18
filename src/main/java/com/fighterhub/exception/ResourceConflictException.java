package com.fighterhub.exception;

// リソースが競合する場合の共通例外
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
