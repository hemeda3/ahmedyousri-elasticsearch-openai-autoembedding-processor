package com.genericembedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.genericembedding.providers.ProviderResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.genericembedding.providers.ProviderRequest;
import java.util.stream.Collectors;

public class HttpHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String apiUrl;
    private final String model;
    private final Map<String, String> headers;
    private final String requestTemplate;
    private final String responsePath;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final RateLimiter rateLimiter;
    private final BackoffStrategy backoffStrategy;

    public HttpHelper(Map<String, Object> config) {
        this.apiUrl = (String) config.getOrDefault(PluginConstants.CONFIG_API_URL, PluginConstants.DEFAULT_API_URL);
        this.model = (String) config.getOrDefault(PluginConstants.CONFIG_MODEL, PluginConstants.DEFAULT_MODEL);
        this.headers = (Map<String, String>) config.getOrDefault(PluginConstants.CONFIG_HEADERS, Map.of());
        this.requestTemplate = (String) config.getOrDefault(PluginConstants.CONFIG_REQUEST_TEMPLATE, PluginConstants.DEFAULT_REQUEST_TEMPLATE);
        this.responsePath = (String) config.getOrDefault(PluginConstants.CONFIG_RESPONSE_PATH, PluginConstants.DEFAULT_RESPONSE_PATH);
        this.connectTimeoutMillis = (int) parseDurationToMillis((String) config.getOrDefault(PluginConstants.CONFIG_CONNECT_TIMEOUT, PluginConstants.DEFAULT_CONNECT_TIMEOUT));
        this.readTimeoutMillis = (int) parseDurationToMillis((String) config.getOrDefault(PluginConstants.CONFIG_READ_TIMEOUT, PluginConstants.DEFAULT_READ_TIMEOUT));

        int maxRequestsPerSecond = (int) config.getOrDefault(PluginConstants.CONFIG_MAX_REQUESTS_PER_SECOND, PluginConstants.DEFAULT_MAX_REQUESTS_PER_SECOND);
        this.rateLimiter = new RateLimiter(maxRequestsPerSecond);

        long initialDelayMs = (long) config.getOrDefault(PluginConstants.CONFIG_BACKOFF_INITIAL_DELAY_MS, PluginConstants.DEFAULT_BACKOFF_INITIAL_DELAY_MS);
        long maxDelayMs = (long) config.getOrDefault(PluginConstants.CONFIG_BACKOFF_MAX_DELAY_MS, PluginConstants.DEFAULT_BACKOFF_MAX_DELAY_MS);
        double multiplier = (double) config.getOrDefault(PluginConstants.CONFIG_BACKOFF_MULTIPLIER, PluginConstants.DEFAULT_BACKOFF_MULTIPLIER);
        this.backoffStrategy = new BackoffStrategy(initialDelayMs, maxDelayMs, multiplier);
    }

    public ProviderResponse getEmbeddings(List<ProviderRequest> requests) throws IOException {
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Rate limiter interrupted", e);
        }

        int maxRetries = 5;
        backoffStrategy.reset();

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
                connection.setDoOutput(true);
                connection.setConnectTimeout(connectTimeoutMillis);
                connection.setReadTimeout(readTimeoutMillis);

                String inputsJson = requests.stream()
                    .map(req -> "\"" + escapeJson(req.getText()) + "\"")
                    .collect(Collectors.joining(","));

                String requestBody = requestTemplate
                    .replace("{{text}}", "[" + inputsJson + "]")
                    .replace("{{model}}", escapeJson(model));

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (InputStream is = connection.getInputStream()) {
                        JsonNode rootNode = MAPPER.readTree(is);
                        JsonNode dataNode = findNodeByPath(rootNode, "data");

                        if (dataNode != null && dataNode.isArray()) {
                            List<List<Float>> allEmbeddings = new ArrayList<>();
                            for (JsonNode itemNode : (ArrayNode) dataNode) {
                                JsonNode embeddingNode = findNodeByPath(itemNode, responsePath.substring(responsePath.indexOf(".") + 1));
                                if (embeddingNode != null && embeddingNode.isArray()) {
                                    List<Float> embedding = new ArrayList<>();
                                    for (JsonNode node : (ArrayNode) embeddingNode) {
                                        embedding.add(node.floatValue());
                                    }
                                    allEmbeddings.add(embedding);
                                } else {
                                    throw new IOException(PluginConstants.ERROR_INVALID_EMBEDDING_RESPONSE + responsePath + " for item.");
                                }
                            }
                            return new ProviderResponse(allEmbeddings);
                        } else {
                            throw new IOException(PluginConstants.ERROR_INVALID_EMBEDDING_RESPONSE + "data array not found or invalid.");
                        }
                    }
                } else if (responseCode == 429 || responseCode >= 500) {
                    if (attempt < maxRetries - 1) {
                        long delay = backoffStrategy.nextDelay();
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Backoff interrupted", e);
                        }
                        continue;
                    } else {
                        String errorResponse;
                        try (InputStream es = connection.getErrorStream()) {
                            errorResponse = es != null ? new String(es.readAllBytes(), StandardCharsets.UTF_8) : "No error stream";
                        }
                        throw new IOException(PluginConstants.ERROR_API_REQUEST_FAILED + responseCode + ": " + errorResponse);
                    }
                } else {
                    String errorResponse;
                    try (InputStream es = connection.getErrorStream()) {
                        errorResponse = es != null ? new String(es.readAllBytes(), StandardCharsets.UTF_8) : "No error stream";
                    }
                    throw new IOException(PluginConstants.ERROR_API_REQUEST_FAILED + responseCode + ": " + errorResponse);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        throw new IOException("Max retries exceeded");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private long parseDurationToMillis(String durationString) {
        if (durationString.endsWith("s")) {
            return Long.parseLong(durationString.substring(0, durationString.length() - 1)) * 1000;
        } else if (durationString.endsWith("m")) {
            return Long.parseLong(durationString.substring(0, durationString.length() - 1)) * 60 * 1000;
        } else if (durationString.endsWith("h")) {
            return Long.parseLong(durationString.substring(0, durationString.length() - 1)) * 60 * 60 * 1000;
        }
        try {
            return Long.parseLong(durationString) * 1000;
        } catch (NumberFormatException e) {
            return Duration.parse("PT" + durationString.toUpperCase()).toMillis();
        }
    }

    private JsonNode findNodeByPath(JsonNode root, String path) {
        String[] pathParts = path.split("\\.");
        JsonNode currentNode = root;
        for (String part : pathParts) {
            if (currentNode == null) {
                return null;
            }
            if (part.matches("\\d+")) {
                int index = Integer.parseInt(part);
                if (currentNode.isArray() && index < currentNode.size()) {
                    currentNode = currentNode.get(index);
                } else {
                    return null;
                }
            } else {
                currentNode = currentNode.get(part);
            }
        }
        return currentNode;
    }
}
