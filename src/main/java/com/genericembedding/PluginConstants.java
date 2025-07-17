package com.genericembedding;

public final class PluginConstants {

    private PluginConstants() {
    }

    public static final String PROCESSOR_TYPE = "ai_embed";

    public static final String DEFAULT_API_URL = "https://api.openai.com/v1/embeddings";
    public static final String DEFAULT_MODEL = "text-embedding-3-small";
    public static final String DEFAULT_REQUEST_TEMPLATE = "{\"input\": \"{{text}}\", \"model\": \"{{model}}\"}";
    public static final String DEFAULT_RESPONSE_PATH = "data.0.embedding";
    public static final String DEFAULT_CONNECT_TIMEOUT = "5s";
    public static final String DEFAULT_READ_TIMEOUT = "10s";
    public static final int DEFAULT_MAX_REQUESTS_PER_SECOND = 10;
    public static final long DEFAULT_BACKOFF_INITIAL_DELAY_MS = 1000L;
    public static final long DEFAULT_BACKOFF_MAX_DELAY_MS = 30000L;
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;

    // Provider Types
    public static final String PROVIDER_TYPE_GENERIC = "generic";
    public static final String PROVIDER_TYPE_OPENAI = "openai";

    public static final String CONFIG_SOURCE_FIELDS = "source_fields";
    public static final String CONFIG_API_URL = "api_url";
    public static final String CONFIG_API_KEY = "api_key";
    public static final String CONFIG_MODEL = "model";
    public static final String CONFIG_HEADERS = "headers";
    public static final String CONFIG_REQUEST_TEMPLATE = "request_template";
    public static final String CONFIG_RESPONSE_PATH = "response_path";
    public static final String CONFIG_CONNECT_TIMEOUT = "connect_timeout";
    public static final String CONFIG_READ_TIMEOUT = "read_timeout";
    public static final String CONFIG_PROVIDER = "provider";
    public static final String CONFIG_MAX_REQUESTS_PER_SECOND = "max_requests_per_second";
    public static final String CONFIG_BACKOFF_INITIAL_DELAY_MS = "backoff_initial_delay_ms";
    public static final String CONFIG_BACKOFF_MAX_DELAY_MS = "backoff_max_delay_ms";
    public static final String CONFIG_BACKOFF_MULTIPLIER = "backoff_multiplier";


    public static final String ERROR_SOURCE_FIELDS_MISSING = "required property [" + CONFIG_SOURCE_FIELDS + "] is missing for processor [" + PROCESSOR_TYPE + "]";
    public static final String ERROR_SOURCE_FIELDS_INVALID_TYPE = "property [" + CONFIG_SOURCE_FIELDS + "] must be a list of strings or a string for processor [" + PROCESSOR_TYPE + "]";
    public static final String ERROR_INVALID_EMBEDDING_RESPONSE = "Invalid embedding response format or path: ";
    public static final String ERROR_API_REQUEST_FAILED = "API request failed with code ";
}
