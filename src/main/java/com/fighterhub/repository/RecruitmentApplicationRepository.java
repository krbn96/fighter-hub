package com.fighterhub.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;

public interface RecruitmentApplicationRepository extends JpaRepository<RecruitmentApplication, Long> {

    // 同じTeamに同じUserの指定statusの申請が存在するか確認する。
    // Day 3の参加申請作成時は、statusにPENDINGを渡して重複申請チェックに使用する。
    boolean existsByTeam_IdAndUser_IdAndStatus(Long teamId, Long userId, RecruitmentApplicationStatus status);

    // Team ownerが自身のTeamへの参加申請一覧を確認するために使用する。
    // PENDING/APPROVED/REJECTEDいずれも含む申請履歴をcreatedAt降順で返す(statusによる絞り込みはしない)。
    // Response変換でUser(userName)を使用するためJOIN FETCHでN+1を避ける。
    // Teamは呼び出し側のService(既にfindActiveTeamByIdで取得済み)を使うため、ここではFETCHしない。
    @Query("""
            SELECT ra FROM RecruitmentApplication ra
            JOIN FETCH ra.user u
            WHERE ra.team.id = :teamId
            ORDER BY ra.createdAt DESC
            """)
    List<RecruitmentApplication> findByTeamIdOrderByCreatedAtDesc(@Param("teamId") Long teamId);

    // ログインユーザー本人が送った参加申請一覧を取得する。
    // 取得結果はすべて同一User(呼び出し元本人)のため、Userの遅延ロードは初回アクセス時に
    // 1回発生するのみでN+1にはならず、JOIN FETCHは不要と判断しderived queryのみで実装する。
    List<RecruitmentApplication> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
