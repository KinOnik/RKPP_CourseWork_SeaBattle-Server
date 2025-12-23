package com.battleship.common;

public enum MessageType {
    REGISTER,
    LOGIN,
    AUTH_TOKEN,
    RECONNECT_TOKEN,
    REGISTER_FAIL,
    LOGIN_SUCCESS,
    LOGIN_FAIL,
    START_NEW_GAME,
    CONTINUE_GAME,
    GAME_LIST,
    SAVE_GAME,
    DELETE_SAVE,
    PLACE_SHIPS,
    SHOT,
    SHOT_RESULT,
    SHIP_SUNK,
    GAME_START,
    GAME_STATE,
    OPPONENT_SHOT,
    GAME_OVER,
    LOBBY_ENTER,
    ERROR
}