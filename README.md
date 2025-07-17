````markdown
# ahmedyousri-elasticsearch-openai-autoembedding-processor

An Elasticsearch ingest processor plugin that automatically generates OpenAI-compatible dense vector embeddings for selected fields during document ingestion. Perfect for semantic search over legal, Arabic, or multilingual data.

---

## ✨ Features

- 🔌 **Elasticsearch Ingest Processor**: Named `openai_embed`
- 🧠 **Per-field embedding**: Auto-generates one embedding per field (e.g. `case_identifier`, `summary`)
- 🧾 **Stores vectors in** `dense_vector` fields with names like `{field}_vector`
- ⚙️ **Configurable**:
  - `api_key`: OpenAI or compatible API key
  - `api_url`: Defaults to `https://api.openai.com/v1/embeddings`
  - `model`: e.g. `text-embedding-3-small`
- ⚡ **No need for custom mapping logic** – plugin auto-names output fields

---

## 🛠 Example Ingest Pipeline

```json
PUT _ingest/pipeline/auto_embed_pipeline
{
  "processors": [
    {
      "openai_embed": {
        "source_fields": ["case_identifier", "full_case_text", "summary"],
        "api_key": "{{OPENAI_API_KEY}}",
        "model": "text-embedding-3-small"
      }
    }
  ]
}
```

---

## 📄 Example Document

```json
POST /legal_cases/_doc?pipeline=auto_embed_pipeline
{
  "case_identifier": "17-12-2018 - رقم الطعن 708",
  "full_case_text": "تفاصيل الحكم الكامل...",
  "summary": "جريمة قتل في ظروف مشددة"
}
```

After processing, Elasticsearch will store:

```json
{
  "case_identifier_vector": [...],
  "full_case_text_vector": [...],
  "summary_vector": [...]
}
```

---

## 🔍 Vector Search Example

```json
POST /legal_cases/_search
{
  "query": {
    "script_score": {
      "query": { "match_all": {} },
      "script": {
        "source": "cosineSimilarity(params.query_vector, 'summary_vector') + 1.0",
        "params": {
          "query_vector": [0.123, 0.456, 0.789, ...]
        }
      }
    }
  }
}
```

---

## 📐 Index Mapping Example

```json
PUT /legal_cases
{
  "mappings": {
    "properties": {
      "case_identifier": { "type": "text" },
      "full_case_text": { "type": "text" },
      "summary": { "type": "text" },
      "case_identifier_vector": {
        "type": "dense_vector",
        "dims": 1536,
        "index": true,
        "similarity": "cosine"
      },
      "full_case_text_vector": {
        "type": "dense_vector",
        "dims": 1536,
        "index": true,
        "similarity": "cosine"
      },
      "summary_vector": {
        "type": "dense_vector",
        "dims": 1536,
        "index": true,
        "similarity": "cosine"
      }
    }
  }
}
```

---

## ⚙️ Build & Install

1. Build the plugin with Maven:

```bash
mvn package
```

2. Install it into Elasticsearch:

```bash
elasticsearch-plugin install file:///path/to/ahmedyousri-elasticsearch-openai-autoembedding-processor.zip
```

3. Restart Elasticsearch:

```bash
systemctl restart elasticsearch
# or
docker restart elasticsearch-container
```

---

## 📋 Notes

- `dense_vector` field dimensions must match the model (e.g. `1536` for `text-embedding-3-small`)
- All vector output fields are named `{source_field}_vector` by default
- Plugin supports OpenAI-compatible APIs (like Azure OpenAI or self-hosted models)

---

## 🧪 Sample Test Data

```json
[
  {
    "title": "The Importance of Sleep",
    "content": "Sleep improves memory, mood, and immune function."
  },
  {
    "title": "Baking Cookies",
    "content": "Mix ingredients, bake at 350°F, enjoy delicious cookies."
  },
  {
    "title": "Exploring the Solar System",
    "content": "The solar system contains the sun and eight planets."
  }
]
```

Use this with source fields `["title", "content"]` and test search on `title_vector` or `content_vector`.

---

## 📖 License

MIT (or Apache 2.0 — your choice)

---
````

Let me know if you want it saved to a `.md` file or zipped with your plugin.
