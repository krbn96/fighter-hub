package com.fighterhub.exception;

// ユーザーが存在しない場合の例外
public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException(Long id) {
        super("User not found. id=" + id);
    }
}
