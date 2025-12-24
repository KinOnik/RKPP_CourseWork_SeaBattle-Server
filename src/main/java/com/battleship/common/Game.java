package com.battleship.common;

import java.io.Serializable;
import java.util.*;

public class Game implements Serializable {
    public final String playerName;
    public List<int[]> computerHitCells = new ArrayList<>();
    public int[] computerLastHit = null;
    public int computerCurrentDirection = -1;
    public String difficulty = "Средний";
    public boolean isPlayerTurn = true;
    public boolean gameOver = false;
    public boolean playerWon = false;

    public static final int CELL_EMPTY = 0;
    public static final int CELL_SHIP = 1;
    public static final int CELL_MISS = 2;
    public static final int CELL_HIT = 3;
    public static final int CELL_SUNK = 4;

    public final int[][] playerField = new int[10][10];
    public final int[][] playerShots = new int[10][10];

    public final int[][] computerField = new int[10][10];
    public final int[][] computerShots = new int[10][10];

    public final List<Ship> playerShips = new ArrayList<>();
    public final List<Ship> computerShips = new ArrayList<>();


    public int playerSunkShips = 0;
    public int computerSunkShips = 0;

    public Game(String playerName) {
        this.playerName = playerName;

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                playerField[i][j] = CELL_EMPTY;
                playerShots[i][j] = CELL_EMPTY;
                computerField[i][j] = CELL_EMPTY;
                computerShots[i][j] = CELL_EMPTY;
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

    private boolean canPlaceShip(int[][] field, int x, int y, int size, boolean vertical) {
        for (int i = -1; i <= size; i++) {
            for (int j = -1; j <= (vertical ? 1 : size); j++) {
                int dx = vertical ? j : i;
                int dy = vertical ? i : j;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < 10 && ny >= 0 && ny < 10 && field[nx][ny] == CELL_SHIP) {
                    return false;
                }
            }
        }
        return true;
    }

    private void placeShip(int[][] field, List<Ship> ships, int x, int y, int size, boolean vertical) {
        Ship ship = new Ship(size);
        ship.isVertical = vertical;
        for (int i = 0; i < size; i++) {
            int dx = vertical ? 0 : i;
            int dy = vertical ? i : 0;
            field[x + dx][y + dy] = CELL_SHIP;
            ship.cells.add(new int[]{x + dx, y + dy});
        }
        ships.add(ship);
    }

    public void copyPlayerShipsFrom(Game sourceGame) {
        this.playerShips.clear();
        for (Ship ship : sourceGame.playerShips) {
            Ship newShip = new Ship(ship.size);
            newShip.isVertical = ship.isVertical;
            newShip.cells.addAll(ship.cells);
            this.playerShips.add(newShip);
        }

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                this.playerField[i][j] = sourceGame.playerField[i][j];
            }
        }

        this.difficulty = sourceGame.difficulty;
    }

    public boolean isShipSunk(List<Ship> ships, int[][] shots, int x, int y) {
        for (Ship ship : ships) {
            for (int[] cell : ship.cells) {
                if (cell[0] == x && cell[1] == y) {
                    for (int[] c : ship.cells) {
                        if (shots[c[0]][c[1]] != CELL_HIT && shots[c[0]][c[1]] != CELL_SUNK) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void markSunkShip(List<Ship> ships, int[][] field, int[][] shots, int x, int y) {
        for (Ship ship : ships) {
            for (int[] cell : ship.cells) {
                if (cell[0] == x && cell[1] == y) {
                    for (int[] c : ship.cells) {
                        field[c[0]][c[1]] = CELL_SUNK;
                        shots[c[0]][c[1]] = CELL_SUNK;
                    }
                    return;
                }
            }
        }
    }
}