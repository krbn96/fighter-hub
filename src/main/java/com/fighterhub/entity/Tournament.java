package com.fighterhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "t_tournaments")
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "team_size", nullable = false)
    private Integer teamSize;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;

    // statusの許容値・状態遷移は未確定のため、単純な文字列として保持する。
    // 詳細な設計はTournament機能を実装するタイミングで別途行う。
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "delete_flag", nullable = false)
    private boolean deleteFlag = false;

    protected Tournament() {
    }

    public static Tournament create(
            String name,
            Integer teamSize,
            LocalDateTime startAt,
            Integer maxPlayers,
            String status) {

        Tournament tournament = new Tournament();
        tournament.name = name;
        tournament.teamSize = teamSize;
        tournament.startAt = startAt;
        tournament.maxPlayers = maxPlayers;
        tournament.status = status;

        return tournament;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getTeamSize() {
        return teamSize;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleteFlag() {
        return deleteFlag;
    }
}
