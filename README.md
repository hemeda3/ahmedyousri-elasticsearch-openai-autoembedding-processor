# Elasticsearch OpenAI Auto-Embedding Plugin

Elasticsearch plugin for automatic document embedding with OpenAI API and semantic/hybrid search capabilities.

## Installation

```bash
# Build plugin
mvn clean package

# Install in Elasticsearch
bin/elasticsearch-plugin install file:///path/to/target/elasticsearch-openai-autoembedding-processor-1.0.0.zip

# Restart Elasticsearch
```

## Quick Setup

### 1. Create Index with Vector Mapping

```bash
PUT /my_index
{
  "mappings": {
    "properties": {
      "text_field": {"type": "text"},
      "case_identifier": {"type": "text"},
      "html_filename": {"type": "keyword"},
      "objectID": {"type": "keyword"},
      "text_field_vector": {"type": "dense_vector", "dims": 1536},
      "case_identifier_vector": {"type": "dense_vector", "dims": 1536}
    }
  }
}
```

### 2. Create Embedding Pipeline

```bash
PUT /_ingest/pipeline/embedding_pipeline
{
  "description": "Generate embeddings for documents",
  "processors": [
    {
      "ai_embed": {
        "source_fields": ["text_field", "case_identifier"],
        "provider": "openai",
        "api_url": "https://api.openai.com/v1/embeddings",
        "model": "text-embedding-3-small",
        "headers": {
          "Authorization": "Bearer YOUR_OPENAI_API_KEY"
        },
        "connect_timeout": "10s",
        "read_timeout": "30s",
        "max_requests_per_second": 10,
        "backoff_initial_delay_ms": 1000,
        "backoff_max_delay_ms": 30000,
        "backoff_multiplier": 2.0
      }
    }
  ]
}
```

### 3. Index Documents

```bash
POST /my_index/_doc?pipeline=embedding_pipeline
{
  "text_field": "Your document content here",
  "case_identifier": "Case-123",
  "html_filename": "document.html",
  "objectID": "obj_001"
}
```

## REST Handlers

### 1. Semantic Search Handler

**Endpoint**: `POST /{index}/_semantic_search`

Pure vector similarity search using OpenAI embeddings.

```bash
POST /my_index/_semantic_search
{
  "query": {
    "match": {"text_field": "contract dispute"}
  },
  "semantic_search": {
    "enabled": true,
    "field": "text_field_vector",
    "boost": 2.0
  },
  "size": 10,
  "from": 0,
  "_source": {
    "excludes": ["*_vector", "embedding_usage"]
  }
}
```

### 2. Hybrid Search Handler

**Endpoint**: `POST /{index}/_hybrid_search`

Combined keyword and semantic search with result merging.

```bash
POST /my_index/_hybrid_search
{
  "query": {
    "match": {"text_field": "contract dispute"}
  },
  "semantic_search": {
    "enabled": true,
    "field": "text_field_vector",
    "boost": 2.0,
    "top_k": 20
  },
  "size": 10,
  "from": 0,
  "_source": {
    "includes": ["text_field", "case_identifier"],
    "excludes": ["*_vector"]
  }
}
```

## Configuration Options

### AI Embed Processor Parameters

#### Required Parameters
- **`source_fields`** (array/string): Fields to generate embeddings for
  ```json
  "source_fields": ["field1", "field2"]
  // or
  "source_fields": "single_field"
  ```

#### Provider Configuration
- **`provider`** (string): Provider type
  - `"openai"` - OpenAI API (default settings)
  - `"generic"` - Custom API endpoint
  - Default: `"generic"`

- **`api_url`** (string): API endpoint URL
  - Default: `"https://api.openai.com/v1/embeddings"`

- **`model`** (string): Embedding model name
  - Default: `"text-embedding-3-small"`
  - Options: `"text-embedding-3-small"`, `"text-embedding-3-large"`, `"text-embedding-ada-002"`

#### Authentication
- **`headers`** (object): HTTP headers for API requests
  ```json
  "headers": {
    "Authorization": "Bearer sk-...",
    "Custom-Header": "value"
  }
  ```

#### Timeout Configuration
- **`connect_timeout`** (string): Connection timeout
  - Format: `"5s"`, `"30s"`, `"1m"`
  - Default: `"5s"`

- **`read_timeout`** (string): Read timeout
  - Format: `"10s"`, `"60s"`, `"2m"`
  - Default: `"10s"`

#### Rate Limiting
- **`max_requests_per_second`** (integer): Request rate limit
  - Default: `10`
  - Set to `0` to disable rate limiting

#### Backoff Strategy
- **`backoff_initial_delay_ms`** (long): Initial retry delay in milliseconds
  - Default: `1000`

- **`backoff_max_delay_ms`** (long): Maximum retry delay in milliseconds
  - Default: `30000`

- **`backoff_multiplier`** (double): Delay multiplier for exponential backoff
  - Default: `2.0`

#### Custom Request Template
- **`request_template`** (string): Custom JSON request template
  - Default: `"{\"input\": \"{{text}}\", \"model\": \"{{model}}\"}"`
  - Variables: `{{text}}`, `{{model}}`

- **`response_path`** (string): JSON path to extract embeddings
  - Default: `"data.0.embedding"`

### Search Handler Parameters

#### Semantic Search Configuration
- **`enabled`** (boolean): Enable semantic search
  - Required: `true`

- **`field`** (string): Vector field name
  - Default: `"full_case_text_vector"`

- **`boost`** (double): Score multiplier
  - Default: `1.0`

#### Hybrid Search Additional Parameters
- **`top_k`** (integer): Results per search type
  - Default: Same as `size` parameter
  - Controls how many results each search returns before merging

### Source Filtering Options

#### Simple String
```json
"_source": "field_name"
```

#### Array of Fields
```json
"_source": ["field1", "field2", "field3"]
```

#### Object with Includes/Excludes
```json
"_source": {
  "includes": ["text_field", "case_identifier"],
  "excludes": ["*_vector", "embedding_usage", "embedding_error"]
}
```

#### Disable Source
```json
"_source": false
```

## Response Formats

### Semantic Search Response
```json
{
  "took": 45,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {"value": 100, "relation": "eq"},
    "max_score": 2.5,
    "hits": [
      {
        "_index": "my_index",
        "_id": "doc1",
        "_score": 2.5,
        "_source": {
          "text_field": "Contract dispute resolution",
          "case_identifier": "Case-123"
        }
      }
    ]
  }
}
```

### Hybrid Search Response
```json
{
  "took": 67,
  "timed_out": false,
  "_shards": {
    "total": 1,
    "successful": 1,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {"value": 15, "relation": "eq"},
    "max_score": 4.2,
    "hits": [
      {
        "_index": "my_index",
        "_id": "doc1",
        "_score": 4.2,
        "_search_type": "hybrid",
        "_regular_score": 2.1,
        "_semantic_score": 2.1,
        "_combined_score": 4.2,
        "_source": {
          "text_field": "Contract dispute resolution",
          "case_identifier": "Case-123",
          "search_type": "hybrid"
        }
      },
      {
        "_index": "my_index",
        "_id": "doc2",
        "_score": 1.8,
        "_search_type": "semantic",
        "_semantic_score": 1.8,
        "_source": {
          "text_field": "Legal agreement terms",
          "case_identifier": "Case-456",
          "search_type": "semantic"
        }
      }
    ]
  },
  "search_breakdown": {
    "regular_hits": 20,
    "semantic_hits": 20,
    "combined_hits": 15,
    "hybrid_matches": 8
  }
}
```

### Search Type Metadata

#### `_search_type` Values
- **`"hybrid"`**: Document found in both keyword and semantic search
- **`"regular"`**: Document found only in keyword search
- **`"semantic"`**: Document found only in semantic search

#### Score Fields
- **`_score`**: Final combined score used for ranking
- **`_regular_score`**: Score from keyword search (if applicable)
- **`_semantic_score`**: Score from vector search (if applicable)
- **`_combined_score`**: Sum of regular and semantic scores (for hybrid results)

## Error Handling

### Common Error Responses

#### Missing Vector Field
```json
{
  "error": "Vector field not found in index mapping. Please ensure the index has vector fields created by the embedding pipeline. No field found for [text_field_vector] in mapping",
  "status": 400
}
```

#### API Key Missing
```json
{
  "error": "API key not found. Please provide it via Authorization header (Bearer token), OPENAI_API_KEY environment variable, or semantic_search.openai.api_key setting",
  "status": 400
}
```

#### Invalid Request Body
```json
{
  "error": "Request body is required for semantic search",
  "status": 400
}
```

#### Semantic Search Disabled
```json
{
  "error": "semantic_search.enabled must be true",
  "status": 400
}
```

#### Network/API Errors
```json
{
  "error": "API request failed with code 429: Rate limit exceeded",
  "status": 500
}
```

### Exception Types

#### Security Exceptions
- **AccessControlException**: Network permission denied
- **Solution**: Ensure plugin security policy allows OpenAI API access

#### Mapping Exceptions
- **IllegalArgumentException**: Vector field not found in mapping
- **Solution**: Create proper index mapping with dense_vector fields

#### API Exceptions
- **IOException**: Network connectivity issues
- **RuntimeException**: API authentication failures

#### Processing Exceptions
- **IllegalStateException**: Mismatch between embeddings and fields
- **NullPointerException**: Missing required configuration

## Advanced Configuration

### Custom API Provider
```json
{
  "ai_embed": {
    "source_fields": ["text"],
    "provider": "generic",
    "api_url": "https://custom-api.com/embeddings",
    "model": "custom-model",
    "request_template": "{\"text\": \"{{text}}\", \"model\": \"{{model}}\", \"format\": \"float\"}",
    "response_path": "embeddings.0.values",
    "headers": {
      "Authorization": "Bearer custom-token",
      "Content-Type": "application/json"
    }
  }
}
```

### Multiple Field Processing
```json
{
  "ai_embed": {
    "source_fields": ["title", "content", "summary"],
    "provider": "openai",
    "headers": {
      "Authorization": "Bearer sk-..."
    }
  }
}
```

### Performance Tuning
```json
{
  "ai_embed": {
    "source_fields": ["content"],
    "max_requests_per_second": 50,
    "connect_timeout": "5s",
    "read_timeout": "15s",
    "backoff_initial_delay_ms": 500,
    "backoff_max_delay_ms": 10000,
    "backoff_multiplier": 1.5
  }
}
```

## API Authentication Methods

### 1. Authorization Header (Recommended)
```bash
curl -X POST "localhost:9200/my_index/_semantic_search" \
  -H "Authorization: Bearer sk-proj-..." \
  -H "Content-Type: application/json" \
  -d '{"query": {...}, "semantic_search": {...}}'
```

### 2. Environment Variable
```bash
export OPENAI_API_KEY="sk-proj-..."
```

### 3. Cluster Settings
```bash
PUT /_cluster/settings
{
  "persistent": {
    "semantic_search.openai.api_key": "sk-proj-..."
  }
}
```

## Query Support

### Supported Query Types
- **match**: `{"match": {"field": "text"}}`
- **multi_match**: `{"multi_match": {"query": "text", "fields": ["field1", "field2"]}}`
- **term**: `{"term": {"field": "exact_value"}}`
- **query_string**: `{"query_string": {"query": "text"}}`

### Unsupported Queries
- Complex nested queries
- Aggregation queries
- Script queries

## Requirements

- **Elasticsearch**: 8.13+
- **Java**: 17+
- **Maven**: 3.6+
- **OpenAI API Key**: Required for embedding generation
- **Memory**: Minimum 2GB heap for vector operations
- **Network**: HTTPS access to api.openai.com:443

## Troubleshooting

### Plugin Installation Issues
```bash
# Check plugin status
bin/elasticsearch-plugin list

# Remove and reinstall
bin/elasticsearch-plugin remove elasticsearch-openai-autoembedding-processor
bin/elasticsearch-plugin install file:///path/to/plugin.zip
```

### Vector Field Issues
```bash
# Check index mapping
GET /my_index/_mapping

# Verify vector field exists
GET /my_index/_doc/1?_source_includes=*_vector
```

### API Connectivity
```bash
# Test API key
curl -H "Authorization: Bearer sk-..." https://api.openai.com/v1/models

# Check Elasticsearch logs
tail -f logs/elasticsearch.log | grep "genericembedding"
```

### Performance Issues
- Increase `max_requests_per_second` for higher throughput
- Adjust timeout values for slow networks
- Monitor embedding_usage field for token consumption
- Use smaller embedding models for faster processing
