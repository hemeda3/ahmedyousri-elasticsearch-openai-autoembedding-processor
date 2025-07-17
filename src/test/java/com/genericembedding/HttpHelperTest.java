package com.genericembedding;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class HttpHelperTest {

    @Test
    public void testHttpHelperClassLoading() {
        // This should trigger the static initialization of HttpHelper
        // which is where the Jackson ObjectMapper is created at line 23
        Map<String, Object> config = new HashMap<>();
        config.put("api_url", "https://api.openai.com/v1/embeddings");
        config.put("model", "text-embedding-3-small");
        
        HttpHelper helper = new HttpHelper(config);
        assertNotNull("HttpHelper should be instantiable", helper);
    }

    @Test
    public void testJacksonAvailability() {
        try {
            // Try to directly instantiate ObjectMapper to test Jackson availability
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            assertNotNull("ObjectMapper should be available", mapper);
        } catch (NoClassDefFoundError e) {
            fail("Jackson ObjectMapper class not found in classpath: " + e.getMessage());
        }
    }

    @Test
    public void testStaticObjectMapperInitialization() {
        try {
            // This will trigger the static initialization block where ObjectMapper is created
            Class.forName("com.genericembedding.HttpHelper");
            assertTrue("HttpHelper class should load without NoClassDefFoundError", true);
        } catch (NoClassDefFoundError e) {
            fail("Static ObjectMapper initialization failed: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            fail("HttpHelper class not found: " + e.getMessage());
        }
    }
}
