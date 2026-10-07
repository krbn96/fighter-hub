package com.fighterhub.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class RankTest {

    @Test
    void isValid_定義済みの8値はtrueを返す() {
        assertTrue(Rank.isValid("ROOKIE"));
        assertTrue(Rank.isValid("IRON"));
        assertTrue(Rank.isValid("BRONZE"));
        assertTrue(Rank.isValid("SILVER"));
        assertTrue(Rank.isValid("GOLD"));
        assertTrue(Rank.isValid("PLATINUM"));
        assertTrue(Rank.isValid("DIAMOND"));
        assertTrue(Rank.isValid("MASTER"));
    }

    @Test
    void isValid_未定義の値やnullはfalseを返す() {
        assertFalse(Rank.isValid("GRANDMASTER"));
        assertFalse(Rank.isValid(""));
        assertFalse(Rank.isValid(null));
    }

    @Test
    void fromValue_定義済みの値はRankを返す() {
        assertEquals(Optional.of(Rank.DIAMOND), Rank.fromValue("DIAMOND"));
    }

    @Test
    void fromValue_未定義の値は空を返す() {
        assertEquals(Optional.empty(), Rank.fromValue("GRANDMASTER"));
    }

    @Test
    void satisfies_自身より低いまたは同じ要求ランクは満たす() {
        assertTrue(Rank.DIAMOND.satisfies(Rank.IRON));
        assertTrue(Rank.DIAMOND.satisfies(Rank.DIAMOND));
    }

    @Test
    void satisfies_自身より高い要求ランクは満たさない() {
        assertFalse(Rank.DIAMOND.satisfies(Rank.MASTER));
    }
}
