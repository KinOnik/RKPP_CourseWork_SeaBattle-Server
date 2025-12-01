package com.battleship.server;

import com.battleship.common.*;
import com.battleship.server.DAO.UserDAO;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private User currentUser = null;
    private final UserDAO userDAO = new UserDAO();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Message msg = (Message) in.readObject();
                handleMessage(msg);
            }
        } catch (EOFException | SocketException e) {
            System.out.println("Клиент " + (currentUser != null ? currentUser.getLogin() : "") + " отключился");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void handleMessage(Message msg) throws IOException {
        switch (msg.getType()) {
            case REGISTER -> handleRegister(msg);
            case LOGIN -> handleLogin(msg);
            default -> send(new Message(MessageType.ERROR, "Сначала авторизуйся"));
        }
    }

    private void handleRegister(Message msg) throws IOException {
        String[] data = (String[]) msg.getPayload(); // [login, password]
        String login = data[0];
        String password = data[1];

        if (userDAO.findByLogin(login) != null) {
            send(new Message(MessageType.REGISTER_FAIL, "Логин уже занят"));
            return;
        }

        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(password, salt);
        User user = new User(login, hash, salt);
        userDAO.register(user);

        send(new Message(MessageType.REGISTER_SUCCESS));
        System.out.println("Зареган новый пользователь: " + login);
    }

    private void handleLogin(Message msg) throws IOException {
        String[] data = (String[]) msg.getPayload(); // [login, password]
        String login = data[0];
        String password = data[1];

        User user = userDAO.findByLogin(login);
        if (user == null) {
            send(new Message(MessageType.LOGIN_FAIL, "Неверный логин или пароль"));
            return;
        }

        String expectedHash = user.getPasswordHash();
        String actualHash = PasswordUtil.hashPassword(password, user.getSalt());

        if (expectedHash.equals(actualHash)) {
            this.currentUser = user;
            send(new Message(MessageType.LOGIN_SUCCESS, login));
            System.out.println("Успешный вход: " + login);
        } else {
            send(new Message(MessageType.LOGIN_FAIL, "Неверный логин или пароль"));
        }
    }

    private void send(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }

    private void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}