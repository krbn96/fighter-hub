package com.fighterhub.exception;

// ログインUserがADMINロールを持たない状態で、ADMIN専用操作を行おうとした場合の例外
public class NotAdminException extends ForbiddenException {

    public NotAdminException(Long userId) {
        super("User is not an admin. userId=" + userId);
    }
}
