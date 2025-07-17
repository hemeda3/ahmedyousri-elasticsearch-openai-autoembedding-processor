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

```bash
curl -X POST "localhost:9200/cases/_search" -H 'Content-Type: application/json' -d'
{
  "knn": {
    "field": "full_case_text_vector",
    "query_vector": [0.1, 0.2, 0.3],
    "k": 10
  }
}'
