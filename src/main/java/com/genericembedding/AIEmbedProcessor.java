package com.genericembedding;

import com.genericembedding.providers.EmbeddingProvider;
import com.genericembedding.providers.ProviderRequest;
import com.genericembedding.providers.ProviderResponse;
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;

import java.util.ArrayList;
import java.util.List;

public class AIEmbedProcessor extends AbstractProcessor {

    public static final String TYPE = PluginConstants.PROCESSOR_TYPE;
    private final EmbeddingProvider provider;
    private final List<String> sourceFields;

    public AIEmbedProcessor(String tag, String description, List<String> sourceFields, EmbeddingProvider provider) {
        super(tag, description);
        this.sourceFields = sourceFields;
        this.provider = provider;
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) {
        List<ProviderRequest> requests = new ArrayList<>();
        List<String> fieldsToEmbed = new ArrayList<>();

        for (String field : sourceFields) {
            if (ingestDocument.hasField(field)) {
                String content = ingestDocument.getFieldValue(field, String.class);
                if (content != null && !content.isEmpty()) {
                    requests.add(new ProviderRequest(content));
                    fieldsToEmbed.add(field);
                }
            }
        }

        if (!requests.isEmpty()) {
            try {
                ProviderResponse response = provider.embed(requests);
                List<List<Float>> vectors = response.getVectors();

                if (vectors.size() != fieldsToEmbed.size()) {
                    throw new IllegalStateException("Number of returned embeddings does not match number of requested fields.");
                }

                for (int i = 0; i < fieldsToEmbed.size(); i++) {
                    ingestDocument.setFieldValue(fieldsToEmbed.get(i) + "_vector", vectors.get(i));
                }
            } catch (Exception e) {
                ingestDocument.setFieldValue("embedding_error", "Failed to embed fields: " + e.getMessage());
            }
        }
        return ingestDocument;
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
