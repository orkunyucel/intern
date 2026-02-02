"""
Microservice Endpoints for Flowable Integration

Each AI component as a separate endpoint for detailed BPMN visualization.
Every step in the process is exposed as an individual service task.
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional
import google.generativeai as genai
import os
from datetime import datetime

from app.rag.retriever import QdrantRetriever
from app.llm.sentiment_analyzer import SentimentAnalyzer
from app.tools.mcp_tools import MCPToolExecutor

router = APIRouter(prefix="/api/microservices", tags=["microservices"])

# Initialize components
genai.configure(api_key=os.getenv("GEMINI_API_KEY"))
retriever = QdrantRetriever()
sentiment_analyzer = SentimentAnalyzer()
mcp_executor = MCPToolExecutor()


# ============================================================================
# REQUEST/RESPONSE MODELS
# ============================================================================

class EmbeddingRequest(BaseModel):
    text: str = Field(description="Text to embed")

class EmbeddingResponse(BaseModel):
    embedding: List[float] = Field(description="768-dim embedding vector")
    model: str = Field(description="Embedding model used")
    text_length: int = Field(description="Input text length")

class QdrantSearchRequest(BaseModel):
    embedding: List[float] = Field(description="Query embedding vector")
    limit: int = Field(default=5, description="Number of results")

class QdrantSearchResponse(BaseModel):
    results: List[Dict[str, Any]] = Field(description="Search results with scores")
    collection: str = Field(description="Collection name")
    count: int = Field(description="Number of results")

class SentimentRequest(BaseModel):
    text: str = Field(description="Text to analyze")
    source: str = Field(default="text", description="Source type")

class SentimentResponse(BaseModel):
    emotion: str
    intensity: int
    justifies_urgency: bool
    reasoning: str

class LLMRequest(BaseModel):
    text: str = Field(description="Customer request text")
    rag_context: str = Field(description="RAG context from Qdrant")
    sentiment: Dict[str, Any] = Field(description="Sentiment analysis result")

class LLMResponse(BaseModel):
    intent: str
    category: str
    priority: str
    auto_approve: bool
    reasoning: str
    tool_calls: List[Dict[str, Any]]

class MCPToolRequest(BaseModel):
    params: Dict[str, Any] = Field(description="Tool parameters")

class MCPToolResponse(BaseModel):
    success: bool
    result: Dict[str, Any]
    tool_name: str

class TTSRequest(BaseModel):
    text: str = Field(description="Text to convert to speech")
    language: str = Field(default="tr", description="Language code")

class StoreVectorRequest(BaseModel):
    text: str
    metadata: Dict[str, Any]
    collection: str = Field(default="conversations")


# ============================================================================
# 1. EMBEDDING GENERATION (Gemini text-embedding-004)
# ============================================================================

@router.post("/embedding", response_model=EmbeddingResponse)
async def generate_embedding(request: EmbeddingRequest):
    """
    Generate text embedding using Gemini

    Node: 🧮 Embedding Generation
    Purpose: Convert text to 768-dim vector for RAG search
    """
    try:
        print(f"[EMBEDDING] Generating for text: {request.text[:50]}...")

        result = genai.embed_content(
            model="models/text-embedding-004",
            content=request.text,
            task_type="retrieval_query"
        )

        embedding = result['embedding']

        print(f"[EMBEDDING] Generated {len(embedding)}-dim vector")

        return EmbeddingResponse(
            embedding=embedding,
            model="text-embedding-004",
            text_length=len(request.text)
        )
    except Exception as e:
        print(f"[ERROR] Embedding generation failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 2. QDRANT VECTOR SEARCH
# ============================================================================

@router.post("/qdrant-search", response_model=QdrantSearchResponse)
async def qdrant_vector_search(request: QdrantSearchRequest):
    """
    Search Qdrant vector database

    Node: 🔍 Vector Search (Qdrant)
    Purpose: Retrieve relevant policy documents using embedding
    """
    try:
        print(f"[QDRANT] Searching with embedding vector (dim={len(request.embedding)})")

        # Direct Qdrant search
        search_results = retriever.client.search(
            collection_name=retriever.collection_name,
            query_vector=request.embedding,
            limit=request.limit
        )

        results = []
        for hit in search_results:
            results.append({
                "text": hit.payload.get("text", ""),
                "score": hit.score,
                "metadata": hit.payload.get("metadata", {})
            })

        print(f"[QDRANT] Found {len(results)} results")

        return QdrantSearchResponse(
            results=results,
            collection=retriever.collection_name,
            count=len(results)
        )
    except Exception as e:
        print(f"[ERROR] Qdrant search failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 3. SENTIMENT ANALYSIS
# ============================================================================

@router.post("/sentiment", response_model=SentimentResponse)
async def analyze_sentiment(request: SentimentRequest):
    """
    Analyze customer sentiment

    Node: 😊 Sentiment Analysis
    Purpose: Detect emotion, intensity, and urgency justification
    """
    try:
        print(f"[SENTIMENT] Analyzing: {request.text[:50]}...")

        result = sentiment_analyzer.analyze(request.text, source=request.source)

        print(f"[SENTIMENT] {result['emotion']} (intensity: {result['intensity']}/10)")

        return SentimentResponse(
            emotion=result["emotion"],
            intensity=result["intensity"],
            justifies_urgency=result["justifies_urgency"],
            reasoning=result["reasoning"]
        )
    except Exception as e:
        print(f"[ERROR] Sentiment analysis failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 4. GEMINI LLM CALL
# ============================================================================

@router.post("/llm-call", response_model=LLMResponse)
async def gemini_llm_call(request: LLMRequest):
    """
    Call Gemini LLM for decision making

    Node: 🤖 Gemini LLM (Decision)
    Purpose: Generate intent, category, priority based on RAG context and sentiment
    """
    try:
        print(f"[GEMINI] Processing request...")

        # Build prompt
        prompt = f"""You are an intelligent customer service agent. Analyze this request:

REQUEST: {request.text}

RAG CONTEXT (Company Policies):
{request.rag_context}

SENTIMENT ANALYSIS:
- Emotion: {request.sentiment.get('emotion')}
- Intensity: {request.sentiment.get('intensity')}/10
- Urgency Justified: {request.sentiment.get('justifies_urgency')}

Based on the policies and sentiment, determine:
1. Intent (what does customer want?)
2. Category (TECH_SUPPORT, BILLING, HR, or GENERAL)
3. Priority (LOW, MEDIUM, HIGH, or URGENT)
4. Auto-approve eligibility (true/false)
5. Tool calls needed (updateCategory, setPriority, createTask, etc.)

Return as JSON."""

        model = genai.GenerativeModel('gemini-2.5-flash')
        response = model.generate_content(prompt)
        response_text = response.text

        # Parse response (simplified - in production use structured output)
        # For now, return mock structured data
        print(f"[GEMINI] Response received")

        return LLMResponse(
            intent=f"Customer request: {request.text[:50]}",
            category="TECH_SUPPORT",  # Parse from LLM response
            priority="HIGH",  # Parse from LLM response
            auto_approve=False,
            reasoning=response_text[:200],
            tool_calls=[
                {"tool": "updateCategory", "params": {"category": "TECH_SUPPORT"}},
                {"tool": "setPriority", "params": {"priority": "HIGH"}}
            ]
        )
    except Exception as e:
        print(f"[ERROR] Gemini LLM call failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 5. MCP TOOL: UPDATE CATEGORY
# ============================================================================

@router.post("/mcp/update-category", response_model=MCPToolResponse)
async def mcp_update_category(request: MCPToolRequest):
    """
    MCP Tool: Update Category

    Node: 📁 Update Category
    Purpose: Set request category in BPM system
    """
    try:
        result = mcp_executor.execute_tool("updateCategory", request.params)
        return MCPToolResponse(
            success=True,
            result=result,
            tool_name="updateCategory"
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 6. MCP TOOL: SET PRIORITY
# ============================================================================

@router.post("/mcp/set-priority", response_model=MCPToolResponse)
async def mcp_set_priority(request: MCPToolRequest):
    """
    MCP Tool: Set Priority

    Node: ⚡ Set Priority
    Purpose: Set request priority level
    """
    try:
        result = mcp_executor.execute_tool("setPriority", request.params)
        return MCPToolResponse(
            success=True,
            result=result,
            tool_name="setPriority"
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 7. MCP TOOL: CREATE TASK
# ============================================================================

@router.post("/mcp/create-task", response_model=MCPToolResponse)
async def mcp_create_task(request: MCPToolRequest):
    """
    MCP Tool: Create Task

    Node: 📝 Create Task
    Purpose: Create task for support team
    """
    try:
        result = mcp_executor.execute_tool("createTask", request.params)
        return MCPToolResponse(
            success=True,
            result=result,
            tool_name="createTask"
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 8. TEXT-TO-SPEECH
# ============================================================================

@router.post("/text-to-speech")
async def text_to_speech(request: TTSRequest):
    """
    Convert text to speech

    Node: 🔊 Text-to-Speech
    Purpose: Generate audio response for phone channel
    """
    try:
        from gtts import gTTS
        import tempfile

        print(f"[TTS] Converting to speech: {request.text[:50]}...")

        # Generate TTS
        tts = gTTS(text=request.text, lang=request.language)

        # Save to temp file
        temp_file = tempfile.NamedTemporaryFile(delete=False, suffix=".mp3")
        tts.save(temp_file.name)

        print(f"[TTS] Audio generated: {temp_file.name}")

        return {
            "success": True,
            "audio_file": temp_file.name,
            "text_length": len(request.text),
            "language": request.language
        }
    except Exception as e:
        print(f"[ERROR] TTS failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 9. STORE TO QDRANT
# ============================================================================

@router.post("/store-vector")
async def store_to_qdrant(request: StoreVectorRequest):
    """
    Store conversation/response to Qdrant

    Node: 💾 Store to Vector DB
    Purpose: Save interaction for future RAG retrieval
    """
    try:
        print(f"[STORE] Saving to collection: {request.collection}")

        # Generate embedding
        embedding_result = genai.embed_content(
            model="models/text-embedding-004",
            content=request.text,
            task_type="retrieval_document"
        )

        # Store in Qdrant
        from qdrant_client.models import PointStruct
        import uuid

        point = PointStruct(
            id=str(uuid.uuid4()),
            vector=embedding_result['embedding'],
            payload={
                "text": request.text,
                "metadata": request.metadata,
                "timestamp": datetime.now().isoformat()
            }
        )

        retriever.client.upsert(
            collection_name=request.collection,
            points=[point]
        )

        print(f"[STORE] Stored successfully")

        return {
            "success": True,
            "collection": request.collection,
            "text_length": len(request.text)
        }
    except Exception as e:
        print(f"[ERROR] Store failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 10. SUB-CATEGORIZATION (Department-specific routing)
# ============================================================================

class SubCategorizationRequest(BaseModel):
    category: str = Field(description="Main category (TECH_SUPPORT, BILLING, HR)")
    text: str = Field(description="Customer request text")
    intent: str = Field(description="Request intent")

class SubCategorizationResponse(BaseModel):
    main_category: str
    sub_category: str
    department: str
    assigned_team: str
    routing_rules: Dict[str, Any]
    reasoning: str

@router.post("/sub-categorization", response_model=SubCategorizationResponse)
async def sub_categorize_request(request: SubCategorizationRequest):
    """
    Sub-categorize requests within departments

    Node: 🏢 Sub-Categorization
    Purpose: Route to specific team within department

    Examples:
    - TECH_SUPPORT -> Internet, Email, Software, Hardware, Network
    - BILLING -> Payment, Invoice, Refund, Subscription, Cancellation
    - HR -> Leave, Onboarding, Performance, Payroll, Benefits
    """
    try:
        print(f"[SUB-CAT] Categorizing within {request.category}")

        # Sub-category mappings
        sub_categories = {
            "TECH_SUPPORT": {
                "internet": {"team": "Network Team", "sla_hours": 4},
                "email": {"team": "Email Support", "sla_hours": 8},
                "software": {"team": "Software Team", "sla_hours": 12},
                "hardware": {"team": "Hardware Team", "sla_hours": 24},
                "network": {"team": "Network Team", "sla_hours": 4},
                "security": {"team": "Security Team", "sla_hours": 2}
            },
            "BILLING": {
                "payment": {"team": "Payments Team", "sla_hours": 24},
                "invoice": {"team": "Invoicing Team", "sla_hours": 48},
                "refund": {"team": "Refunds Team", "sla_hours": 72},
                "subscription": {"team": "Subscriptions Team", "sla_hours": 24},
                "cancellation": {"team": "Retention Team", "sla_hours": 12}
            },
            "HR": {
                "leave": {"team": "HR Operations", "sla_hours": 24},
                "onboarding": {"team": "HR Recruitment", "sla_hours": 168},
                "performance": {"team": "HR Performance", "sla_hours": 168},
                "payroll": {"team": "Payroll Team", "sla_hours": 48},
                "benefits": {"team": "Benefits Team", "sla_hours": 72}
            },
            "GENERAL": {
                "info": {"team": "General Support", "sla_hours": 48},
                "feedback": {"team": "Customer Success", "sla_hours": 72},
                "complaint": {"team": "Quality Assurance", "sla_hours": 24}
            }
        }

        # AI-based sub-categorization
        text_lower = request.text.lower()
        intent_lower = request.intent.lower()

        detected_sub = "general"
        confidence = 0.5

        if request.category in sub_categories:
            cat_subs = sub_categories[request.category]

            # Keyword matching for sub-categorization
            keywords = {
                "internet": ["internet", "wifi", "bağlantı", "connection", "hız", "speed"],
                "email": ["email", "mail", "e-posta", "outlook", "gmail"],
                "software": ["yazılım", "software", "uygulama", "app", "program"],
                "hardware": ["donanım", "hardware", "bilgisayar", "pc", "laptop"],
                "network": ["ağ", "network", "vpn", "router", "switch"],
                "security": ["güvenlik", "security", "virus", "hack", "şifre", "password"],
                "payment": ["ödeme", "payment", "pay", "credit", "kredi kartı"],
                "invoice": ["fatura", "invoice", "bill"],
                "refund": ["iade", "refund", "geri ödeme"],
                "subscription": ["abonelik", "subscription", "plan"],
                "cancellation": ["iptal", "cancel", "kapatma"],
                "leave": ["izin", "leave", "tatil", "vacation"],
                "onboarding": ["işe başlama", "onboarding", "yeni çalışan"],
                "performance": ["performans", "performance", "değerlendirme"],
                "payroll": ["maaş", "payroll", "salary"],
                "benefits": ["yan haklar", "benefits", "sigorta"]
            }

            max_score = 0
            for sub_cat, kw_list in keywords.items():
                if sub_cat in cat_subs:
                    score = sum(1 for kw in kw_list if kw in text_lower or kw in intent_lower)
                    if score > max_score:
                        max_score = score
                        detected_sub = sub_cat
                        confidence = min(0.95, 0.5 + (score * 0.1))

        # Get routing rules
        routing_info = sub_categories.get(request.category, {}).get(detected_sub, {
            "team": "General Support",
            "sla_hours": 48
        })

        print(f"[SUB-CAT] {request.category} -> {detected_sub} (confidence: {confidence:.2f})")

        return SubCategorizationResponse(
            main_category=request.category,
            sub_category=detected_sub,
            department=f"{request.category}_{detected_sub.upper()}",
            assigned_team=routing_info["team"],
            routing_rules={
                "sla_hours": routing_info["sla_hours"],
                "confidence": confidence,
                "escalation_path": f"{routing_info['team']} -> Manager"
            },
            reasoning=f"Detected '{detected_sub}' based on keywords in request (confidence: {confidence:.0%})"
        )

    except Exception as e:
        print(f"[ERROR] Sub-categorization failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# ============================================================================
# 11. RAG RERANKING
# ============================================================================

class RerankinRequest(BaseModel):
    query: str = Field(description="Search query")
    results: List[Dict[str, Any]] = Field(description="Initial search results")
    top_k: int = Field(default=3, description="Number of results to return")

class RerankingResponse(BaseModel):
    reranked_results: List[Dict[str, Any]]
    top_k: int
    algorithm: str

@router.post("/rag-reranking", response_model=RerankingResponse)
async def rerank_rag_results(request: RerankinRequest):
    """
    Rerank RAG results for better relevance

    Node: 🔄 Reranking
    Purpose: Improve search result quality with semantic reranking
    """
    try:
        print(f"[RERANK] Reranking {len(request.results)} results for query: {request.query[:50]}")

        # Use retriever's reranker
        reranked = retriever.reranker.rerank(
            request.results,
            request.query,
            keywords=[]
        )

        # Take top k
        top_results = reranked[:request.top_k]

        print(f"[RERANK] Top {len(top_results)} results selected")

        return RerankingResponse(
            reranked_results=top_results,
            top_k=len(top_results),
            algorithm="SimpleReranker (keyword + type + position weighting)"
        )

    except Exception as e:
        print(f"[ERROR] Reranking failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))
