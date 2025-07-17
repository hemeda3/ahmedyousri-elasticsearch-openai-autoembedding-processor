import json
import requests
import time
import os

# Configuration
JSON_FILE_PATH = 'ahkam.json'
ELASTICSEARCH_URL = 'http://localhost:9200'
INDEX_NAME = 'my_index'
PIPELINE_NAME = 'my_index_pipeline'
OPENAI_API_KEY = os.getenv('OPENAI_API_KEY')  # Read from environment variable
CHUNK_SIZE = None  # Process ALL documents at once

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
        case = cases[i].copy()  # Make a copy to avoid modifying original
        
        # Truncate long text fields to first 5000 characters
        if 'full_case_text' in case and case['full_case_text']:
            if len(case['full_case_text']) > 2000:
                case['full_case_text'] = case['full_case_text'][:2000]
                print(f"⚠️  Truncated full_case_text for document {i+1} (was {len(cases[i]['full_case_text'])} chars, now 5000)")
        
        if 'case_identifier' in case and case['case_identifier']:
            if len(case['case_identifier']) > 1000:  # Smaller limit for identifier
                case['case_identifier'] = case['case_identifier'][:1000]
                print(f"⚠️  Truncated case_identifier for document {i+1}")
        
        # Index action (no ID = auto-generated)
        index_action = {"index": {}}
        bulk_data.append(json.dumps(index_action, ensure_ascii=False))
        
        # Document data
        bulk_data.append(json.dumps(case, ensure_ascii=False))
    
    return '\n'.join(bulk_data) + '\n'

def bulk_index_chunk(bulk_data, chunk_num, total_chunks):
    """Index a chunk of data"""
    url = f"{ELASTICSEARCH_URL}/{INDEX_NAME}/_bulk?pipeline={PIPELINE_NAME}"
    headers = {'Content-Type': 'application/x-ndjson'}
    
    print(f"📤 Indexing chunk {chunk_num}/{total_chunks}...")
    
    try:
        response = requests.post(url, data=bulk_data, headers=headers)
        
        if response.status_code == 200:
            result = response.json()
            if result.get('errors'):
                print(f"⚠️  Chunk {chunk_num} had some errors")
                # Print first error for debugging
                for item in result['items']:
                    if 'index' in item and 'error' in item['index']:
                        print(f"   Error: {item['index']['error']}")
                        break
            else:
                print(f"✅ Chunk {chunk_num} indexed successfully")
            return True
        else:
            print(f"❌ Chunk {chunk_num} failed: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Error indexing chunk {chunk_num}: {e}")
        return False

def main():
    """Main execution function"""
    print("🚀 Starting Qatar Cases Bulk Indexing with Auto-Vectorization")
    print("=" * 60)
    
    # Check if API key is set
    if OPENAI_API_KEY == 'YOUR_OPENAI_API_KEY_HERE':
        print("❌ Please set your OpenAI API key in the script")
        return
    print(f"❌P  {OPENAI_API_KEY}")
    # Create pipeline
    if not create_pipeline():
        return
    
    # Load JSON file
    cases = load_json_file()
    if not cases:
        return
    
    # Process ALL documents at once
    total_cases = len(cases)
    
    print(f"📊 Processing ALL {total_cases} cases in a single bulk request")
    print("=" * 60)
    
    start_time = time.time()
    
    # Create bulk data for ALL cases
    bulk_data = create_bulk_data(cases, 0, total_cases)
    
    # Index all documents at once
    print(f"📤 Indexing ALL {total_cases} documents...")
    success = bulk_index_chunk(bulk_data, 1, 1)
    
    # Summary
    elapsed_time = time.time() - start_time
    print("=" * 60)
    print(f"🎉 Indexing completed!")
    print(f"   Total cases: {total_cases}")
    print(f"   Time elapsed: {elapsed_time:.2f} seconds")
    
    if success:
        print("✅ All cases indexed successfully with auto-vectorization!")
        print(f"🔍 You can now search using:")
        print(f"   POST {ELASTICSEARCH_URL}/{INDEX_NAME}/_semantic_search")
        print(f"   POST {ELASTICSEARCH_URL}/{INDEX_NAME}/_hybrid_search")
    else:
        print("⚠️  Bulk indexing failed. Check the errors above.")

if __name__ == "__main__":
    main()
