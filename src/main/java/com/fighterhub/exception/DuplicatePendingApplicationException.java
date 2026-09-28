package com.fighterhub.exception;

// 同一Tournament内でPENDING状態の参加申請がすでに存在する場合の例外。
// 「1ユーザーが同一Tournament内で同時に持てるPENDING申請は1件だけ」という仕様のため、
// Team単位ではなくTournament単位で判定する(Day 6で意味を変更)。
public class DuplicatePendingApplicationException extends ResourceConflictException {

    public DuplicatePendingApplicationException(Long tournamentId, Long userId) {
        super("Pending application already exists in this tournament. tournamentId="
                + tournamentId + ", userId=" + userId);
    }
}
