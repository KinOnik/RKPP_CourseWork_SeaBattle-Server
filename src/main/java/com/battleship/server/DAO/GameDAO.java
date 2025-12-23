package com.battleship.server.DAO;

import com.battleship.common.Game;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GameDAO {
    private static final String SAVES_DIR = "saves";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private final Lock lock = new ReentrantLock();  // Для синхронизации файловых операций

    public GameDAO() {
        new File(SAVES_DIR).mkdirs();
    }

    public String saveGame(String username, Game game, boolean isAuto) {
        lock.lock();
        try {
            String userDir = SAVES_DIR + "/" + username;
            new File(userDir).mkdirs();

            LocalDateTime now = LocalDateTime.now();
            String timestamp = now.format(FORMATTER);
            String prefix = isAuto ? "autosave_" : "save_";
            String filename = prefix + timestamp + ".dat";
            String fullPath = userDir + "/" + filename;

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fullPath))) {
                oos.writeObject(game);
            }
            return filename;
        } catch (IOException e) {
            System.err.println("Ошибка сохранения игры для " + username + ": " + e.getMessage());
            return null;
        } finally {
            lock.unlock();
        }
    }

    public void autosaveLatest(String username, Game game) {
        lock.lock();
        try {
            String userDir = SAVES_DIR + "/" + username;
            new File(userDir).mkdirs();
            String fullPath = userDir + "/autosave_latest.dat";

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fullPath))) {
                oos.writeObject(game);
            }
        } catch (IOException e) {
            System.err.println("Ошибка автосохранения для " + username + ": " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public Game loadGame(String username, String filename) {
        lock.lock();
        try {
            String fullPath = SAVES_DIR + "/" + username + "/" + filename;
            File file = new File(fullPath);
            if (!file.exists()) return null;

            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                return (Game) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Ошибка загрузки игры для " + username + ": " + e.getMessage());
            return null;
        } finally {
            lock.unlock();
        }
    }

    public List<Object[]> getGameList(String username) {
        lock.lock();
        try {
            Path userDir = Paths.get(SAVES_DIR + "/" + username);
            if (!Files.exists(userDir)) return new ArrayList<>();

            List<Object[]> list = new ArrayList<>();
            Files.list(userDir).forEach(path -> {
                String name = path.getFileName().toString();
                if (name.endsWith(".dat")) {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(name.substring(name.indexOf("_") + 1, name.lastIndexOf(".")), FORMATTER);
                        long size = Files.size(path) / 1024;  // В KB
                        list.add(new Object[]{name, dt.toLocalDate().toString(), dt.toLocalTime().toString(), size});
                    } catch (Exception ignored) {}
                }
            });
            return list;
        } catch (IOException e) {
            System.err.println("Ошибка списка игр для " + username + ": " + e.getMessage());
            return new ArrayList<>();
        } finally {
            lock.unlock();
        }
    }

    public boolean deleteGame(String username, String filename) {
        lock.lock();
        try {
            Path path = Paths.get(SAVES_DIR + "/" + username + "/" + filename);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            System.err.println("Ошибка удаления игры для " + username + ": " + e.getMessage());
            return false;
        } finally {
            lock.unlock();
        }
    }
}