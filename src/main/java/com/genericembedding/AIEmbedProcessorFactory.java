package com.genericembedding;

import com.genericembedding.providers.EmbeddingProvider;
import com.genericembedding.providers.ProviderFactory;
import org.elasticsearch.ingest.Processor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AIEmbedProcessorFactory implements Processor.Factory {

    @Override
    public Processor create(Map<String, Processor.Factory> factories, String tag, String description, Map<String, Object> config) throws Exception {
        List<String> sourceFields;
        Object sourceFieldsObj = config.remove(PluginConstants.CONFIG_SOURCE_FIELDS);
        if (sourceFieldsObj == null) {
            throw new IllegalArgumentException(PluginConstants.ERROR_SOURCE_FIELDS_MISSING);
        }
        if (sourceFieldsObj instanceof List) {
            sourceFields = (List<String>) sourceFieldsObj;
        } else if (sourceFieldsObj instanceof String) {
            sourceFields = Collections.singletonList((String) sourceFieldsObj);
        } else {
            throw new IllegalArgumentException(PluginConstants.ERROR_SOURCE_FIELDS_INVALID_TYPE);
        }
        
        Map<String, Object> processedConfig = new HashMap<>();
        processedConfig.put(PluginConstants.CONFIG_API_URL, config.remove(PluginConstants.CONFIG_API_URL));
        processedConfig.put(PluginConstants.CONFIG_MODEL, config.remove(PluginConstants.CONFIG_MODEL));
        processedConfig.put(PluginConstants.CONFIG_REQUEST_TEMPLATE, config.remove(PluginConstants.CONFIG_REQUEST_TEMPLATE));
        processedConfig.put(PluginConstants.CONFIG_RESPONSE_PATH, config.remove(PluginConstants.CONFIG_RESPONSE_PATH));
        processedConfig.put(PluginConstants.CONFIG_CONNECT_TIMEOUT, config.remove(PluginConstants.CONFIG_CONNECT_TIMEOUT));
        processedConfig.put(PluginConstants.CONFIG_READ_TIMEOUT, config.remove(PluginConstants.CONFIG_READ_TIMEOUT));
        processedConfig.put(PluginConstants.CONFIG_PROVIDER, config.remove(PluginConstants.CONFIG_PROVIDER));
        processedConfig.put(PluginConstants.CONFIG_MAX_REQUESTS_PER_SECOND, config.remove(PluginConstants.CONFIG_MAX_REQUESTS_PER_SECOND));
        processedConfig.put(PluginConstants.CONFIG_BACKOFF_INITIAL_DELAY_MS, config.remove(PluginConstants.CONFIG_BACKOFF_INITIAL_DELAY_MS));
        processedConfig.put(PluginConstants.CONFIG_BACKOFF_MAX_DELAY_MS, config.remove(PluginConstants.CONFIG_BACKOFF_MAX_DELAY_MS));
        processedConfig.put(PluginConstants.CONFIG_BACKOFF_MULTIPLIER, config.remove(PluginConstants.CONFIG_BACKOFF_MULTIPLIER));
        processedConfig.put(PluginConstants.CONFIG_HEADERS, config.remove(PluginConstants.CONFIG_HEADERS));
        
        processedConfig.putIfAbsent(PluginConstants.CONFIG_API_URL, PluginConstants.DEFAULT_API_URL);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_MODEL, PluginConstants.DEFAULT_MODEL);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_REQUEST_TEMPLATE, PluginConstants.DEFAULT_REQUEST_TEMPLATE);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_RESPONSE_PATH, PluginConstants.DEFAULT_RESPONSE_PATH);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_CONNECT_TIMEOUT, PluginConstants.DEFAULT_CONNECT_TIMEOUT);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_READ_TIMEOUT, PluginConstants.DEFAULT_READ_TIMEOUT);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_PROVIDER, PluginConstants.PROVIDER_TYPE_GENERIC);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_MAX_REQUESTS_PER_SECOND, PluginConstants.DEFAULT_MAX_REQUESTS_PER_SECOND);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_BACKOFF_INITIAL_DELAY_MS, PluginConstants.DEFAULT_BACKOFF_INITIAL_DELAY_MS);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_BACKOFF_MAX_DELAY_MS, PluginConstants.DEFAULT_BACKOFF_MAX_DELAY_MS);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_BACKOFF_MULTIPLIER, PluginConstants.DEFAULT_BACKOFF_MULTIPLIER);
        processedConfig.putIfAbsent(PluginConstants.CONFIG_HEADERS, new HashMap<String, String>());
        
        EmbeddingProvider provider = ProviderFactory.create(processedConfig);

        return new AIEmbedProcessor(tag, description, sourceFields, provider);
    }
}
