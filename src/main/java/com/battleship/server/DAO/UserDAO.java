package com.battleship.server.DAO;

import com.battleship.common.User;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserDAO {
    private static final String FILE = "users.dat";
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    public UserDAO() {
        load();
    }

    public void register(User user) {
        users.put(user.getLogin(), user);
        save();
    }

    public User findByLogin(String login) {
        return users.get(login);
    }

    private void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE))) {
            oos.writeObject(users);
        } catch (IOException e) {
            System.err.println("Не удалось сохранить пользователей: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        File f = new File(FILE);
        if (!f.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            Object obj = ois.readObject();
            if (obj instanceof ConcurrentHashMap) {
                users.putAll((ConcurrentHashMap<String, User>) obj);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Не удалось загрузить пользователей");
        }
    }
}