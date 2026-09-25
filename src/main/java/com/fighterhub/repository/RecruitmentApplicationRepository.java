package com.fighterhub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;

public interface RecruitmentApplicationRepository extends JpaRepository<RecruitmentApplication, Long> {

    // 同じTeamに同じUserの指定statusの申請が存在するか確認する。
    // Day 3の参加申請作成時は、statusにPENDINGを渡して重複申請チェックに使用する。
    boolean existsByTeam_IdAndUser_IdAndStatus(Long teamId, Long userId, RecruitmentApplicationStatus status);
}
