package com.genericembedding;

import com.genericembedding.providers.EmbeddingProvider;
import com.genericembedding.providers.ProviderRequest;
import com.genericembedding.providers.ProviderResponse;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SimpleProcessorTest {

    private EmbeddingProvider mockProvider;
    private AIEmbedProcessor processor;

    @Before
    public void setUp() {
        mockProvider = mock(EmbeddingProvider.class);
        processor = new AIEmbedProcessor("test-tag", "test-description", 
            Arrays.asList("title", "content"), mockProvider);
    }

    @Test
    public void testProcessorType() {
        assertEquals(PluginConstants.PROCESSOR_TYPE, processor.getType());
    }

    @Test
    public void testProviderInteraction() throws Exception {
        List<List<Float>> mockVectors = Arrays.asList(
            Arrays.asList(0.1f, 0.2f, 0.3f),
            Arrays.asList(0.4f, 0.5f, 0.6f)
        );
        ProviderResponse mockResponse = new ProviderResponse(mockVectors);

        when(mockProvider.embed(any())).thenReturn(mockResponse);

        List<ProviderRequest> requests = Arrays.asList(
            new ProviderRequest("Test Title"),
            new ProviderRequest("Test Content")
        );

        ProviderResponse result = mockProvider.embed(requests);

        verify(mockProvider, times(1)).embed(any());
        assertNotNull(result);
        assertEquals(2, result.getVectors().size());
        assertEquals(Arrays.asList(0.1f, 0.2f, 0.3f), result.getVectors().get(0));
        assertEquals(Arrays.asList(0.4f, 0.5f, 0.6f), result.getVectors().get(1));
    }

    @Test
    public void testProviderException() throws Exception {
        when(mockProvider.embed(any())).thenThrow(new RuntimeException("API Error"));

        try {
            List<ProviderRequest> requests = Arrays.asList(
                new ProviderRequest("Test Title")
            );
            mockProvider.embed(requests);
            fail("Expected exception was not thrown");
        } catch (RuntimeException e) {
            assertEquals("API Error", e.getMessage());
        }

        verify(mockProvider, times(1)).embed(any());
    }

    @Test
    public void testProviderRequestCreation() {
        ProviderRequest request = new ProviderRequest("Test text");
        assertEquals("Test text", request.getText());
    }

    @Test
    public void testProviderResponseCreation() {
        List<List<Float>> vectors = Arrays.asList(
            Arrays.asList(0.1f, 0.2f, 0.3f)
        );
        ProviderResponse response = new ProviderResponse(vectors);
        assertEquals(1, response.getVectors().size());
        assertEquals(Arrays.asList(0.1f, 0.2f, 0.3f), response.getVectors().get(0));
    }
}
