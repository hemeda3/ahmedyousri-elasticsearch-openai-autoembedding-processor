package com.genericembedding.providers;

import java.util.List;

public class ProviderResponse {
    private final List<List<Float>> vectors;

    public ProviderResponse(List<List<Float>> vectors) {
        this.vectors = vectors;
    }

    public List<List<Float>> getVectors() {
        return vectors;
    }
}
