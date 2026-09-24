package com.fighterhub.exception;

// チームが存在しない場合の例外
public class TeamNotFoundException extends ResourceNotFoundException {

    public TeamNotFoundException(Long id) {
        super("Team not found. id=" + id);
    }
}
