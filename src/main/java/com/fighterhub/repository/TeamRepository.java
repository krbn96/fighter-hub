package com.fighterhub.repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fighterhub.entity.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

    // deleteFlag条件がTeam/Tournament/ownerの3Entityにまたがり、derived queryでは
    // メソッド名が過度に長く不自然になるためJPQLを使用する。
    // 一覧取得時のTournament/ownerのLAZYロードによるN+1を避けるため、JOIN FETCHで
    // 併せて取得する。
    @Query("""
            SELECT t FROM Team t
            JOIN FETCH t.tournament tour
            JOIN FETCH t.owner o
            WHERE t.deleteFlag = false
              AND tour.deleteFlag = false
              AND o.deleteFlag = false
            """)
    List<Team> findAllActiveTeams();

    @Query("""
            SELECT t FROM Team t
            JOIN FETCH t.tournament tour
            JOIN FETCH t.owner o
            WHERE t.id = :id
              AND t.deleteFlag = false
              AND tour.deleteFlag = false
              AND o.deleteFlag = false
            """)
    Optional<Team> findActiveTeamById(@Param("id") Long id);

    // findAllActiveTeamsと同じ有効性条件(Team/Tournament/ownerのdeleteFlag)に
    // tournamentId絞り込みを加えたもの。大会ごとのチーム一覧取得APIで使用する。
    @Query("""
            SELECT t FROM Team t
            JOIN FETCH t.tournament tour
            JOIN FETCH t.owner o
            WHERE tour.id = :tournamentId
              AND t.deleteFlag = false
              AND tour.deleteFlag = false
              AND o.deleteFlag = false
            """)
    List<Team> findActiveTeamsByTournamentId(@Param("tournamentId") Long tournamentId);

    // 同じTeamに対して複数Applicationがほぼ同時にapproveされた場合に、両Transactionが
    // 同じmember countを見て定員を超過することを防止するための行ロック付き取得。
    // 有効Teamの判定条件はfindActiveTeamByIdと同じ。ロック対象をTeam行のみに限定するため、
    // tournament/ownerはJOIN FETCHではなく通常のJOINで条件確認にのみ使用する。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM Team t
            JOIN t.tournament tour
            JOIN t.owner o
            WHERE t.id = :id
              AND t.deleteFlag = false
              AND tour.deleteFlag = false
              AND o.deleteFlag = false
            """)
    Optional<Team> findActiveTeamByIdForUpdate(@Param("id") Long id);
}
