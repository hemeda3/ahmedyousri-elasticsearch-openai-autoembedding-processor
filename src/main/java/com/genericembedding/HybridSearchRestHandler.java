package com.genericembedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.elasticsearch.rest.RestRequest.Method.POST;

public class HybridSearchRestHandler implements RestHandler {
    
    private static final Logger logger = LogManager.getLogger(HybridSearchRestHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Settings settings;

    public HybridSearchRestHandler(Settings settings) {
        this.settings = settings;
    }

    @Override
    public List<Route> routes() {
        return List.of(
            new Route(POST, "/{index}/_hybrid_search")
        );
    }

    @Override
    public void handleRequest(RestRequest request, RestChannel channel, NodeClient client) throws Exception {
        logger.info("=== HYBRID SEARCH REQUEST RECEIVED ===");
        
        String index = request.param("index");
        logger.info("Target index: {}", index);
        
        // Parse the request body
        String requestBody = null;
        if (request.hasContent()) {
            requestBody = request.content().utf8ToString();
        }
        
        if (requestBody == null || requestBody.trim().isEmpty()) {
            sendErrorResponse(channel, "Request body is required for hybrid search", RestStatus.BAD_REQUEST);
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
            int topK = semanticSearch.has("top_k") ? 
                semanticSearch.get("top_k").asInt() : 
                (requestJson.has("size") ? requestJson.get("size").asInt() : 10);
            
            logger.info("Hybrid search parameters - field: {}, boost: {}, top_k: {}", vectorField, boost, topK);
            
            // Extract query text from the regular query
            String queryText = extractQueryText(requestJson.get("query"));
            if (queryText == null || queryText.isEmpty()) {
                sendErrorResponse(channel, "Could not extract query text from query section", RestStatus.BAD_REQUEST);
                return;
            }
            
            logger.info("Extracted query text: {}", queryText);
            
            // Perform both searches concurrently
            CompletableFuture<SearchResponse> regularSearchFuture = performRegularSearch(client, index, requestJson, topK);
            CompletableFuture<SearchResponse> semanticSearchFuture = performSemanticSearch(client, index, queryText, vectorField, boost, topK, request);
            
            // Wait for both searches to complete
            CompletableFuture.allOf(regularSearchFuture, semanticSearchFuture).thenAccept(v -> {
                try {
                    SearchResponse regularResponse = regularSearchFuture.get();
                    SearchResponse semanticResponse = semanticSearchFuture.get();
                    
                    // Combine results
                    List<SearchHit> combinedHits = combineSearchResults(regularResponse, semanticResponse, topK);
                    
                    // Build hybrid response
                    XContentBuilder builder = XContentFactory.jsonBuilder();
                    buildHybridResponse(builder, combinedHits, regularResponse, semanticResponse);
                    
                    channel.sendResponse(new org.elasticsearch.rest.RestResponse(RestStatus.OK, builder));
                    
                } catch (Exception e) {
                    logger.error("Error processing hybrid search results", e);
                    try {
                        sendErrorResponse(channel, "Error processing hybrid search results: " + e.getMessage(), RestStatus.INTERNAL_SERVER_ERROR);
                    } catch (IOException ioException) {
                        logger.error("Failed to send error response", ioException);
                    }
                }
            }).exceptionally(throwable -> {
                logger.error("Hybrid search failed", throwable);
                try {
                    sendErrorResponse(channel, "Hybrid search failed: " + throwable.getMessage(), RestStatus.INTERNAL_SERVER_ERROR);
                } catch (IOException e) {
                    logger.error("Failed to send error response", e);
                }
                return null;
            });
            
        } catch (Exception e) {
            logger.error("Error processing hybrid search: {}", e.getMessage(), e);
            sendErrorResponse(channel, "Error processing hybrid search: " + e.getMessage(), RestStatus.BAD_REQUEST);
        }
    }
    
    private CompletableFuture<SearchResponse> performRegularSearch(NodeClient client, String index, JsonNode requestJson, int topK) {
        CompletableFuture<SearchResponse> future = new CompletableFuture<>();
        
        try {
            // Create regular search request without semantic_search section
            ObjectNode regularRequest = MAPPER.createObjectNode();
            requestJson.fields().forEachRemaining(entry -> {
                if (!"semantic_search".equals(entry.getKey())) {
                    regularRequest.set(entry.getKey(), entry.getValue());
                }
            });
            
            // Override size to topK
            regularRequest.put("size", topK);
            
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            
            // Use wrapper query for the regular query
            String queryJson = MAPPER.writeValueAsString(regularRequest.get("query"));
            sourceBuilder.query(org.elasticsearch.index.query.QueryBuilders.wrapperQuery(queryJson));
            sourceBuilder.size(topK);
            
            // Add other parameters
            if (regularRequest.has("from")) {
                sourceBuilder.from(regularRequest.get("from").asInt());
            }
            if (regularRequest.has("_source")) {
                JsonNode sourceNode = regularRequest.get("_source");
                if (sourceNode.isTextual()) {
                    sourceBuilder.fetchSource(sourceNode.asText(), null);
                } else if (sourceNode.isArray()) {
                    String[] includes = new String[sourceNode.size()];
                    for (int i = 0; i < sourceNode.size(); i++) {
                        includes[i] = sourceNode.get(i).asText();
                    }
                    sourceBuilder.fetchSource(includes, null);
                } else if (sourceNode.isObject()) {
                    // Handle _source object with includes/excludes
                    JsonNode includesNode = sourceNode.get("includes");
                    JsonNode excludesNode = sourceNode.get("excludes");
                    
                    String[] includes = null;
                    String[] excludes = null;
                    
                    if (includesNode != null && includesNode.isArray()) {
                        includes = new String[includesNode.size()];
                        for (int i = 0; i < includesNode.size(); i++) {
                            includes[i] = includesNode.get(i).asText();
                        }
                    }
                    
                    if (excludesNode != null && excludesNode.isArray()) {
                        excludes = new String[excludesNode.size()];
                        for (int i = 0; i < excludesNode.size(); i++) {
                            excludes[i] = excludesNode.get(i).asText();
                        }
                    }
                    
                    sourceBuilder.fetchSource(includes, excludes);
                }
            } else {
                // Exclude vector fields by default to avoid large payloads
                sourceBuilder.fetchSource(null, new String[]{"*_vector", "embedding_usage", "embedding_error"});
            }
            
            searchRequest.source(sourceBuilder);
            
            logger.info("Executing regular search");
            client.search(searchRequest, new org.elasticsearch.action.ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    logger.info("Regular search completed with {} hits", searchResponse.getHits().getTotalHits().value);
                    future.complete(searchResponse);
                }
                
                @Override
                public void onFailure(Exception e) {
                    logger.error("Regular search failed", e);
                    future.completeExceptionally(e);
                }
            });
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    private CompletableFuture<SearchResponse> performSemanticSearch(NodeClient client, String index, String queryText, String vectorField, double boost, int topK, RestRequest request) {
        CompletableFuture<SearchResponse> future = new CompletableFuture<>();
        
        try {
            // Generate embedding for the query
            List<Float> queryVector = generateEmbedding(queryText, request);
            logger.info("Generated query vector with {} dimensions for semantic search", queryVector.size());
            
            // Create script_score query
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
            fullQuery.set("script_score", scriptScoreJson);
            
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            
            String scriptScoreQueryString = MAPPER.writeValueAsString(fullQuery);
            sourceBuilder.query(org.elasticsearch.index.query.QueryBuilders.wrapperQuery(scriptScoreQueryString));
            sourceBuilder.size(topK);
            
            // Exclude vector fields from response to avoid large payloads
            sourceBuilder.fetchSource(null, new String[]{vectorField, "embedding_usage", "embedding_error"});
            
            searchRequest.source(sourceBuilder);
            
            logger.info("Executing semantic search");
            client.search(searchRequest, new org.elasticsearch.action.ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse searchResponse) {
                    logger.info("Semantic search completed with {} hits", searchResponse.getHits().getTotalHits().value);
                    future.complete(searchResponse);
                }
                
                @Override
                public void onFailure(Exception e) {
                    logger.error("Semantic search failed", e);
                    future.completeExceptionally(e);
                }
            });
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    private List<SearchHit> combineSearchResults(SearchResponse regularResponse, SearchResponse semanticResponse, int topK) {
        logger.info("Combining search results - regular: {}, semantic: {}", 
                   regularResponse.getHits().getHits().length, 
                   semanticResponse.getHits().getHits().length);
        
        Map<String, SearchHit> combinedHits = new LinkedHashMap<>();
        
        // Add regular search results first (higher priority)
        for (SearchHit hit : regularResponse.getHits().getHits()) {
            hit.getSourceAsMap().put("search_type", "regular");
            hit.getSourceAsMap().put("regular_score", hit.getScore());
            combinedHits.put(hit.getId(), hit);
        }
        
        // Add semantic search results, merging if already exists
        for (SearchHit hit : semanticResponse.getHits().getHits()) {
            String id = hit.getId();
            if (combinedHits.containsKey(id)) {
                // Merge scores for documents found in both searches
                SearchHit existingHit = combinedHits.get(id);
                existingHit.getSourceAsMap().put("search_type", "hybrid");
                existingHit.getSourceAsMap().put("semantic_score", hit.getScore());
                existingHit.getSourceAsMap().put("combined_score", existingHit.getScore() + hit.getScore());
                // Update the hit score to combined score
                existingHit.score(existingHit.getScore() + hit.getScore());
            } else {
                // Add semantic-only results
                hit.getSourceAsMap().put("search_type", "semantic");
                hit.getSourceAsMap().put("semantic_score", hit.getScore());
                combinedHits.put(id, hit);
            }
        }
        
        // Sort by combined score and return top K
        List<SearchHit> sortedHits = new ArrayList<>(combinedHits.values());
        sortedHits.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        
        return sortedHits.subList(0, Math.min(topK, sortedHits.size()));
    }
    
    private void buildHybridResponse(XContentBuilder builder, List<SearchHit> combinedHits, SearchResponse regularResponse, SearchResponse semanticResponse) throws IOException {
        builder.startObject();
        
        // Response metadata
        builder.field("took", Math.max(regularResponse.getTook().millis(), semanticResponse.getTook().millis()));
        builder.field("timed_out", regularResponse.isTimedOut() || semanticResponse.isTimedOut());
        
        // Shards info
        builder.startObject("_shards");
        builder.field("total", regularResponse.getTotalShards());
        builder.field("successful", Math.min(regularResponse.getSuccessfulShards(), semanticResponse.getSuccessfulShards()));
        builder.field("skipped", Math.max(regularResponse.getSkippedShards(), semanticResponse.getSkippedShards()));
        builder.field("failed", Math.max(regularResponse.getFailedShards(), semanticResponse.getFailedShards()));
        builder.endObject();
        
        // Hits
        builder.startObject("hits");
        builder.field("total", Map.of("value", combinedHits.size(), "relation", "eq"));
        builder.field("max_score", combinedHits.isEmpty() ? null : combinedHits.get(0).getScore());
        
        builder.startArray("hits");
        for (SearchHit hit : combinedHits) {
            builder.startObject();
            builder.field("_index", hit.getIndex());
            builder.field("_id", hit.getId());
            builder.field("_score", hit.getScore());
            builder.field("_source", hit.getSourceAsMap());
            builder.endObject();
        }
        builder.endArray();
        builder.endObject();
        
        // Search type breakdown
        builder.startObject("search_breakdown");
        builder.field("regular_hits", regularResponse.getHits().getHits().length);
        builder.field("semantic_hits", semanticResponse.getHits().getHits().length);
        builder.field("combined_hits", combinedHits.size());
        builder.field("hybrid_matches", (int) combinedHits.stream().filter(hit -> "hybrid".equals(hit.getSourceAsMap().get("search_type"))).count());
        builder.endObject();
        
        builder.endObject();
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
}
