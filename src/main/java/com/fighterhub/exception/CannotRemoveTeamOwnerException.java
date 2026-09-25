package com.fighterhub.exception;

// Team ownerを自分自身のTeamMemberから削除しようとした場合の例外
public class CannotRemoveTeamOwnerException extends ResourceConflictException {

    public CannotRemoveTeamOwnerException(Long teamId, Long userId) {
        super("Team owner cannot be removed from the team. teamId="
                + teamId + ", userId=" + userId);
    }
}
