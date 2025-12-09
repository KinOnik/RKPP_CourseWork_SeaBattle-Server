package com.battleship.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Ship implements Serializable {
    public final int size;
    public final List<int[]> cells = new ArrayList<>();
    public boolean isVertical;
    public int hits = 0;

    public Ship(int size) {
        this.size = size;
    }

    public boolean isSunk() {
        return hits >= size;
    }
}