package com.fighterhub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "t_teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "rank_requirement", length = 30)
    private String rankRequirement;

    // Character IDのリストをJSONB配列としてそのまま保存する。
    // 各IDがM_CHARACTERSに実在するかのチェックはこのEntityでは行わず、Service層で行う。
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "character_requirements", columnDefinition = "jsonb")
    private List<Long> characterRequirements;

    @Column(name = "recruitment_message", columnDefinition = "TEXT")
    private String recruitmentMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "delete_flag", nullable = false)
    private boolean deleteFlag = false;

    protected Team() {
    }

    public static Team create(
            Tournament tournament,
            User owner,
            String name,
            String rankRequirement,
            List<Long> characterRequirements,
            String recruitmentMessage) {

        Team team = new Team();
        team.tournament = tournament;
        team.owner = owner;
        team.name = name;
        team.rankRequirement = rankRequirement;
        team.characterRequirements = characterRequirements;
        team.recruitmentMessage = recruitmentMessage;

        return team;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateRankRequirement(String rankRequirement) {
        this.rankRequirement = rankRequirement;
    }

    public void updateCharacterRequirements(List<Long> characterRequirements) {
        this.characterRequirements = characterRequirements;
    }

    public void updateRecruitmentMessage(String recruitmentMessage) {
        this.recruitmentMessage = recruitmentMessage;
    }

    public Long getId() {
        return id;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getRankRequirement() {
        return rankRequirement;
    }

    public List<Long> getCharacterRequirements() {
        return characterRequirements;
    }

    public String getRecruitmentMessage() {
        return recruitmentMessage;
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
