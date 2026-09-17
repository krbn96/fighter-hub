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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "t_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_1")
    private Character character1;

    @Column(name = "rank_1", length = 30)
    private String rank1;

    @Column(name = "mr_1")
    private Integer mr1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_2")
    private Character character2;

    @Column(name = "rank_2", length = 30)
    private String rank2;

    @Column(name = "mr_2")
    private Integer mr2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_3")
    private Character character3;

    @Column(name = "rank_3", length = 30)
    private String rank3;

    @Column(name = "mr_3")
    private Integer mr3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_4")
    private Character character4;

    @Column(name = "rank_4", length = 30)
    private String rank4;

    @Column(name = "mr_4")
    private Integer mr4;

    @Column(name = "play_time_start")
    private LocalTime playTimeStart;

    @Column(name = "play_time_end")
    private LocalTime playTimeEnd;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Column(name = "x_id", length = 255)
    private String xId;

    @Column(name = "discord_id", length = 255)
    private String discordId;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "delete_flag", nullable = false)
    private boolean deleteFlag = false;

    protected User() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Character getCharacter1() {
        return character1;
    }

    public String getRank1() {
        return rank1;
    }

    public Integer getMr1() {
        return mr1;
    }

    public Character getCharacter2() {
        return character2;
    }

    public String getRank2() {
        return rank2;
    }

    public Integer getMr2() {
        return mr2;
    }

    public Character getCharacter3() {
        return character3;
    }

    public String getRank3() {
        return rank3;
    }

    public Integer getMr3() {
        return mr3;
    }

    public Character getCharacter4() {
        return character4;
    }

    public String getRank4() {
        return rank4;
    }

    public Integer getMr4() {
        return mr4;
    }

    public LocalTime getPlayTimeStart() {
        return playTimeStart;
    }

    public LocalTime getPlayTimeEnd() {
        return playTimeEnd;
    }

    public String getMessage() {
        return message;
    }

    public String getEmail() {
        return email;
    }

    public String getXId() {
        return xId;
    }

    public String getDiscordId() {
        return discordId;
    }

    public String getPasswordHash() {
        return passwordHash;
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
