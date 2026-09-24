package com.fighterhub.exception;

// 大会が存在しない場合の例外
public class TournamentNotFoundException extends ResourceNotFoundException {

    public TournamentNotFoundException(Long id) {
        super("Tournament not found. id=" + id);
    }
}
