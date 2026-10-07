package com.fighterhub.entity;

import java.util.Optional;

// FIGHTER HUBの確定ランク仕様(8種類、ROOKIE(最低)→MASTER(最高))。
// frontend/src/constants/ranks.tsのRANK_OPTIONSと同じ8値・同じ順序。
// rankはDB/API上は自由文字列として扱われている(T_USERS.rank_1〜4、Team.rankRequirement)ため、
// このenumへの変換はService層で順序比較や許容値検証が必要な場所でのみ行う唯一の定義とする。
public enum Rank {
    ROOKIE, IRON, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, MASTER;

    public static boolean isValid(String value) {
        return fromValue(value).isPresent();
    }

    public static Optional<Rank> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (Rank rank : values()) {
            if (rank.name().equals(value)) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }

    // thisランク(応募しようとするプレイヤーのランク)が、requirement(チームのrankRequirement)を
    // 満たせるかどうか。順序はROOKIE(最低)→MASTER(最高)で、thisのordinalがrequirement以上であれば
    // 満たす(例: DIAMONDのプレイヤーはIRON/GOLD/PLATINUM/DIAMOND要求のチームを満たせるが、
    // MASTER要求のチームは満たせない)。
    public boolean satisfies(Rank requirement) {
        return this.ordinal() >= requirement.ordinal();
    }
}
