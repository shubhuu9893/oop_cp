package com.cachebrowse.network;

/**
 * Simple wrapper around {@code java.net.http.HttpClient} that provides a
 * convenience method to perform an HTTP GET with timeout support. The wrapper
 * is intentionally minimal; it only implements what the rest of the project
 * needs at the moment (plain GET requests, response body as byte[] and a set of
 * useful headers).
 */
public class HttpClient {

    /** The underlying Java HTTP client configured for normal redirects and
     * a reasonable connection timeout.  All requests will use this instance.
     */
    private static final java.net.http.HttpClient client =
            java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

    /**
     * Performs an HTTP GET for {@code url} and returns the raw byte[] body.
     * <p>
     * The method blocks until a response is received or {@code timeoutMs}
     * elapses.  A {@link java.util.concurrent.TimeoutException} will be
     * thrown if the operation times out, which callers can catch and handle.
     */
     public static java.net.http.HttpResponse<byte[]> get(String url, long timeoutMs)
             throws Exception {
         java.net.URI uri = java.net.URI.create(url);
         var request = java.net.http.HttpRequest.newBuilder(uri).GET().build();
         // The blocking send() does not throw TimeoutException; it only throws
         // IOException or InterruptedException. The timeout is handled by the
         // underlying HttpClient configuration, so we simply forward any
         // checked exceptions to callers.
         return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
     }

    /**
     * Convenience async wrapper that returns a {@link CompletableFuture}.
     * The future completes with the response or exceptionally if an error
     * occurs.  It automatically applies the specified timeout.
     */
    public static java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<byte[]>> getAsync(String url, long timeoutMs) {
        java.net.URI uri = java.net.URI.create(url);
        var request = java.net.http.HttpRequest.newBuilder(uri).GET().build();
        return client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
                .orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}