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
      "text_field_vector": {"type": "dense_vector", "dims": 1536}
    }
  }
}
```

### 2. Create Embedding Pipeline

```bash
PUT /_ingest/pipeline/embedding_pipeline
{
  "processors": [
    {
      "ai_embed": {
        "source_fields": ["text_field"],
        "headers": {
          "Authorization": "Bearer YOUR_OPENAI_API_KEY"
        }
      }
    }
  ]
}
```

### 3. Index Documents

```bash
POST /my_index/_doc?pipeline=embedding_pipeline
{
  "text_field": "Your document content here"
}
```

## Search Endpoints

### Semantic Search
```bash
POST /my_index/_semantic_search
{
  "query": {"match": {"text_field": "search query"}},
  "semantic_search": {
    "enabled": true,
    "field": "text_field_vector",
    "boost": 2.0
  },
  "size": 10
}
```

### Hybrid Search
```bash
POST /my_index/_hybrid_search
{
  "query": {"match": {"text_field": "search query"}},
  "semantic_search": {
    "enabled": true,
    "field": "text_field_vector",
    "boost": 2.0,
    "top_k": 20
  },
  "size": 10
}
```

## Configuration

### Processor Parameters
- `source_fields`: Fields to embed (required)
- `provider`: "openai" or "generic" (default: "generic")
- `api_url`: API endpoint (default: OpenAI)
- `model`: Embedding model (default: "text-embedding-3-small")
- `headers`: HTTP headers including API key

### Search Parameters
- `field`: Vector field name
- `boost`: Score multiplier
- `top_k`: Results per search type (hybrid only)

### Response Filtering
```bash
{
  "_source": {
    "excludes": ["*_vector", "embedding_usage"]
  }
}
```

## Response Format

### Hybrid Search Response
```json
{
  "hits": {
    "hits": [
      {
        "_id": "doc1",
        "_score": 3.2,
        "_search_type": "hybrid",
        "_regular_score": 1.5,
        "_semantic_score": 1.7,
        "_source": {...}
      }
    ]
  },
  "search_breakdown": {
    "regular_hits": 20,
    "semantic_hits": 20,
    "hybrid_matches": 3
  }
}
```

### Search Types
- `"hybrid"`: Found in both keyword and semantic search
- `"regular"`: Found only in keyword search  
- `"semantic"`: Found only in semantic search

## Requirements

- Elasticsearch 8.13+
- OpenAI API key
- Java 17+
- Maven 3.6+
