package com.cachebrowse.bookmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simple bookmark manager that persists bookmarks to {@code data/bookmarks.txt}.
 */
public class BookmarkManager {
    private final List<String> bookmarks = new ArrayList<>();
    private final Path filePath;

    public BookmarkManager() {
        this.filePath = Path.of("data", "bookmarks.txt");
        try {
            Files.createDirectories(filePath.getParent());
            if (Files.exists(filePath)) {
                bookmarks.addAll(Files.readAllLines(filePath)
                        .stream()
                        .map(String::trim)
                        .filter(l -> !l.isEmpty())
                        .collect(Collectors.toList()));
            }
        } catch (IOException e) {
            // start with empty list
        }
    }

    public synchronized void addBookmark(String url) {
        if (url == null || url.isBlank()) return;
        String trimmed = url.trim();
        bookmarks.add(trimmed);
        try {
            Files.writeString(filePath, trimmed + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    public synchronized void removeBookmark(int index) {
        if (index < 0 || index >= bookmarks.size()) return;
        bookmarks.remove(index);
        persist();
    }

    public synchronized List<String> getAll() {
        return new ArrayList<>(bookmarks);
    }

    private void persist() {
        try {
            Files.write(filePath, bookmarks.stream()
                    .map(s -> s + System.lineSeparator())
                    .collect(Collectors.toList()), java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public synchronized void clear() {
        bookmarks.clear();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }
}