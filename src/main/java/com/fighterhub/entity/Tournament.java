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

    // FIGHTER HUB上でのチーム募集・参加申請の締切日時。
    // 募集状態(RECRUITING/CLOSED)はDBへ保存せず、isRecruitmentOpen(now)でその都度導出する。
    @Column(name = "recruitment_deadline", nullable = false)
    private LocalDateTime recruitmentDeadline;

    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;

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
            LocalDateTime recruitmentDeadline,
            Integer maxPlayers) {

        Tournament tournament = new Tournament();
        tournament.name = name;
        tournament.teamSize = teamSize;
        tournament.startAt = startAt;
        tournament.recruitmentDeadline = recruitmentDeadline;
        tournament.maxPlayers = maxPlayers;

        return tournament;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateTeamSize(Integer teamSize) {
        this.teamSize = teamSize;
    }

    public void updateStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public void updateRecruitmentDeadline(LocalDateTime recruitmentDeadline) {
        this.recruitmentDeadline = recruitmentDeadline;
    }

    public void updateMaxPlayers(Integer maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    // 物理DELETEは行わず、deleteFlagをtrueにする論理削除。
    public void deactivate() {
        this.deleteFlag = true;
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

    public LocalDateTime getRecruitmentDeadline() {
        return recruitmentDeadline;
    }

    // 募集状態(RECRUITING/CLOSED)はDBへ保存せず、呼び出し側(Service)がClockから取得したnowを
    // 渡して都度判定する。Entity自体はSpring/Clockに依存しない(nowを引数で受け取るのみ)。
    // 締切ちょうど(now == recruitmentDeadline)はfalse(CLOSED)。
    public boolean isRecruitmentOpen(LocalDateTime now) {
        return now.isBefore(recruitmentDeadline);
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
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
