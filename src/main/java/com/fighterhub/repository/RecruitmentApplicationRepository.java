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

    // 「同一TournamentでUserがTeamMemberになった時点で、そのUserが同一Tournament内の
    // 他TeamへのPENDING申請をREJECTEDへ遷移する」business rule専用。対象User+Tournamentの
    // PENDING申請をすべて行ロック付きで取得する(TeamService#createTeamから使用)。
    // ORDER BY ra.id ASCにより、複数行を一括ロックする際の取得順序を常に昇順に固定している。
    // PostgreSQLのSELECT ... FOR UPDATEはORDER BY適用後の順序で1行ずつロックを取得するため、
    // 同じ行集合をロックしようとする別Transaction(RecruitmentApplicationService#approveApplicationの
    // findTargetAndPendingForUpdateOrderByIdも同じID昇順)がいても、取得順序が食い違わず
    // deadlockが起こり得ない。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ra FROM RecruitmentApplication ra
            WHERE ra.user.id = :userId
              AND ra.team.tournament.id = :tournamentId
              AND ra.status = :status
            ORDER BY ra.id ASC
            """)
    List<RecruitmentApplication> findByUser_IdAndTeam_Tournament_IdAndStatusForUpdate(
            @Param("userId") Long userId,
            @Param("tournamentId") Long tournamentId,
            @Param("status") RecruitmentApplicationStatus status);

    // RecruitmentApplicationService#approveApplication専用。承認対象Application(id一致、
    // statusを問わない) + 対象と同じUser/同一TournamentのPENDING Applicationを、すべて
    // ID昇順で一括してPESSIMISTIC_WRITEロックする。
    //
    // 旧実装では「対象ApplicationをfindByIdForUpdateで先にロック→他PENDINGを別クエリで
    // 後からロック」という2段階だったため、異なるTeam Ownerが同一Userの異なるPENDING
    // Applicationをほぼ同時にapproveすると、T1が対象Aを保持したまま他方Bを待ち、
    // T2が対象Bを保持したまま他方Aを待つ、という循環待ち(deadlock)が起こり得た。
    // 対象+他PENDINGを1つのクエリにまとめてID昇順でロックすることで、
    // どちらのTransactionも必ず同じ順序でロックを試みるようになり、循環待ちを構造的になくす。
    //
    // 対象の所有者Userは外部から渡さず、相関サブクエリ(applicationIdから直接解決)で求める。
    // 事前にSercice側でfindByIdなどにより対象ApplicationをEntityとして読み込んでしまうと、
    // そのEntityがこのTransactionのpersistence contextに管理対象として乗ってしまい、
    // このロック取得クエリが(他Transactionのcommit待ちで一度ブロックしたのち)実際には
    // 最新のstatusで行を返しても、Hibernateが「既に管理中の同一Entityインスタンス」を
    // 再利用し、読み込み時点の古いstatus値を上書きしない、という罠がある
    // (実際にこれが原因でApplicationAlreadyProcessedExceptionになるべきケースが
    //  DuplicateTournamentMembershipExceptionになる不具合を統合テストで検出し、
    //  このSubquery方式に修正した)。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ra FROM RecruitmentApplication ra
            WHERE ra.id = :applicationId
               OR (ra.status = :status
                   AND ra.team.tournament.id = :tournamentId
                   AND ra.user.id = (
                       SELECT target.user.id FROM RecruitmentApplication target
                       WHERE target.id = :applicationId
                   ))
            ORDER BY ra.id ASC
            """)
    List<RecruitmentApplication> findTargetAndPendingForUpdateOrderById(
            @Param("applicationId") Long applicationId,
            @Param("tournamentId") Long tournamentId,
            @Param("status") RecruitmentApplicationStatus status);

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
