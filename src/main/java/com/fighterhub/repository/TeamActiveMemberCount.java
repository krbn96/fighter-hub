package com.fighterhub.repository;

// TeamMemberRepository#countActiveMembersByTeamIdsの集計結果projection。
// チーム検索のavailable判定で、対象Team群の有効メンバー数をTeamごとのcount queryに分割せず
// 1回のクエリでまとめて取得するために使用する(N+1回避)。
public interface TeamActiveMemberCount {
    Long getTeamId();

    long getActiveMemberCount();
}
