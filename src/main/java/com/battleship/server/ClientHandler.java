package com.battleship.server;

import com.battleship.common.*;
import com.battleship.server.DAO.GameDAO;
import com.battleship.server.DAO.UserDAO;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private User currentUser = null;
    private String authToken = null;
    private static final Map<String, String> activeTokens = new ConcurrentHashMap<>();

    private final UserDAO userDAO = new UserDAO();
    private final Map<String, Game> activeGames = new ConcurrentHashMap<>();
    private final Map<String, ComputerStrategy> computerStrategies = new ConcurrentHashMap<>();

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
            case RECONNECT_TOKEN -> handleReconnect(msg);
            case START_NEW_GAME -> handleStartNewGame();
            case PLACE_SHIPS -> handlePlaceShips(msg);
            case SHOT -> handleShot(msg);
            case LOBBY_ENTER -> handleLobbyEnter();
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

        send(new Message(MessageType.REGISTER_SUCCESS, "Выполните вход"));
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
        this.authToken = UUID.randomUUID().toString();
        activeTokens.put(this.authToken, login);

        send(new Message(MessageType.LOGIN_SUCCESS, login));
        send(new Message(MessageType.AUTH_TOKEN, this.authToken));
        System.out.println("Вошёл: " + login);
    }

    private void handleReconnect(Message msg) throws IOException {
        String token = (String) msg.getPayload();
        String login = activeTokens.get(token);

        if (login != null) {
            this.currentUser = userDAO.findByLogin(login);
            this.authToken = token;
            send(new Message(MessageType.LOGIN_SUCCESS, login));
            System.out.println("Переподключение по токену: " + login);
        } else {
            send(new Message(MessageType.LOGIN_FAIL, "Токен недействителен"));
            close();
        }
    }

    private void handleStartNewGame() throws IOException {
        Game game = new Game(currentUser.getLogin());
        game.difficulty = "Средний";
        activeGames.put(currentUser.getLogin(), game);

        send(new Message(MessageType.GAME_STATE, game));
        System.out.println("Новая игра создана для " + currentUser.getLogin());
    }

    private void handlePlaceShips(Message msg) throws IOException {
        Game game = (Game) msg.getPayload();
        activeGames.put(currentUser.getLogin(), game);

        game.state = GameState.PLAYER_TURN;
        send(new Message(MessageType.GAME_START, true));

        computerStrategies.put(currentUser.getLogin(), new ComputerStrategy());

        System.out.println(currentUser.getLogin() + " завершил расстановку | Сложность: " + game.difficulty);
    }

    private void handleShot(Message msg) throws IOException {
        int[] coord = (int[]) msg.getPayload();
        int row = coord[0];
        int col = coord[1];

        Game game = activeGames.get(currentUser.getLogin());
        if (game.computerHits[row][col]) {
            send(new Message(MessageType.ERROR, "Вы уже стреляли в эту клетку!"));
            return;
        }

        boolean hit = game.computerField[row][col];
        game.computerHits[row][col] = true;

        Ship sunkShip = null;
        if (hit) {
            game.computerField[row][col] = false;
            Ship ship = updateShipHits(game.computerShips, row, col);
            if (ship != null && ship.hits == ship.size) {
                sunkShip = ship;
            }
        }

        send(new Message(MessageType.SHOT_RESULT, new int[]{row, col, hit ? 1 : 0}));

        if (sunkShip != null) {
            Object[] payload = {sunkShip.cells, false}; // false — поле противника
            send(new Message(MessageType.SHIP_SUNK, payload));
        }

        if (allSunk(game.computerShips)) {
            send(new Message(MessageType.GAME_OVER, true));
            activeGames.remove(currentUser.getLogin());
            computerStrategies.remove(currentUser.getLogin());
            System.out.println(currentUser.getLogin() + " ПОБЕДИЛ!");
            return;
        }

        if (!hit) {
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    computerTurn(game);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void handleLobbyEnter() throws IOException {
        System.out.println(currentUser.getLogin() + " вернулся в лобби");
    }

    private void computerTurn(Game game) throws IOException {
        ComputerStrategy strategy = computerStrategies.get(currentUser.getLogin());
        int[] shot = getComputerShot(game, strategy);

        int row = shot[0];
        int col = shot[1];

        boolean hit = game.playerField[row][col];
        game.playerHits[row][col] = true;

        Ship sunkShip = null;
        if (hit) {
            game.playerField[row][col] = false;
            Ship ship = updateShipHits(game.playerShips, row, col);
            if (ship != null && ship.hits == ship.size) {
                sunkShip = ship;
            }
            strategy.recordHit(row, col);
        } else {
            strategy.recordMiss();
        }

        send(new Message(MessageType.OPPONENT_SHOT, new int[]{row, col, hit ? 1 : 0}));

        if (sunkShip != null) {
            Object[] payload = {sunkShip.cells, true}; // true — поле игрока
            send(new Message(MessageType.SHIP_SUNK, payload));

            ComputerStrategy compStrategy = computerStrategies.get(currentUser.getLogin());
            if (compStrategy != null) {
                for (int[] cell : sunkShip.cells) {
                    compStrategy.hitCells.remove(cell[0] + "," + cell[1]);
                }
            }
        }

        if (allSunk(game.playerShips)) {
            send(new Message(MessageType.GAME_OVER, false));
            activeGames.remove(currentUser.getLogin());
            computerStrategies.remove(currentUser.getLogin());
            return;
        }

        if (hit) {
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    computerTurn(game);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private int[] getComputerShot(Game game, ComputerStrategy strategy) {
        return switch (game.difficulty) {
            case "Лёгкий" -> strategy.getRandomShot(game);
            //case "Средний" -> ;
            //case "Сложный" -> ;
            default -> strategy.getRandomShot(game);
        };
    }

    private Ship updateShipHits(List<Ship> ships, int row, int col) {
        for (Ship ship : ships) {
            for (int[] cell : ship.cells) {
                if (cell[0] == row && cell[1] == col) {
                    ship.hits++;
                    return ship;
                }
            }
        }
        return null;
    }

    private boolean allSunk(List<Ship> ships) {
        return ships.stream().allMatch(s -> s.hits >= s.size);
    }

    private void send(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }

    private void close() {
        if (authToken != null) {
            activeTokens.remove(authToken);
        }
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}
    }

    private static class ComputerStrategy {
        private final Set<String> hitCells = new HashSet<>();
        private int[] lastHit = null;
        private int currentDirection = -1;

        void recordHit(int row, int col) {
            hitCells.add(row + "," + col);
            lastHit = new int[]{row, col};
            currentDirection = -1;
        }

        void recordMiss() {
            currentDirection = -1;
        }

        int[] getRandomShot(Game game) {
            Random rnd = new Random();
            int row, col;
            do {
                row = rnd.nextInt(10);
                col = rnd.nextInt(10);
            } while (game.playerHits[row][col]);
            return new int[]{row, col};
        }
    }
}