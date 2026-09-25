package com.fighterhub.exception;

// 自分がownerのTeamへ参加申請しようとした場合の例外
public class CannotApplyToOwnTeamException extends InvalidRequestException {

    public CannotApplyToOwnTeamException(Long userId, Long teamId) {
        super("User cannot apply to their own team. userId=" + userId + ", teamId=" + teamId);
    }
}
