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
    private final GameDAO gameDAO = new GameDAO();
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
            case SAVE_GAME -> handleSaveGame();
            case GAME_LIST -> handleGameList();
            case CONTINUE_GAME -> handleContinueGame(msg);
            case DELETE_SAVE -> handleDeleteSave(msg);
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

        this.currentUser = user;
        this.authToken = UUID.randomUUID().toString();
        activeTokens.put(this.authToken, login);

        send(new Message(MessageType.LOGIN_SUCCESS, login));
        send(new Message(MessageType.AUTH_TOKEN, this.authToken));

        System.out.println("Зарегистрирован и автоматически вошёл: " + login);
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
        gameDAO.deleteGame(currentUser.getLogin(), "autosave_latest.dat");
        activeGames.remove(currentUser.getLogin());
        computerStrategies.remove(currentUser.getLogin());

        Game game = new Game(currentUser.getLogin());
        game.difficulty = "Средний";
        activeGames.put(currentUser.getLogin(), game);

        send(new Message(MessageType.GAME_STATE, game));
        System.out.println("Новая игра создана для " + currentUser.getLogin());
    }

    private void handlePlaceShips(Message msg) throws IOException {
        Game receivedGame = (Game) msg.getPayload();
        String login = currentUser.getLogin();

        Game activeGame = activeGames.get(login);
        if (activeGame == null) {
            send(new Message(MessageType.ERROR, "Игра не найдена"));
            return;
        }

        activeGame.copyPlayerShipsFrom(receivedGame);
        activeGame.difficulty = receivedGame.difficulty;

        computerStrategies.put(login, new ComputerStrategy());

        send(new Message(MessageType.GAME_START, activeGame));
        System.out.println(login + " завершил расстановку | Сложность: " + activeGame.difficulty);
    }

    private void handleShot(Message msg) throws IOException {
        int[] coord = (int[]) msg.getPayload();
        int row = coord[0];
        int col = coord[1];

        Game game = activeGames.get(currentUser.getLogin());
        if (game == null) {
            send(new Message(MessageType.ERROR, "Игра не найдена"));
            return;
        }

        if (!game.isPlayerTurn) {
            send(new Message(MessageType.ERROR, "Сейчас не ваш ход"));
            return;
        }

        if (game.playerShots[row][col] != Game.CELL_EMPTY) {
            send(new Message(MessageType.ERROR, "Вы уже стреляли в эту клетку!"));
            return;
        }

        boolean hit = game.computerField[row][col] == Game.CELL_SHIP;

        if (hit) {
            game.playerShots[row][col] = Game.CELL_HIT;
            game.computerField[row][col] = Game.CELL_HIT;

            if (game.isShipSunk(game.computerShips, game.playerShots, row, col)) {
                game.markSunkShip(game.computerShips, game.computerField, game.playerShots, row, col);
                game.computerSunkShips++;

                Ship sunkShip = findSunkShip(game.computerShips, row, col);
                if (sunkShip != null) {
                    Object[] payload = {sunkShip.cells, false}; // false — поле противника
                    send(new Message(MessageType.SHIP_SUNK, payload));
                }
            }

            if (game.computerSunkShips == game.computerShips.size()) {
                game.gameOver = true;
                game.playerWon = true;
                send(new Message(MessageType.GAME_OVER, true));
                gameDAO.deleteGame(currentUser.getLogin(), "autosave_latest.dat");
                activeGames.remove(currentUser.getLogin());
                computerStrategies.remove(currentUser.getLogin());
                System.out.println(currentUser.getLogin() + " ПОБЕДИЛ!");
                return;
            }
        } else {
            game.playerShots[row][col] = Game.CELL_MISS;
            game.computerField[row][col] = Game.CELL_MISS;
            game.isPlayerTurn = false;
        }

        send(new Message(MessageType.SHOT_RESULT, new int[]{row, col, hit ? 1 : 0}));

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

        gameDAO.autosaveLatest(currentUser.getLogin(), game);
    }

    private void handleLobbyEnter() throws IOException {
        System.out.println(currentUser.getLogin() + " вернулся в лобби");
    }

    private void handleSaveGame() throws IOException {
        Game game = activeGames.get(currentUser.getLogin());
        if (game != null) {
            saveStrategyToGame(game);

            String filename = gameDAO.saveGame(currentUser.getLogin(), game, false);
            if (filename != null) {
                send(new Message(MessageType.GAME_STATE, "Игра сохранена: " + filename));
            } else {
                send(new Message(MessageType.ERROR, "Ошибка сохранения"));
            }
        } else {
            send(new Message(MessageType.ERROR, "Нет активной игры"));
        }
    }

    private void handleGameList() throws IOException {
        List<Object[]> list = gameDAO.getGameList(currentUser.getLogin());
        Object[][] array = list.toArray(new Object[0][]);
        send(new Message(MessageType.GAME_LIST, array));
        System.out.println("Отправлен список сохранений для " + currentUser.getLogin() + ": " + list.size() + " файлов");
    }

    private void handleContinueGame(Message msg) throws IOException {
        String filename = (String) msg.getPayload();
        Game game = gameDAO.loadGame(currentUser.getLogin(), filename);
        if (game != null) {
            activeGames.put(currentUser.getLogin(), game);

            ComputerStrategy strategy = new ComputerStrategy();
            restoreStrategyFromGame(game, strategy);
            computerStrategies.put(currentUser.getLogin(), strategy);

            send(new Message(MessageType.GAME_STATE, game));
            System.out.println(currentUser.getLogin() + " продолжил игру из " + filename + " | Сложность: " + game.difficulty);
        } else {
            send(new Message(MessageType.ERROR, "Ошибка загрузки"));
        }
    }

    private void handleDeleteSave(Message msg) throws IOException {
        String filename = (String) msg.getPayload();
        boolean success = gameDAO.deleteGame(currentUser.getLogin(), filename);
        send(new Message(MessageType.GAME_STATE, success ? "Удалено" : "Ошибка удаления"));
    }

    private void computerTurn(Game game) throws IOException {
        ComputerStrategy strategy = computerStrategies.get(currentUser.getLogin());
        if (strategy == null) {
            strategy = new ComputerStrategy();
            computerStrategies.put(currentUser.getLogin(), strategy);
        }

        int[] shot = getComputerShot(game, strategy);

        int row = shot[0];
        int col = shot[1];

        boolean hit = game.playerField[row][col] == Game.CELL_SHIP;

        if (hit) {
            game.computerShots[row][col] = Game.CELL_HIT;
            game.playerField[row][col] = Game.CELL_HIT;

            if (game.isShipSunk(game.playerShips, game.computerShots, row, col)) {
                game.markSunkShip(game.playerShips, game.playerField, game.computerShots, row, col);
                game.playerSunkShips++;

                Ship sunkShip = findSunkShip(game.playerShips, row, col);
                if (sunkShip != null) {
                    Object[] payload = {sunkShip.cells, true}; // true — поле игрока
                    send(new Message(MessageType.SHIP_SUNK, payload));

                    for (int[] cell : sunkShip.cells) {
                        strategy.hitCells.remove(cell[0] + "," + cell[1]);
                    }
                }
                strategy.recordHit(row, col);
            } else {
                strategy.recordHit(row, col);
            }

            if (game.playerSunkShips == game.playerShips.size()) {
                game.gameOver = true;
                game.playerWon = false;
                send(new Message(MessageType.GAME_OVER, false));
                gameDAO.deleteGame(currentUser.getLogin(), "autosave_latest.dat");
                activeGames.remove(currentUser.getLogin());
                computerStrategies.remove(currentUser.getLogin());
                return;
            }
        } else {
            game.computerShots[row][col] = Game.CELL_MISS;
            game.playerField[row][col] = Game.CELL_MISS;
            game.isPlayerTurn = true;
            strategy.recordMiss();
        }

        send(new Message(MessageType.OPPONENT_SHOT, new int[]{row, col, hit ? 1 : 0}));

        saveStrategyToGame(game);

        gameDAO.autosaveLatest(currentUser.getLogin(), game);

        if (hit && !game.gameOver) {
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
            case "Средний" -> strategy.getMediumShot(game);
            case "Сложный" -> strategy.getHardShot(game);
            default -> strategy.getRandomShot(game);
        };
    }

    private Ship findSunkShip(List<Ship> ships, int row, int col) {
        for (Ship ship : ships) {
            for (int[] cell : ship.cells) {
                if (cell[0] == row && cell[1] == col) {
                    return ship;
                }
            }
        }
        return null;
    }

    private void saveStrategyToGame(Game game) {
        ComputerStrategy strategy = computerStrategies.get(currentUser.getLogin());
        if (strategy != null) {
            game.computerHitCells.clear();

            for (String hit : strategy.hitCells) {
                String[] parts = hit.split(",");
                game.computerHitCells.add(new int[]{
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1])
                });
            }

            game.computerLastHit = strategy.lastHit;
            game.computerCurrentDirection = strategy.currentDirection;
        }
    }

    private void restoreStrategyFromGame(Game game, ComputerStrategy strategy) {
        if (game.computerHitCells != null) {
            for (int[] hit : game.computerHitCells) {
                strategy.hitCells.add(hit[0] + "," + hit[1]);
            }
        }

        strategy.lastHit = game.computerLastHit;
        strategy.currentDirection = game.computerCurrentDirection;
    }

    private void send(Message msg) throws IOException {
        out.writeObject(msg);
        out.flush();
    }

    private void close() {
        if (currentUser != null) {
            Game game = activeGames.get(currentUser.getLogin());
            if (game != null) {
                saveStrategyToGame(game);

                String filename = gameDAO.saveGame(currentUser.getLogin(), game, true);
                if (filename != null) {
                    System.out.println("Автосохранение при отключении для " + currentUser.getLogin() + ": " + filename);
                }
            }
        }

        if (authToken != null) {
            activeTokens.remove(authToken);
        }

        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}

        if (currentUser != null) {
            activeGames.remove(currentUser.getLogin());
            computerStrategies.remove(currentUser.getLogin());
        }
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
            } while (game.computerShots[row][col] != Game.CELL_EMPTY);
            return new int[]{row, col};
        }

        int[] getMediumShot(Game game) {
            if (!hitCells.isEmpty()) {
                for (String hit : hitCells) {
                    String[] parts = hit.split(",");
                    int r = Integer.parseInt(parts[0]);
                    int c = Integer.parseInt(parts[1]);
                    int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
                    for (int[] d : dirs) {
                        int nr = r + d[0];
                        int nc = c + d[1];
                        if (nr >= 0 && nr < 10 && nc >= 0 && nc < 10 &&
                                game.computerShots[nr][nc] == Game.CELL_EMPTY) {
                            return new int[]{nr, nc};
                        }
                    }
                }
            }
            return getRandomShot(game);
        }

        int[] getHardShot(Game game) {
            if (!hitCells.isEmpty()) {
                if (lastHit != null) {
                    int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
                    if (currentDirection == -1) {
                        for (int d = 0; d < 4; d++) {
                            int nr = lastHit[0] + dirs[d][0];
                            int nc = lastHit[1] + dirs[d][1];
                            if (nr >= 0 && nr < 10 && nc >= 0 && nc < 10 &&
                                    hitCells.contains(nr + "," + nc)) {
                                currentDirection = d;
                                break;
                            }
                        }
                    }
                    if (currentDirection != -1) {
                        int nr = lastHit[0] + dirs[currentDirection][0];
                        int nc = lastHit[1] + dirs[currentDirection][1];
                        if (nr >= 0 && nr < 10 && nc >= 0 && nc < 10 &&
                                game.computerShots[nr][nc] == Game.CELL_EMPTY) {
                            return new int[]{nr, nc};
                        }
                        int opp = (currentDirection % 2 == 0) ? currentDirection + 1 : currentDirection - 1;
                        nr = lastHit[0] + dirs[opp][0];
                        nc = lastHit[1] + dirs[opp][1];
                        if (nr >= 0 && nr < 10 && nc >= 0 && nc < 10 &&
                                game.computerShots[nr][nc] == Game.CELL_EMPTY) {
                            return new int[]{nr, nc};
                        }
                    }
                }
                return getMediumShot(game);
            }
            return getRandomShot(game);
        }
    }
}