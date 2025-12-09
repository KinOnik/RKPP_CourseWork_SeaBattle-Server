package com.battleship.common;

import java.io.Serializable;
import java.util.*;

public class Game implements Serializable {
    public final String playerName;
    public GameState state = GameState.PLACING_SHIPS;

    public final boolean[][] playerField = new boolean[10][10];
    public final boolean[][] playerHits = new boolean[10][10];
    public final boolean[][] computerField = new boolean[10][10];
    public final boolean[][] computerHits = new boolean[10][10];

    public final List<Ship> playerShips = new ArrayList<>();
    public final List<Ship> computerShips = new ArrayList<>();

    public Game(String playerName) {
        this.playerName = playerName;
    }


}