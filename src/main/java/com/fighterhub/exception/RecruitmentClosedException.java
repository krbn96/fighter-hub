package com.fighterhub.exception;

// 募集締切(Tournament.recruitmentDeadline)後に、新規Team作成または新規RecruitmentApplication
// 作成を試みた場合の例外。両ユースケースで原因は共通(募集期間外)のため、1クラスを両方から
// 再利用する(DuplicateTournamentMembershipExceptionが複数Serviceから再利用されているのと同じ方針)。
public class RecruitmentClosedException extends ResourceConflictException {

    public RecruitmentClosedException(Long tournamentId) {
        super("Recruitment is closed for this tournament. tournamentId=" + tournamentId);
    }
}
