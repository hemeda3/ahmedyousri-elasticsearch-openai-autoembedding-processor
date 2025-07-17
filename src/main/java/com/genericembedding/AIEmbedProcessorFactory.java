package com.genericembedding;

import com.genericembedding.providers.EmbeddingProvider;
import com.genericembedding.providers.ProviderFactory;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.ingest.Processor.Factory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AIEmbedProcessorFactory implements Factory {

    @Override
    public Processor create(Map<String, Processor.Factory> factories, String tag, String description, Map<String, Object> config) throws Exception {
        List<String> sourceFields;
        Object sourceFieldsObj = config.get(PluginConstants.CONFIG_SOURCE_FIELDS);
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
        
        EmbeddingProvider provider = ProviderFactory.create(config);

        return new AIEmbedProcessor(tag, description, sourceFields, provider);
    }
}
