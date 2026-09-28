package com.fighterhub.repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;

public interface RecruitmentApplicationRepository extends JpaRepository<RecruitmentApplication, Long> {

    // 同じUserが同一Tournament内で指定statusの申請を持っているか確認する。
    // Day 6より「同一Tournament内で同時に持てるPENDING申請は1件だけ」という仕様のため、
    // Team単位ではなくTournament単位で確認する(旧existsByTeam_IdAndUser_IdAndStatusは廃止)。
    boolean existsByUser_IdAndTeam_Tournament_IdAndStatus(
            Long userId, Long tournamentId, RecruitmentApplicationStatus status);

    // approve/reject処理中に同じApplicationへ同時にapprove+approve/approve+reject/reject+rejectが
    // 実行された場合でも、PENDING状態を同時に読み取らないようにするための行ロック付き取得。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ra FROM RecruitmentApplication ra WHERE ra.id = :id")
    Optional<RecruitmentApplication> findByIdForUpdate(@Param("id") Long id);

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
