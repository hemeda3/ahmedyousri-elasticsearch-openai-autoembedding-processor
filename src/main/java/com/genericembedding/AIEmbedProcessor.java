package com.genericembedding;

import com.genericembedding.providers.EmbeddingProvider;
import com.genericembedding.providers.ProviderRequest;
import com.genericembedding.providers.ProviderResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;

import java.util.ArrayList;
import java.util.List;

public class AIEmbedProcessor extends AbstractProcessor {

    private static final Logger logger = LogManager.getLogger(AIEmbedProcessor.class);
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
        logger.info("=== STARTING EMBEDDING PROCESSOR ===");
        logger.info("Document index: {}", ingestDocument.getSourceAndMetadata().get("_index"));
        logger.info("Source fields to process: {}", sourceFields);
        
        List<ProviderRequest> requests = new ArrayList<>();
        List<String> fieldsToEmbed = new ArrayList<>();

        logger.info("Checking fields in document...");
        for (String field : sourceFields) {
            logger.info("Checking field: {}", field);
            if (ingestDocument.hasField(field)) {
                String content = ingestDocument.getFieldValue(field, String.class);
                logger.info("Field '{}' found with content length: {}", field, content != null ? content.length() : 0);
                if (content != null && !content.isEmpty()) {
                    requests.add(new ProviderRequest(content));
                    fieldsToEmbed.add(field);
                    logger.info("Added field '{}' to embedding queue", field);
                } else {
                    logger.info("Field '{}' is empty or null, skipping", field);
                }
            } else {
                logger.info("Field '{}' not found in document", field);
            }
        }

        logger.info("Total fields to embed: {}", fieldsToEmbed.size());
        logger.info("Fields to embed: {}", fieldsToEmbed);

        if (!requests.isEmpty()) {
            try {
                logger.info("=== CALLING EMBEDDING PROVIDER ===");
                logger.info("Provider class: {}", provider.getClass().getSimpleName());
                logger.info("Number of requests: {}", requests.size());
                
                ProviderResponse response = provider.embed(requests);
                
                logger.info("=== EMBEDDING PROVIDER RESPONSE ===");
                List<List<Float>> vectors = response.getVectors();
                logger.info("Number of vectors returned: {}", vectors.size());
                
                for (int i = 0; i < vectors.size(); i++) {
                    logger.info("Vector {} dimensions: {}", i, vectors.get(i).size());
                }

                if (vectors.size() != fieldsToEmbed.size()) {
                    String errorMsg = "Number of returned embeddings (" + vectors.size() + ") does not match number of requested fields (" + fieldsToEmbed.size() + ")";
                    logger.error(errorMsg);
                    throw new IllegalStateException(errorMsg);
                }

                logger.info("=== SETTING VECTOR FIELDS ===");
                for (int i = 0; i < fieldsToEmbed.size(); i++) {
                    String vectorField = fieldsToEmbed.get(i) + "_vector";
                    logger.info("Setting field '{}' with vector of {} dimensions", vectorField, vectors.get(i).size());
                    ingestDocument.setFieldValue(vectorField, vectors.get(i));
                    logger.info("Successfully set field '{}'", vectorField);
                }
                
                logger.info("=== EMBEDDING PROCESSOR COMPLETED SUCCESSFULLY ===");
            } catch (Exception e) {
                logger.error("=== EMBEDDING PROCESSOR ERROR ===");
                logger.error("Error type: {}", e.getClass().getSimpleName());
                logger.error("Error message: {}", e.getMessage());
                logger.error("Full stack trace:", e);
                
                String errorMsg = "Failed to embed fields: " + e.getMessage();
                logger.info("Setting embedding_error field with: {}", errorMsg);
                ingestDocument.setFieldValue("embedding_error", errorMsg);
            }
        } else {
            logger.info("No fields to embed, skipping embedding process");
        }
        
        logger.info("=== EMBEDDING PROCESSOR FINISHED ===");
        return ingestDocument;
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
