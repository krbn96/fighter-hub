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
import java.util.List;

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

    public record CharacterAssignment(
            Character character,
            String rank,
            Integer mr) {
    }

    protected User() {
    }

    public static User create(
            String name,
            String email,
            String passwordHash,
            List<CharacterAssignment> characters,
            LocalTime playTimeStart,
            LocalTime playTimeEnd,
            String message) {

        User user = new User();
        user.name = name;
        user.email = email;
        user.passwordHash = passwordHash;
        user.playTimeStart = playTimeStart;
        user.playTimeEnd = playTimeEnd;
        user.message = message;
        user.updateCharacters(characters);

        return user;
    }

    public void updateName(String name) {
        this.name = name;
    }

    // charactersを1〜4件で全置換する。使用されない枠はcharacter/rank/mrすべてをnullにする。
    public void updateCharacters(List<CharacterAssignment> characters) {
        if (characters == null || characters.isEmpty() || characters.size() > 4) {
            throw new IllegalArgumentException("characters must contain between 1 and 4 elements.");
        }

        CharacterAssignment assignment1 = characters.size() >= 1 ? characters.get(0) : null;
        this.character1 = assignment1 != null ? assignment1.character() : null;
        this.rank1 = assignment1 != null ? assignment1.rank() : null;
        this.mr1 = assignment1 != null ? assignment1.mr() : null;

        CharacterAssignment assignment2 = characters.size() >= 2 ? characters.get(1) : null;
        this.character2 = assignment2 != null ? assignment2.character() : null;
        this.rank2 = assignment2 != null ? assignment2.rank() : null;
        this.mr2 = assignment2 != null ? assignment2.mr() : null;

        CharacterAssignment assignment3 = characters.size() >= 3 ? characters.get(2) : null;
        this.character3 = assignment3 != null ? assignment3.character() : null;
        this.rank3 = assignment3 != null ? assignment3.rank() : null;
        this.mr3 = assignment3 != null ? assignment3.mr() : null;

        CharacterAssignment assignment4 = characters.size() >= 4 ? characters.get(3) : null;
        this.character4 = assignment4 != null ? assignment4.character() : null;
        this.rank4 = assignment4 != null ? assignment4.rank() : null;
        this.mr4 = assignment4 != null ? assignment4.mr() : null;
    }

    public void updatePlayTimeStart(LocalTime playTimeStart) {
        this.playTimeStart = playTimeStart;
    }

    public void updatePlayTimeEnd(LocalTime playTimeEnd) {
        this.playTimeEnd = playTimeEnd;
    }

    public void updateMessage(String message) {
        this.message = message;
    }

    public void updateXId(String xId) {
        this.xId = xId;
    }

    public void updateDiscordId(String discordId) {
        this.discordId = discordId;
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
