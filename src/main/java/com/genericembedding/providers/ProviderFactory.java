package com.genericembedding.providers;

import com.genericembedding.HttpHelper;
import com.genericembedding.PluginConstants;
import java.util.Map;

public class ProviderFactory {
    public static EmbeddingProvider create(Map<String, Object> config) {
        String providerType = (String) config.getOrDefault(PluginConstants.CONFIG_PROVIDER, PluginConstants.PROVIDER_TYPE_GENERIC);

        switch (providerType.toLowerCase()) {
            case PluginConstants.PROVIDER_TYPE_OPENAI:
                return new OpenAIProvider(config);
            case PluginConstants.PROVIDER_TYPE_GENERIC:
            default:
                return new GenericHttpProvider(config);
        }
    }
}
