package com.fighterhub.exception;

// キャラクターが存在しない場合の例外
public class CharacterNotFoundException extends ResourceNotFoundException {

    public CharacterNotFoundException(Long id) {
        super("Character not found. id=" + id);
    }
}