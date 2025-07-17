package com.genericembedding;

import org.elasticsearch.ingest.Processor;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;

import java.util.Collections;
import java.util.Map;

/**
 * Main plugin class for AI Embedding Processor
 */
public class AIEmbedPlugin extends Plugin implements IngestPlugin {
    
    public static final String TYPE = PluginConstants.PROCESSOR_TYPE;

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
        return Collections.singletonMap(TYPE, new AIEmbedProcessorFactory());
    }
}
