package com.fighterhub.exception;

// 同一Tournament内で、Userがすでにいずれかのteamへ所属している場合の例外。
// Team作成時のowner登録だけでなく、将来のRecruitmentApplication経由の加入にも適用される。
public class DuplicateTournamentMembershipException extends ResourceConflictException {

    public DuplicateTournamentMembershipException(Long userId, Long tournamentId) {
        super("User already belongs to a team in this tournament. userId="
                + userId + ", tournamentId=" + tournamentId);
    }
}
