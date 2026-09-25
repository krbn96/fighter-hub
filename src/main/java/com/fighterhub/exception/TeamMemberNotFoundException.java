package com.fighterhub.exception;

// 指定teamId+userIdに対応するTeamMemberが存在しない場合の例外
public class TeamMemberNotFoundException extends ResourceNotFoundException {

    public TeamMemberNotFoundException(Long teamId, Long userId) {
        super("TeamMember not found. teamId=" + teamId + ", userId=" + userId);
    }
}
