package com.fighterhub.exception;

// TeamのメンバーがTournament.teamSizeの定員に達している状態で、
// 参加申請を承認しようとした場合の例外
public class TeamFullException extends ResourceConflictException {

    public TeamFullException(Long teamId) {
        super("Team is full. teamId=" + teamId);
    }
}
