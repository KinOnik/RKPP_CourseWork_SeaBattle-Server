package com.battleship.common;

import java.io.Serializable;
import java.util.*;

public class Game implements Serializable {
    public final String playerName;
    public GameState state = GameState.PLACING_SHIPS;
    public String difficulty = "Средний";

    public final boolean[][] playerField = new boolean[10][10];
    public final boolean[][] playerHits = new boolean[10][10];
    public final boolean[][] computerField = new boolean[10][10];
    public final boolean[][] computerHits = new boolean[10][10];

    public final List<Ship> playerShips = new ArrayList<>();
    public final List<Ship> computerShips = new ArrayList<>();

    public Game(String playerName) {
        this.playerName = playerName;

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                playerHits[i][j] = false;
                computerHits[i][j] = false;
            }
        }

        placeComputerShipsRandomly();
    }

    private void placeComputerShipsRandomly() {
        Random r = new Random();
        int[] sizes = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
        for (int size : sizes) {
            while (true) {
                boolean vertical = r.nextBoolean();
                int x = r.nextInt(vertical ? 10 : 11 - size);
                int y = r.nextInt(vertical ? 11 - size : 10);

                if (canPlaceShip(computerField, x, y, size, vertical)) {
                    placeShip(computerField, computerShips, x, y, size, vertical);
                    break;
                }
            }
        }
    }

    private boolean canPlaceShip(boolean[][] field, int x, int y, int size, boolean vertical) {
        for (int i = -1; i <= size; i++) {
            for (int j = -1; j <= (vertical ? 1 : size); j++) {
                int dx = vertical ? j : i;
                int dy = vertical ? i : j;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < 10 && ny >= 0 && ny < 10 && field[nx][ny]) return false;
            }
        }
        return true;
    }

    private void placeShip(boolean[][] field, List<Ship> ships, int x, int y, int size, boolean vertical) {
        Ship ship = new Ship(size);
        ship.isVertical = vertical;
        for (int i = 0; i < size; i++) {
            int dx = vertical ? 0 : i;
            int dy = vertical ? i : 0;
            field[x + dx][y + dy] = true;
            ship.cells.add(new int[]{x + dx, y + dy});
        }
        ships.add(ship);
    }

}