package com.battleship.common;

import java.io.Serializable;

public class User implements Serializable {
    private final String login;
    private final String passwordHash;
    private final String salt;

    public User(String login, String passwordHash, String salt) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public String getLogin() { return login; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
}