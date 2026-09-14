package com.fighterhub.exception;

public class CharacterNotFoundException extends RuntimeException {

    public CharacterNotFoundException(Long id) {
        super("Character not found. id=" + id);
    }
}