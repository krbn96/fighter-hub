package com.fighterhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fighterhub.entity.TeamMember;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    // 同じTeamへの二重所属確認
    boolean existsByTeam_IdAndUser_Id(Long teamId, Long userId);

    // 同一Tournamentで1ユーザー1Teamの所属確認
    boolean existsByUser_IdAndTeam_Tournament_Id(Long userId, Long tournamentId);
}
