package com.cachebrowse.proxy;

import java.io.IOException;
import java.net.Socket;

/**
 * Handles a single client connection.  This is just a placeholder to satisfy
 * compilation; the actual request parsing, proxying and response handling will
 * be added later.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (socket) {
            // TODO: Parse request, forward to remote server or serve from cache.
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}