package com.genericembedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.genericembedding.providers.EmbeddingProvider;
import com.genericembedding.providers.ProviderFactory;
import com.genericembedding.providers.ProviderRequest;
import com.genericembedding.providers.ProviderResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.RestApiVersion;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.POST;

public class SemanticSearchRestHandler implements RestHandler {
    
    private static final Logger logger = LogManager.getLogger(SemanticSearchRestHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Settings settings;

    public SemanticSearchRestHandler(Settings settings) {
        this.settings = settings;
    }

    @Override
    public List<Route> routes() {
        return List.of(
            new Route(POST, "/{index}/_semantic_search")
        );
    }

    @Override
    public void handleRequest(RestRequest request, RestChannel channel, NodeClient client) throws Exception {
        logger.info("=== SEMANTIC SEARCH REQUEST RECEIVED ===");
        
        String index = request.param("index");
        logger.info("Target index: {}", index);
        
        // Parse the request body
        String requestBody = null;
        if (request.hasContent()) {
            requestBody = request.content().utf8ToString();
        }
        
        if (requestBody == null || requestBody.trim().isEmpty()) {
            sendErrorResponse(channel, "Request body is required for semantic search", RestStatus.BAD_REQUEST);
            return;
        }
        
        logger.info("Request body: {}", requestBody);
        
        try {
            JsonNode requestJson = MAPPER.readTree(requestBody);
            
            // Check if semantic search is enabled
            if (!requestJson.has("semantic_search")) {
                sendErrorResponse(channel, "semantic_search section is required", RestStatus.BAD_REQUEST);
                return;
            }
            
            JsonNode semanticSearch = requestJson.get("semantic_search");
            if (!semanticSearch.has("enabled") || !semanticSearch.get("enabled").asBoolean()) {
                sendErrorResponse(channel, "semantic_search.enabled must be true", RestStatus.BAD_REQUEST);
                return;
            }
            
            // Extract parameters
            String vectorField = semanticSearch.has("field") ? 
                semanticSearch.get("field").asText() : "full_case_text_vector";
            double boost = semanticSearch.has("boost") ? 
                semanticSearch.get("boost").asDouble() : 1.0;
            
            logger.info("Semantic search parameters - field: {}, boost: {}", vectorField, boost);
            
            // Extract query text from the regular query
            String queryText = extractQueryText(requestJson.get("query"));
            if (queryText == null || queryText.isEmpty()) {
                sendErrorResponse(channel, "Could not extract query text from query section", RestStatus.BAD_REQUEST);
                return;
            }
            
            logger.info("Extracted query text: {}", queryText);
            
            // Generate embedding for the query
            List<Float> queryVector = generateEmbedding(queryText, request);
            logger.info("Generated query vector with {} dimensions", queryVector.size());
            
            // Create the script_score query
            ObjectNode newQuery = createScriptScoreQuery(queryVector, vectorField, boost);
            
            // Create new request without semantic_search section
            ObjectNode newRequest = MAPPER.createObjectNode();
            requestJson.fields().forEachRemaining(entry -> {
                if (!"semantic_search".equals(entry.getKey()) && !"query".equals(entry.getKey())) {
                    newRequest.set(entry.getKey(), entry.getValue());
                }
            });
            
            // Set the new query
            newRequest.set("query", newQuery);
            
            // Create SearchRequest with the converted query
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            
            // Create the script_score query JSON manually and use wrapper query
            // The wrapper query expects the full query structure with the query type as the root
            ObjectNode fullQuery = MAPPER.createObjectNode();
            ObjectNode scriptScoreJson = MAPPER.createObjectNode();
            ObjectNode queryJson = MAPPER.createObjectNode();
            ObjectNode scriptJson = MAPPER.createObjectNode();
            ObjectNode paramsJson = MAPPER.createObjectNode();
            
            // Build the inner query (match_all)
            queryJson.set("match_all", MAPPER.createObjectNode());
            
            // Build the script
            scriptJson.put("source", String.format("cosineSimilarity(params.query_vector, '%s') * %f + 1.0", vectorField, boost));
            
            // Add the query vector as array
            com.fasterxml.jackson.databind.node.ArrayNode vectorArray = MAPPER.createArrayNode();
            for (Float value : queryVector) {
                vectorArray.add(value);
            }
            paramsJson.set("query_vector", vectorArray);
            scriptJson.set("params", paramsJson);
            
            // Assemble the script_score query
            scriptScoreJson.set("query", queryJson);
            scriptScoreJson.set("script", scriptJson);
            
            // Wrap in the script_score query type
            fullQuery.set("script_score", scriptScoreJson);
            
            String scriptScoreQueryString = MAPPER.writeValueAsString(fullQuery);
            logger.info("Created script_score query: {}", scriptScoreQueryString);
            
            sourceBuilder.query(org.elasticsearch.index.query.QueryBuilders.wrapperQuery(scriptScoreQueryString));
            
            // Add other parameters from the original request
            if (newRequest.has("size")) {
                sourceBuilder.size(newRequest.get("size").asInt());
            }
            if (newRequest.has("from")) {
                sourceBuilder.from(newRequest.get("from").asInt());
            }
            if (newRequest.has("_source")) {
                // Handle _source field if present
                JsonNode sourceNode = newRequest.get("_source");
                if (sourceNode.isTextual()) {
                    sourceBuilder.fetchSource(sourceNode.asText(), null);
                } else if (sourceNode.isArray()) {
                    String[] includes = new String[sourceNode.size()];
                    for (int i = 0; i < sourceNode.size(); i++) {
                        includes[i] = sourceNode.get(i).asText();
                    }
                    sourceBuilder.fetchSource(includes, null);
                }
            }
            
            searchRequest.source(sourceBuilder);
            
            logger.info("Executing semantic search request");
            
            // Execute the search and handle response
            client.search(searchRequest, new org.elasticsearch.action.ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    try {
                        XContentBuilder builder = XContentFactory.jsonBuilder();
                        searchResponse.toXContentChunked(org.elasticsearch.xcontent.ToXContent.EMPTY_PARAMS).forEachRemaining(xcontent -> {
                            try {
                                xcontent.toXContent(builder, org.elasticsearch.xcontent.ToXContent.EMPTY_PARAMS);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        channel.sendResponse(new org.elasticsearch.rest.RestResponse(RestStatus.OK, builder));
                    } catch (Exception e) {
                        logger.error("Error building response", e);
                        onFailure(e);
                    }
                }
                
                @Override
                public void onFailure(Exception e) {
                    logger.error("Search execution failed", e);
                    try {
                        sendErrorResponse(channel, "Search execution failed: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR);
                    } catch (IOException ioException) {
                        logger.error("Failed to send error response", ioException);
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("Error processing semantic search: {}", e.getMessage(), e);
            sendErrorResponse(channel, "Error processing semantic search: " + e.getMessage(), RestStatus.BAD_REQUEST);
        }
    }
    
    private void sendErrorResponse(RestChannel channel, String message, RestStatus status) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        builder.field("error", message);
        builder.field("status", status.getStatus());
        builder.endObject();
        
        channel.sendResponse(new org.elasticsearch.rest.RestResponse(status, builder));
    }
    
    private String extractQueryText(JsonNode queryNode) {
        if (queryNode == null) return null;
        
        logger.info("Extracting query text from: {}", queryNode.toString());
        
        // Handle match query
        if (queryNode.has("match")) {
            JsonNode matchNode = queryNode.get("match");
            if (matchNode.isObject()) {
                // Get first field value
                String fieldName = matchNode.fieldNames().next();
                JsonNode fieldValue = matchNode.get(fieldName);
                if (fieldValue.isTextual()) {
                    return fieldValue.asText();
                } else if (fieldValue.isObject() && fieldValue.has("query")) {
                    return fieldValue.get("query").asText();
                }
            }
        }
        
        // Handle multi_match query
        if (queryNode.has("multi_match") && queryNode.get("multi_match").has("query")) {
            return queryNode.get("multi_match").get("query").asText();
        }
        
        // Handle term query
        if (queryNode.has("term")) {
            JsonNode termNode = queryNode.get("term");
            if (termNode.isObject()) {
                String fieldName = termNode.fieldNames().next();
                return termNode.get(fieldName).asText();
            }
        }
        
        // Handle query_string query
        if (queryNode.has("query_string") && queryNode.get("query_string").has("query")) {
            return queryNode.get("query_string").get("query").asText();
        }
        
        return null;
    }
    
    private List<Float> generateEmbedding(String queryText, RestRequest request) throws Exception {
        logger.info("Generating embedding for query text");
        
        // Get API key from Authorization header first, then fallback to environment/settings
        String apiKey = request.header("Authorization");
        if (apiKey != null && apiKey.startsWith("Bearer ")) {
            apiKey = apiKey.substring(7); // Remove "Bearer " prefix
        } else {
            // Fallback to environment variable
            apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null) {
                // Try to get from cluster settings
                apiKey = settings.get("semantic_search.openai.api_key");
            }
        }
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("API key not found. Please provide it via Authorization header (Bearer token), OPENAI_API_KEY environment variable, or semantic_search.openai.api_key setting");
        }
        
        Map<String, Object> config = new HashMap<>();
        config.put(PluginConstants.CONFIG_API_URL, PluginConstants.DEFAULT_API_URL);
        config.put(PluginConstants.CONFIG_MODEL, PluginConstants.DEFAULT_MODEL);
        config.put(PluginConstants.CONFIG_PROVIDER, PluginConstants.PROVIDER_TYPE_OPENAI);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        config.put(PluginConstants.CONFIG_HEADERS, headers);
        
        EmbeddingProvider provider = ProviderFactory.create(config);
        ProviderRequest providerRequest = new ProviderRequest(queryText);
        ProviderResponse response = provider.embed(Collections.singletonList(providerRequest));
        
        return response.getVectors().get(0);
    }
    
    private ObjectNode createScriptScoreQuery(List<Float> queryVector, String vectorField, double boost) {
        ObjectNode scriptScoreQuery = MAPPER.createObjectNode();
        ObjectNode scriptScore = MAPPER.createObjectNode();
        ObjectNode query = MAPPER.createObjectNode();
        ObjectNode script = MAPPER.createObjectNode();
        ObjectNode params = MAPPER.createObjectNode();
        
        // Build script_score query
        query.set("match_all", MAPPER.createObjectNode());
        script.put("source", String.format("cosineSimilarity(params.query_vector, '%s') * %f + 1.0", vectorField, boost));
        
        // Manually create the array node to avoid Jackson serialization issues
        com.fasterxml.jackson.databind.node.ArrayNode vectorArray = MAPPER.createArrayNode();
        for (Float value : queryVector) {
            vectorArray.add(value);
        }
        params.set("query_vector", vectorArray);
        script.set("params", params);
        
        scriptScore.set("query", query);
        scriptScore.set("script", script);
        scriptScoreQuery.set("script_score", scriptScore);
        
        return scriptScoreQuery;
    }
}
