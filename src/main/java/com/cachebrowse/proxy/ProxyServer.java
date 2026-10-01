package com.cachebrowse.proxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Skeleton for the local HTTP proxy server.
 */
public class ProxyServer {
    private static final int PORT = 8080;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running = false;

    public void start() throws IOException {
        serverSocket = new ServerSocket(PORT);
        executor = Executors.newFixedThreadPool(10); // placeholder pool size
        running = true;
        System.out.println("ProxyServer listening on port " + PORT);
        while (running) {
            try {
                var clientSocket = serverSocket.accept();
                executor.submit(new ClientHandler(clientSocket));
            } catch (IOException e) {
                if (!running) break;
                e.printStackTrace();
            }
        }
    }

    public void stop() throws IOException {
        running = false;
        serverSocket.close();
        executor.shutdownNow();
    }
}