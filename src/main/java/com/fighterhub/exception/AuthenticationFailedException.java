package com.fighterhub.exception;

// email不存在・password不一致・deleteFlag=true・passwordHash=nullのいずれも区別せず、
// 常に同一メッセージで認証失敗を表す例外。
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Invalid email or password.");
    }
}
