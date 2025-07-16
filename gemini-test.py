from google import genai
from google.genai import types
import json

# Configure the client with API key
client = genai.Client(api_key="your-google-api-key")

# Define the grounding tool
grounding_tool = types.Tool(
    google_search=types.GoogleSearch()
)

# Configure generation settings
config = types.GenerateContentConfig(
    tools=[grounding_tool]
)

# Make the request with a question that requires web search
response = client.models.generate_content(
    model="gemini-2.5-flash",
    contents="请搜索网络并告诉我2024年欧洲杯足球锦标赛的冠军是谁？我需要最新和准确的决赛结果信息。",
    config=config,
)

# Print response text only
print("=== 响应文本 ===")
print(response.text)
print()

# Skip detailed candidate analysis - we'll show this in formatted results

# Skip grounding metadata details - will be shown in formatted results

# Format grounding results into structured objects

try:
    results = []

    # Check if we have grounding metadata in any candidate
    for candidate_idx, candidate in enumerate(response.candidates):
        if hasattr(candidate, 'grounding_metadata') and candidate.grounding_metadata:
            gm = candidate.grounding_metadata

            # Get web search queries
            search_queries = []
            if hasattr(gm, 'web_search_queries') and gm.web_search_queries:
                search_queries = [str(query) for query in gm.web_search_queries]

            # Get grounding chunks (sources)
            sources = []
            if hasattr(gm, 'grounding_chunks') and gm.grounding_chunks:
                for chunk_idx, chunk in enumerate(gm.grounding_chunks):
                    source = {
                        "chunk_index": chunk_idx,
                        "title": None,
                        "url": None,
                        "domain": None
                    }

                    if hasattr(chunk, 'web') and chunk.web:
                        if hasattr(chunk.web, 'title'):
                            source["title"] = chunk.web.title
                        if hasattr(chunk.web, 'uri'):
                            source["url"] = chunk.web.uri
                            # Extract domain from URL
                            try:
                                import re
                                domain_match = re.search(r'https?://([^/]+)', chunk.web.uri)
                                if domain_match:
                                    source["domain"] = domain_match.group(1)
                            except:
                                pass

                        # Check for any other web attributes we might have missed
                        # Exclude Pydantic model metadata fields
                        excluded_attrs = ['title', 'uri', 'model_config', 'model_fields', 'model_fields_set', 'model_computed_fields']
                        web_attrs = dir(chunk.web)
                        for attr in web_attrs:
                            if not attr.startswith('_') and attr not in excluded_attrs:
                                try:
                                    value = getattr(chunk.web, attr)
                                    if value is not None and not callable(value):
                                        source[f"web_{attr}"] = str(value)
                                except:
                                    pass

                    # Check for chunk-level attributes that might contain content descriptions
                    chunk_attrs = dir(chunk)
                    for attr in chunk_attrs:
                        if not attr.startswith('_') and attr not in ['web']:
                            try:
                                value = getattr(chunk, attr)
                                if value is not None and not callable(value):
                                    # Look for content-related attributes
                                    if any(keyword in attr.lower() for keyword in ['content', 'text', 'snippet', 'summary', 'description', 'excerpt']):
                                        source[f"chunk_{attr}"] = str(value)
                            except:
                                pass

                    sources.append(source)

            # Get grounding supports (text segments with their sources)
            text_segments = []
            if hasattr(gm, 'grounding_supports') and gm.grounding_supports:
                for support_idx, support in enumerate(gm.grounding_supports):
                    segment = {
                        "support_index": support_idx,
                        "text": None,
                        "start_index": None,
                        "end_index": None,
                        "source_indices": [],
                        "sources": []
                    }

                    # Extract segment information
                    if hasattr(support, 'segment'):
                        if hasattr(support.segment, 'text'):
                            segment["text"] = support.segment.text
                        if hasattr(support.segment, 'start_index'):
                            segment["start_index"] = support.segment.start_index
                        if hasattr(support.segment, 'end_index'):
                            segment["end_index"] = support.segment.end_index

                    # Extract source indices
                    if hasattr(support, 'grounding_chunk_indices'):
                        segment["source_indices"] = list(support.grounding_chunk_indices)
                        # Map indices to actual source information
                        for idx in support.grounding_chunk_indices:
                            if idx < len(sources):
                                segment["sources"].append(sources[idx])

                    text_segments.append(segment)

            # Create the result object for this candidate
            result = {
                "candidate_index": candidate_idx,
                "response_text": candidate.content.parts[0].text if candidate.content and candidate.content.parts else None,
                "search_queries": search_queries,
                "total_sources": len(sources),
                "sources": sources,
                "text_segments": text_segments,
                "metadata": {
                    "finish_reason": str(candidate.finish_reason) if hasattr(candidate, 'finish_reason') else None,
                    "has_safety_ratings": hasattr(candidate, 'safety_ratings') and candidate.safety_ratings is not None
                }
            }

            results.append(result)

    # Output results to console
    results_json = json.dumps(results, indent=2, ensure_ascii=False)
    print(results_json)

    # Save results to file
    try:
        with open('results.txt', 'w', encoding='utf-8') as f:
            f.write(results_json)
        print(f"\n✅ Results saved to results.txt")
    except Exception as file_error:
        print(f"\n❌ Failed to save to file: {file_error}")

except Exception as e:
    print(f"格式化失败: {e}")
    print(f"错误详情: {type(e).__name__}: {str(e)}")
