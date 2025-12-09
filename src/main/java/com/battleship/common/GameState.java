package com.battleship.common;

import java.io.Serializable;

public enum GameState implements Serializable {
    PLACING_SHIPS,
    PLAYER_TURN,
    COMPUTER_TURN,
    PLAYER_WON,
    COMPUTER_WON
}