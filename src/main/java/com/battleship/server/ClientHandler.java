package com.battleship.server;

import com.battleship.common.*;
import com.battleship.server.DAO.GameDAO;
import com.battleship.server.DAO.UserDAO;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private User currentUser = null;
    private final UserDAO userDAO = new UserDAO();
    private final Map<String, Game> activeGames = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Message msg = (Message) in.readObject();
                handleMessage(msg);
            }
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
            case START_NEW_GAME -> handleStartNewGame();
            case PLACE_SHIPS -> handlePlaceShips(msg);
            case SHOT -> handleShot(msg);
            default -> {
                if (currentUser == null) {
                    send(new Message(MessageType.ERROR, "Сначала авторизуйся"));
                }
            }
        }
    }

    private void handleRegister(Message msg) throws IOException {
        String[] data = (String[]) msg.getPayload();
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
        System.out.println("Зарегистрирован: " + login);
    }

    private void handleLogin(Message msg) throws IOException {
        String[] data = (String[]) msg.getPayload();
        String login = data[0];
        String password = data[1];

        User user = userDAO.findByLogin(login);
        if (user == null || !PasswordUtil.hashPassword(password, user.getSalt()).equals(user.getPasswordHash())) {
            send(new Message(MessageType.LOGIN_FAIL, "Неверный логин или пароль"));
            return;
        }

        this.currentUser = user;
        send(new Message(MessageType.LOGIN_SUCCESS, login));
        System.out.println("Вошёл: " + login);
    }

    private void handleStartNewGame() throws IOException {
        Game game = new Game(currentUser.getLogin());
        activeGames.put(currentUser.getLogin(), game);

        send(new Message(MessageType.GAME_STATE, game));
        System.out.println("Новая игра создана для " + currentUser.getLogin());
    }

    private void handlePlaceShips(Message msg) throws IOException {
        Game game = (Game) msg.getPayload();
        activeGames.put(currentUser.getLogin(), game);

        game.state = GameState.PLAYER_TURN;
        send(new Message(MessageType.GAME_START, true)); // начало боя

        System.out.println(currentUser.getLogin() + " завершил расстановку. Бой начат!");
    }

    private void handleShot(Message msg) throws IOException {
        int[] coord = (int[]) msg.getPayload();
        int row = coord[0];
        int col = coord[1];

        Game game = activeGames.get(currentUser.getLogin());

        boolean hit = game.computerField[row][col];
        game.computerField[row][col] = false; // помечаем как выстрелено

        // Отправляем результат игроку
        send(new Message(MessageType.SHOT_RESULT, new int[]{row, col, hit ? 1 : 0}));

        // Проверка победы игрока
        if (allSunk(game.computerShips)) {
            send(new Message(MessageType.GAME_OVER, true));
            activeGames.remove(currentUser.getLogin());
            System.out.println(currentUser.getLogin() + " ПОБЕДИЛ!");
            return;
        }

        // Ход компьютера
        int[] aiShot = computerTurn(game);
        int aiRow = aiShot[0];
        int aiCol = aiShot[1];
        boolean aiHit = game.playerField[aiRow][aiCol];
        game.playerField[aiRow][aiCol] = false;

        send(new Message(MessageType.OPPONENT_SHOT, new int[]{aiRow, aiCol, aiHit ? 1 : 0}));

        if (allSunk(game.playerShips)) {
            send(new Message(MessageType.GAME_OVER, false));
            activeGames.remove(currentUser.getLogin());
            System.out.println(currentUser.getLogin() + " проиграл...");
        }
    }

    private int[] computerTurn(Game game) {
        Random rnd = new Random();
        int row, col;
        do {
            row = rnd.nextInt(10);
            col = rnd.nextInt(10);
        } while (game.computerHits[row][col]); // не стрелять по уже выстреленным
        return new int[]{row, col};
    }

    private boolean allSunk(List<Ship> ships) {
        for (Ship ship : ships) {
            if (!ship.isSunk()) return false;
        }
        return true;
    }

    private void send(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }

    private void close() {
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}
    }
}