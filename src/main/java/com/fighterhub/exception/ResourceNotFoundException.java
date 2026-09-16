package com.fighterhub.exception;

// リソースが存在しない場合の共通例外
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}