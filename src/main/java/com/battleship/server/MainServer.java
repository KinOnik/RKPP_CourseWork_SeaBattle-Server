package com.battleship.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class MainServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8888);
        System.out.println("Сервер запущен на " +
                InetAddress.getLocalHost().getHostAddress() + ":8888");

        while (true) {
            Socket client = serverSocket.accept();
            new Thread(new ClientHandler(client)).start();
        }
    }
}