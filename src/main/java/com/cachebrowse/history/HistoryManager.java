package com.cachebrowse.history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages browsing history. Data persisted to {@code data/history.txt}.
 */
public class HistoryManager {
    private final List<String> history = new ArrayList<>();
    private final Path filePath;

    public HistoryManager() {
        this.filePath = Path.of("data", "history.txt");
        try {
            Files.createDirectories(filePath.getParent());
            if (Files.exists(filePath)) {
                history.addAll(Files.readAllLines(filePath)
                        .stream()
                        .map(String::trim)
                        .filter(l -> !l.isEmpty())
                        .collect(Collectors.toList()));
            }
        } catch (IOException e) {
            // If we cannot read, start with an empty list.
        }
    }

    public synchronized void addHistory(String url) {
        if (url == null || url.isBlank()) return;
        String trimmed = url.trim();
        history.add(trimmed);
        // Append to file for persistence.
        try {
            Files.writeString(filePath, trimmed + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            // ignore write failures – history will still be in memory.
        }
    }

    public synchronized List<String> getAll() {
        return new ArrayList<>(history);
    }

    public synchronized void clear() {
        history.clear();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }
}