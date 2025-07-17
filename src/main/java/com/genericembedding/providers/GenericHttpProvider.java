package com.genericembedding.providers;

import com.genericembedding.HttpHelper;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class GenericHttpProvider implements EmbeddingProvider {
    private final HttpHelper httpHelper;

    public GenericHttpProvider(Map<String, Object> config) {
        this.httpHelper = new HttpHelper(config);
    }

    @Override
    public ProviderResponse embed(List<ProviderRequest> requests) throws IOException {
        return httpHelper.getEmbeddings(requests);
    }
}
