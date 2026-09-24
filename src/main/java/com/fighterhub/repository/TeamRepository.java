package com.fighterhub.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
