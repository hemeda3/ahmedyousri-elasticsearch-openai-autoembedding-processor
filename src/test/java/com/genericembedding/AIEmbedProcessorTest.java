package com.genericembedding;

import com.genericembedding.providers.EmbeddingProvider;
import com.genericembedding.providers.ProviderRequest;
import com.genericembedding.providers.ProviderResponse;
import org.elasticsearch.ingest.IngestDocument;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AIEmbedProcessorTest {

    private EmbeddingProvider mockProvider;
    private AIEmbedProcessor processor;

    @Before
    public void setUp() {
        mockProvider = mock(EmbeddingProvider.class);
        processor = new AIEmbedProcessor("test-tag", "test-description", 
            Arrays.asList("title", "content"), mockProvider);
    }

    private Map<String, Object> createMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("_index", "test");
        metadata.put("_id", "1");
        metadata.put("_version", 1L);
        return metadata;
    }

    @Test
    public void testProcessorType() {
        assertEquals(PluginConstants.PROCESSOR_TYPE, processor.getType());
    }

    @Test
    public void testSuccessfulEmbedding() throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put("title", "Test Title");
        document.put("content", "Test Content");
        document.put("other_field", "Should not be processed");

        IngestDocument ingestDoc = new IngestDocument(document, createMetadata());

        List<List<Float>> mockVectors = Arrays.asList(
            Arrays.asList(0.1f, 0.2f, 0.3f),
            Arrays.asList(0.4f, 0.5f, 0.6f)
        );
        ProviderResponse mockResponse = new ProviderResponse(mockVectors);

        when(mockProvider.embed(any())).thenReturn(mockResponse);

        IngestDocument result = processor.execute(ingestDoc);

        verify(mockProvider, times(1)).embed(any());
        
        assertTrue(result.hasField("title_vector"));
        assertTrue(result.hasField("content_vector"));
        assertFalse(result.hasField("other_field_vector"));
        
        assertEquals(Arrays.asList(0.1f, 0.2f, 0.3f), result.getFieldValue("title_vector", List.class));
        assertEquals(Arrays.asList(0.4f, 0.5f, 0.6f), result.getFieldValue("content_vector", List.class));
    }

    @Test
    public void testMissingFields() throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put("title", "Test Title");

        IngestDocument ingestDoc = new IngestDocument(document, createMetadata());

        List<List<Float>> mockVectors = Arrays.asList(
            Arrays.asList(0.1f, 0.2f, 0.3f)
        );
        ProviderResponse mockResponse = new ProviderResponse(mockVectors);

        when(mockProvider.embed(any())).thenReturn(mockResponse);

        IngestDocument result = processor.execute(ingestDoc);

        verify(mockProvider, times(1)).embed(any());
        
        assertTrue(result.hasField("title_vector"));
        assertFalse(result.hasField("content_vector"));
    }

    @Test
    public void testEmptyFields() throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put("title", "");
        document.put("content", null);

        IngestDocument ingestDoc = new IngestDocument(document, createMetadata());

        IngestDocument result = processor.execute(ingestDoc);

        verify(mockProvider, never()).embed(any());
        
        assertFalse(result.hasField("title_vector"));
        assertFalse(result.hasField("content_vector"));
    }

    @Test
    public void testProviderException() throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put("title", "Test Title");

        IngestDocument ingestDoc = new IngestDocument(document, createMetadata());

        when(mockProvider.embed(any())).thenThrow(new RuntimeException("API Error"));

        IngestDocument result = processor.execute(ingestDoc);

        verify(mockProvider, times(1)).embed(any());
        
        assertFalse(result.hasField("title_vector"));
        assertTrue(result.hasField("embedding_error"));
        assertTrue(result.getFieldValue("embedding_error", String.class).contains("API Error"));
    }

    @Test
    public void testVectorCountMismatch() throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put("title", "Test Title");
        document.put("content", "Test Content");

        IngestDocument ingestDoc = new IngestDocument(document, createMetadata());

        List<List<Float>> mockVectors = Arrays.asList(
            Arrays.asList(0.1f, 0.2f, 0.3f)
        );
        ProviderResponse mockResponse = new ProviderResponse(mockVectors);

        when(mockProvider.embed(any())).thenReturn(mockResponse);

        IngestDocument result = processor.execute(ingestDoc);

        verify(mockProvider, times(1)).embed(any());
        
        assertFalse(result.hasField("title_vector"));
        assertFalse(result.hasField("content_vector"));
        assertTrue(result.hasField("embedding_error"));
        assertTrue(result.getFieldValue("embedding_error", String.class).contains("Number of returned embeddings does not match"));
    }
}
