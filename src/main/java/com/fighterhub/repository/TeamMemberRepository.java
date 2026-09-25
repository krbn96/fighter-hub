package com.fighterhub.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fighterhub.entity.TeamMember;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    // 同じTeamへの二重所属確認
    boolean existsByTeam_IdAndUser_Id(Long teamId, Long userId);

    // teamId+userIdで一意に絞り込めるため、JOIN FETCH不要のderived query。
    Optional<TeamMember> findByTeam_IdAndUser_Id(Long teamId, Long userId);

    // 同一Tournamentで1ユーザー1Teamの所属確認
    boolean existsByUser_IdAndTeam_Tournament_Id(Long userId, Long tournamentId);

    // T_TEAM_MEMBERSを基準に、指定Userが所属している(owner/非ownerを問わない)TeamMemberを取得する。
    // ownerId基準の検索は行わない。deleteFlag条件がTeam/Tournament/ownerにまたがるため
    // derived queryでは不自然になり、JPQLを使用する。JOIN FETCHでN+1を避ける。
    // (HibernateのJOIN FETCH検証上、fetchしたTeamを直接SELECTすることはできないため、
    //  FROM句のrootであるTeamMemberをSELECTし、呼び出し側でgetTeam()を取り出す)
    @Query("""
            SELECT tm FROM TeamMember tm
            JOIN FETCH tm.team t
            JOIN FETCH t.tournament tour
            JOIN FETCH t.owner o
            WHERE tm.user.id = :userId
              AND t.deleteFlag = false
              AND tour.deleteFlag = false
              AND o.deleteFlag = false
            """)
    List<TeamMember> findActiveTeamMembershipsByUserId(@Param("userId") Long userId);

    // 指定Teamの有効なUser(deleteFlag=false)を持つTeamMemberをjoinedAt昇順で取得する。
    // Teamの有効性(delete_flag/Tournament/owner)確認はTeamRepository#findActiveTeamById側の
    // 責務のため、このクエリではteam.idの一致とUserのdeleteFlagのみを条件にする。
    // JOIN FETCHでN+1を避け、Hibernate 7の制約に合わせFROM句のrootであるTeamMemberをSELECTする。
    @Query("""
            SELECT tm FROM TeamMember tm
            JOIN FETCH tm.user u
            WHERE tm.team.id = :teamId
              AND u.deleteFlag = false
            ORDER BY tm.joinedAt ASC
            """)
    List<TeamMember> findActiveTeamMembersByTeamId(@Param("teamId") Long teamId);
}
