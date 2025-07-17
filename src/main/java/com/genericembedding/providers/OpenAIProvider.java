package com.genericembedding.providers;

import com.genericembedding.HttpHelper;
import java.io.IOException;
import com.genericembedding.PluginConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAIProvider implements EmbeddingProvider {
    private final HttpHelper httpHelper;

    public OpenAIProvider(Map<String, Object> config) {
        Map<String, Object> openAIConfig = new HashMap<>(config);
        openAIConfig.putIfAbsent(PluginConstants.CONFIG_API_URL, PluginConstants.DEFAULT_API_URL);
        openAIConfig.putIfAbsent(PluginConstants.CONFIG_REQUEST_TEMPLATE, PluginConstants.DEFAULT_REQUEST_TEMPLATE);
        openAIConfig.putIfAbsent(PluginConstants.CONFIG_RESPONSE_PATH, PluginConstants.DEFAULT_RESPONSE_PATH);

        this.httpHelper = new HttpHelper(openAIConfig);
    }

    @Override
    public ProviderResponse embed(List<ProviderRequest> requests) throws IOException {
        return httpHelper.getEmbeddings(requests);
    }
}
