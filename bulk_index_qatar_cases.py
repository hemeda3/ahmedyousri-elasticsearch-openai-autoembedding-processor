import json
import requests
import time
import os

# Configuration
JSON_FILE_PATH = '/Users/ayousri/projects/elasticsearchstuff/elasticsearch-search-stack-hhetzner/qatar_index/record_qatar_ahkam.json'
ELASTICSEARCH_URL = 'http://localhost:9200'
INDEX_NAME = 'qatar_cases'
PIPELINE_NAME = 'qatar_cases_pipeline'
OPENAI_API_KEY = 'YOUR_OPENAI_API_KEY_HERE'  # Replace with your actual API key
CHUNK_SIZE = 100  # Process 100 documents at a time

def create_pipeline():
    """Create the embedding pipeline"""
    pipeline_config = {
        "description": "Generate embeddings for Qatar legal cases",
        "processors": [
            {
                "ai_embed": {
                    "source_fields": ["full_case_text", "case_identifier"],
                    "provider": "openai",
                    "headers": {
                        "Authorization": f"Bearer {OPENAI_API_KEY}"
                    }
                }
            }
        ]
    }
    
    url = f"{ELASTICSEARCH_URL}/_ingest/pipeline/{PIPELINE_NAME}"
    response = requests.put(url, json=pipeline_config)
    
    if response.status_code == 200:
        print(f"✅ Pipeline '{PIPELINE_NAME}' created successfully")
    else:
        print(f"❌ Failed to create pipeline: {response.text}")
        return False
    return True

def load_json_file():
    """Load the JSON file"""
    print(f"📖 Loading JSON file: {JSON_FILE_PATH}")
    
    if not os.path.exists(JSON_FILE_PATH):
        print(f"❌ File not found: {JSON_FILE_PATH}")
        return None
    
    try:
        with open(JSON_FILE_PATH, 'r', encoding='utf-8') as f:
            cases = json.load(f)
        print(f"✅ Loaded {len(cases)} cases from JSON file")
        return cases
    except Exception as e:
        print(f"❌ Error loading JSON file: {e}")
        return None

def create_bulk_data(cases, start_idx, end_idx):
    """Create bulk data for a chunk of cases"""
    bulk_data = []
    
    for i in range(start_idx, min(end_idx, len(cases))):
        case = cases[i]
        
