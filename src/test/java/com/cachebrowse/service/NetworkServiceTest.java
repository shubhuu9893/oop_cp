package com.cachebrowse.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkServiceTest {
    private final NetworkService service = new NetworkService();
    private static final String SEARCH = "https://html.duckduckgo.com/html/?q=%s";

    @Test
    void resolvesExplicitHttpAndHttpsAddresses() {
        assertEquals("https://example.com", service.resolveUrl("https://example.com", SEARCH));
        assertEquals("http://example.com/path", service.resolveUrl("http://example.com/path", SEARCH));
        assertTrue(service.isValidUrl(service.resolveUrl("example.com/docs", SEARCH)));
    }

    @Test
    void routesWordsAndSpacesToConfiguredSearchEngine() {
        assertEquals("https://search.example/find?term=machine+learning",
                service.resolveUrl("machine learning", "https://search.example/find?term=%s"));
        assertEquals("https://search.example/find?term=YouTube",
                service.resolveUrl("YouTube", "https://search.example/find?term=%s"));
    }

    @Test
    void rejectsEmptyInvalidSchemeAndMalformedSearchConfiguration() {
        assertEquals("", service.resolveUrl("  ", SEARCH));
        assertEquals("", service.resolveUrl("file:///etc/passwd", SEARCH));
        assertEquals("", service.resolveUrl("https://user:secret@example.com", SEARCH));
        assertEquals("", service.resolveUrl("hello", "file:///search?q=%s"));
        assertFalse(service.isValidSearchEngineTemplate("https://user:secret@search.example/find?q=%s"));
        assertFalse(service.isValidUrl("javascript:alert(1)"));
        assertFalse(service.isValidUrl("https://user:secret@example.com"));
        assertFalse(service.isValidSearchEngineTemplate("https://search.example/find?q=%s&again=%s"));
        assertTrue(service.isValidSearchEngineTemplate(SEARCH));
    }
}
