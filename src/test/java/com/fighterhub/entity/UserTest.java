package com.fighterhub.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fighterhub.entity.User.CharacterAssignment;

class UserTest {

    private static User newUserWithFourCharacters() {
        Character c1 = new Character(1L, "Ryu");
        Character c2 = new Character(2L, "Ken");
        Character c3 = new Character(3L, "Chun-Li");
        Character c4 = new Character(4L, "Guile");

        List<CharacterAssignment> assignments = List.of(
                new CharacterAssignment(c1, "MASTER", 1600),
                new CharacterAssignment(c2, "DIAMOND", 1400),
                new CharacterAssignment(c3, "PLATINUM", 1200),
                new CharacterAssignment(c4, "GOLD", 1000)
        );

        return User.create(
                "Test User",
                "user@example.com",
                "hashed-password",
                assignments,
                LocalTime.of(20, 0),
                LocalTime.of(23, 30),
                "hello"
        );
    }

    @Test
    void updateName_nameを更新できる() {
        User user = newUserWithFourCharacters();

        user.updateName("New Name");

        assertEquals("New Name", user.getName());
    }

    @Test
    void updateCharacters_4件から1件へ全置換した場合_2枠目以降がすべてnullになる() {
        User user = newUserWithFourCharacters();

        Character newCharacter = new Character(5L, "Cammy");
        user.updateCharacters(List.of(new CharacterAssignment(newCharacter, "MASTER", 1900)));

        assertEquals(newCharacter, user.getCharacter1());
        assertEquals("MASTER", user.getRank1());
        assertEquals(1900, user.getMr1());

        assertNull(user.getCharacter2());
        assertNull(user.getRank2());
        assertNull(user.getMr2());

        assertNull(user.getCharacter3());
        assertNull(user.getRank3());
        assertNull(user.getMr3());

        assertNull(user.getCharacter4());
        assertNull(user.getRank4());
        assertNull(user.getMr4());
    }

    @Test
    void updateCharacters_1件から4件へ全置換できる() {
        Character c1 = new Character(1L, "Ryu");
        User user = User.create(
                "Test User",
                "user@example.com",
                "hashed-password",
                List.of(new CharacterAssignment(c1, "MASTER", 1600)),
                LocalTime.of(20, 0),
                LocalTime.of(23, 30),
                "hello"
        );

        Character c2 = new Character(2L, "Ken");
        Character c3 = new Character(3L, "Chun-Li");
        Character c4 = new Character(4L, "Guile");

        user.updateCharacters(List.of(
                new CharacterAssignment(c1, "MASTER", 1600),
                new CharacterAssignment(c2, "DIAMOND", 1400),
                new CharacterAssignment(c3, "PLATINUM", 1200),
                new CharacterAssignment(c4, "GOLD", 1000)
        ));

        assertEquals(c1, user.getCharacter1());
        assertEquals(c2, user.getCharacter2());
        assertEquals(c3, user.getCharacter3());
        assertEquals(c4, user.getCharacter4());
        assertEquals("DIAMOND", user.getRank2());
        assertEquals(1400, user.getMr2());
        assertEquals("GOLD", user.getRank4());
        assertEquals(1000, user.getMr4());
    }

    @Test
    void updateCharacters_0件の場合_例外を投げる() {
        User user = newUserWithFourCharacters();

        assertThrows(IllegalArgumentException.class, () -> user.updateCharacters(List.of()));
    }

    @Test
    void updateCharacters_5件の場合_例外を投げる() {
        User user = newUserWithFourCharacters();

        List<CharacterAssignment> fiveItems = List.of(
                new CharacterAssignment(new Character(1L, "Ryu"), "MASTER", 1600),
                new CharacterAssignment(new Character(2L, "Ken"), "MASTER", 1600),
                new CharacterAssignment(new Character(3L, "Chun-Li"), "MASTER", 1600),
                new CharacterAssignment(new Character(4L, "Guile"), "MASTER", 1600),
                new CharacterAssignment(new Character(5L, "Cammy"), "MASTER", 1600)
        );

        assertThrows(IllegalArgumentException.class, () -> user.updateCharacters(fiveItems));
    }

    @Test
    void updatePlayTimeStart_値からnullへ更新できる() {
        User user = newUserWithFourCharacters();

        user.updatePlayTimeStart(null);

        assertNull(user.getPlayTimeStart());
    }

    @Test
    void updatePlayTimeStart_値から別の値へ更新できる() {
        User user = newUserWithFourCharacters();

        user.updatePlayTimeStart(LocalTime.of(9, 0));

        assertEquals(LocalTime.of(9, 0), user.getPlayTimeStart());
    }

    @Test
    void updatePlayTimeEnd_値からnullへ更新できる() {
        User user = newUserWithFourCharacters();

        user.updatePlayTimeEnd(null);

        assertNull(user.getPlayTimeEnd());
    }

    @Test
    void updateMessage_値からnullへ更新できる() {
        User user = newUserWithFourCharacters();

        user.updateMessage(null);

        assertNull(user.getMessage());
    }

    @Test
    void updateXId_値からnullへ更新できる() {
        User user = newUserWithFourCharacters();
        user.updateXId("oldxid");

        user.updateXId(null);

        assertNull(user.getXId());
    }

    @Test
    void updateDiscordId_値からnullへ更新できる() {
        User user = newUserWithFourCharacters();
        user.updateDiscordId("olddiscord#1234");

        user.updateDiscordId(null);

        assertNull(user.getDiscordId());
    }

    @Test
    void updateDiscordId_値から別の値へ更新できる() {
        User user = newUserWithFourCharacters();
        user.updateDiscordId("old#1234");

        user.updateDiscordId("new#5678");

        assertEquals("new#5678", user.getDiscordId());
    }
}
