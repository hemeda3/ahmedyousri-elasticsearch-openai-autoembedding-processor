package com.genericembedding.providers;

import java.io.IOException;
import java.util.List;

public interface EmbeddingProvider {
    /**
     * Takes a request and returns a response containing the embedding vector.
     * @param request The request object containing the text and configuration.
     * @return A response object containing the vector.
     * @throws IOException If the API call fails.
     */
    ProviderResponse embed(List<ProviderRequest> requests) throws IOException;
}
