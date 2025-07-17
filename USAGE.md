# Quick Usage Guide

## 1. Create Index

```bash
curl -X PUT "localhost:9200/cases" -H 'Content-Type: application/json' -d'
{
  "mappings": {
    "properties": {
      "case_identifier": {
        "type": "text"
      },
      "html_filename": {
        "type": "keyword"
      },
      "full_case_text": {
        "type": "text"
      },
      "case_identifier_vector": {
        "type": "dense_vector",
        "dims": 1536
      },
      "full_case_text_vector": {
        "type": "dense_vector",
        "dims": 1536
      }
    }
  }
}'
```

{
    "acknowledged": true,
    "shards_acknowledged": true,
    "index": "cases"
}

## 2. Create Pipeline

```bash
curl -X PUT "localhost:9200/_ingest/pipeline/case_embedding_pipeline" -H 'Content-Type: application/json' -d'
{
  "processors": [
    {
      "ai_embed": {
        "source_fields": ["case_identifier", "full_case_text"],
        "headers": {
          "Authorization": "Bearer YOUR_OPENAI_KEY"
        }
      }
    }
  ]
}'
```

## 3. Index Document

```bash
curl -X POST "localhost:9200/cases/_doc?pipeline=case_embedding_pipeline" -H 'Content-Type: application/json' -d'
{
  "case_identifier": "CASE-2024-001",
  "html_filename": "3721.json",
  "full_case_text": "This is the full case text content..."
}'
```

## 4. Result

Document will have:
- `case_identifier_vector`: [0.1, 0.2, 0.3, ...]
- `full_case_text_vector`: [0.4, 0.5, 0.6, ...]

## 5. Bulk Index

```bash
curl -X POST "localhost:9200/_bulk" -H 'Content-Type: application/json' -d'
{"index": {"_index": "cases", "pipeline": "case_embedding_pipeline"}}
{"case_identifier": "CASE-001", "html_filename": "3721.json", "full_case_text": "Case text 1"}
{"index": {"_index": "cases", "pipeline": "case_embedding_pipeline"}}
{"case_identifier": "CASE-002", "html_filename": "3722.json", "full_case_text": "Case text 2"}
'
```

## 6. Search by Vector

First, get an embedding for your search query:

```bash
curl -X POST "https://api.openai.com/v1/embeddings" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "input": "search for similar cases",
    "model": "text-embedding-3-small"
  }'
```

Then use the returned 1536-dimensional vector for search:

```bash
curl -X POST "localhost:9200/cases/_search" -H 'Content-Type: application/json' -d'
{
  "knn": {
    "field": "full_case_text_vector",
    "query_vector": [0.1, 0.2, 0.3, ...1536 dimensions...],
    "k": 10
  }
}'
```

## 7. Verify Document Has Embeddings

```bash
curl -X GET "localhost:9200/cases/_search" -H 'Content-Type: application/json' -d'
{
  "query": {"match_all": {}},
  "size": 1
}'
```

You should see fields like:
- `case_identifier_vector`: Array of 1536 floats
- `full_case_text_vector`: Array of 1536 floats

## ✅ Success!

Your plugin is working correctly! The document was indexed with proper 1536-dimensional embeddings from OpenAI.
