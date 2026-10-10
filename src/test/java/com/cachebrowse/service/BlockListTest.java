package com.cachebrowse.service;

import com.cachebrowse.adblock.BlockList;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class BlockListTest {
    @TempDir Path temporaryDirectory;

    @Test
    void persistsCustomRulesAndRemovedDefaultsAsACompleteSnapshot() {
        Path file = temporaryDirectory.resolve("rules.txt");
        BlockList initial = new BlockList(file.toString(), Set.of("ads.example.com", "tracker.example.com"));
        initial.removeRule("ads.example.com");
        initial.addRule("custom.example.net");

        BlockList restored = new BlockList(file.toString(), Set.of("ads.example.com", "tracker.example.com"));
        assertFalse(restored.getRules().contains("ads.example.com"));
        assertTrue(restored.getRules().contains("tracker.example.com"));
        assertTrue(restored.getRules().contains("custom.example.net"));
    }
}
