package com.cachebrowse.proxy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Formats and writes raw HTTP responses to an output stream.
 */
public class ResponseHandler {

    public static void writeResponse(OutputStream out, int statusCode, String statusText,
                                     String contentType, byte[] body, Map<String, List<String>> headers) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n");
        sb.append("Content-Type: ").append(contentType != null ? contentType : "text/html; charset=UTF-8").append("\r\n");
        sb.append("Content-Length: ").append(body != null ? body.length : 0).append("\r\n");
        sb.append("Connection: close\r\n");

        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String key = entry.getKey();
                if ("Content-Length".equalsIgnoreCase(key) || "Content-Type".equalsIgnoreCase(key) || "Connection".equalsIgnoreCase(key)) {
                    continue;
                }
                for (String val : entry.getValue()) {
                    sb.append(key).append(": ").append(val).append("\r\n");
                }
            }
        }
        sb.append("\r\n");

        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        if (body != null && body.length > 0) {
            out.write(body);
        }
        out.flush();
    }

    public static void writeBlockedResponse(OutputStream out, String reason) throws IOException {
        String body = "<!DOCTYPE html><html><body><h1>403 Forbidden</h1><p>Blocked by " + reason + "</p></body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        writeResponse(out, 403, "Forbidden", "text/html; charset=UTF-8", bytes, null);
    }

    public static void writeErrorResponse(OutputStream out, int statusCode, String message) throws IOException {
        String body = "<!DOCTYPE html><html><body><h1>" + statusCode + " Error</h1><p>" + message + "</p></body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        writeResponse(out, statusCode, "Error", "text/html; charset=UTF-8", bytes, null);
    }
}