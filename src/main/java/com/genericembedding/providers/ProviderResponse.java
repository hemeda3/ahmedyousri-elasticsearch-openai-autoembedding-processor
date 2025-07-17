package com.genericembedding.providers;

import java.util.List;
import java.util.Map;

public class ProviderResponse {
    private final List<List<Float>> vectors;
    private final Map<String, Object> usage;

    public ProviderResponse(List<List<Float>> vectors) {
        this.vectors = vectors;
        this.usage = null;
    }

    public ProviderResponse(List<List<Float>> vectors, Map<String, Object> usage) {
        this.vectors = vectors;
        this.usage = usage;
    }

    public List<List<Float>> getVectors() {
        return vectors;
    }

    public Map<String, Object> getUsage() {
        return usage;
    }

    public boolean hasUsage() {
        return usage != null && !usage.isEmpty();
    }
}
