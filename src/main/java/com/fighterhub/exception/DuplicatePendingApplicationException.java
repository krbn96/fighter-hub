package com.fighterhub.exception;

// 同じTeamへのPENDING状態の参加申請がすでに存在する場合の例外
public class DuplicatePendingApplicationException extends ResourceConflictException {

    public DuplicatePendingApplicationException(Long teamId, Long userId) {
        super("Pending application already exists. teamId=" + teamId + ", userId=" + userId);
    }
}
