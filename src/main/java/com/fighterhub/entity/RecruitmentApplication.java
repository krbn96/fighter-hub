package com.fighterhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

import com.fighterhub.exception.ApplicationAlreadyProcessedException;

@Entity
@Table(name = "t_recruitment_application")
public class RecruitmentApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RecruitmentApplicationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected RecruitmentApplication() {
    }

    // 新規申請は常にPENDINGで生成する。
    public static RecruitmentApplication create(Team team, User user, String message) {
        RecruitmentApplication application = new RecruitmentApplication();
        application.team = team;
        application.user = user;
        application.message = message;
        application.status = RecruitmentApplicationStatus.PENDING;

        return application;
    }

    // PENDINGからのみAPPROVEDへ遷移できる。APPROVED/REJECTEDからの再変更は禁止する。
    public void approve() {
        if (status != RecruitmentApplicationStatus.PENDING) {
            throw new ApplicationAlreadyProcessedException(id);
        }
        this.status = RecruitmentApplicationStatus.APPROVED;
    }

    // PENDINGからのみREJECTEDへ遷移できる。APPROVED/REJECTEDからの再変更は禁止する。
    public void reject() {
        if (status != RecruitmentApplicationStatus.PENDING) {
            throw new ApplicationAlreadyProcessedException(id);
        }
        this.status = RecruitmentApplicationStatus.REJECTED;
    }

    public Long getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public User getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    public RecruitmentApplicationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
