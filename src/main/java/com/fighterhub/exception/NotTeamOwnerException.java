package com.fighterhub.exception;

// ログインUserがTeamのownerではない状態で、owner専用操作を行おうとした場合の例外
public class NotTeamOwnerException extends ForbiddenException {

    public NotTeamOwnerException(Long userId, Long teamId) {
        super("User is not the owner of this team. userId=" + userId + ", teamId=" + teamId);
    }
}
