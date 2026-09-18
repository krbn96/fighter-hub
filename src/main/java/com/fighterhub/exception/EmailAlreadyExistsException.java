package com.fighterhub.exception;

// emailが既に登録されている場合の例外
public class EmailAlreadyExistsException extends ResourceConflictException {

    public EmailAlreadyExistsException(String email) {
        super("Email already exists. email=" + email);
    }
}
