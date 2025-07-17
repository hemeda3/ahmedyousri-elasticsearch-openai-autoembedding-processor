# ahmedyousri-elasticsearch-openai-autoembedding-processor

An Elasticsearch ingest processor plugin that automatically generates OpenAI-compatible dense embeddings from selected document fields during indexing — perfect for semantic search over legal, Arabic, or mixed-language text.

## ✨ Features

- 🔌 Ingest pipeline processor: `openai_embed`
- 📄 Supports multiple fields: `case_identifier`, `full_case_text`, etc.
- 📦 Sends each field to OpenAI Embedding API
- 🧠 Stores result in `dense_vector` fields like `case_identifier_vector`
- 🔧 Configurable:
  - API Key
  - API Endpoint
  - Model (`text-embedding-3-small`, etc.)
- ⚡ Auto-maps fields:  
  `case_identifier` → `case_identifier_vector`  
  `summary` → `summary_vector`  
  No need to manually map!

## 🛠 Example Ingest Pipeline

```json
PUT _ingest/pipeline/auto_embed_pipeline
{
  "processors": [
    {
      "openai_embed": {
        "source_fields": ["case_identifier", "full_case_text"],
        "api_key": "{{OPENAI_KEY}}",
        "model": "text-embedding-3-small"
      }
    }
  ]
}
