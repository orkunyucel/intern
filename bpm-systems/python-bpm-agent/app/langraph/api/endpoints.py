"""
LangGraph API Endpoints

FastAPI entegrasyonu için endpoint'ler.
"""
from typing import Dict, Any, Optional, List
from pydantic import BaseModel, Field

from app.langraph.graphs.multi_agent_graph import create_multi_agent_graph
from app.langraph.nodes.retrieval_node import create_retrieval_node
from app.langraph.integration import get_langraph_service


# ============================================================================
# Request/Response Models
# ============================================================================

class ChatRequest(BaseModel):
    """Chat request model"""
    question: str = Field(..., description="User question")
    chat_history: Optional[List[Dict[str, str]]] = Field(
        default=[],
        description="Chat history"
    )
    filters: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Optional metadata filters"
    )
    agent_type: Optional[str] = Field(
        default="auto",
        description="Agent type hint (auto, rag, simple_qa, analysis, tool)"
    )
    include_trace: bool = Field(
        default=False,
        description="Include execution trace in response"
    )


class ChatResponse(BaseModel):
    """Chat response model"""
    answer: str = Field(..., description="Answer to the question")
    agent_used: str = Field(..., description="Agent that answered")
    citations: Optional[List[str]] = Field(
        default=[],
        description="Source citations (for RAG)"
    )
    tools_used: Optional[List[str]] = Field(
        default=[],
        description="Tools used (for tool agent)"
    )
    trace: Optional[List[Dict[str, Any]]] = Field(
        default=[],
        description="Execution trace (if requested)"
    )
    success: bool = Field(..., description="Success status")
    error: Optional[str] = Field(default=None, description="Error message if any")


class SearchRequest(BaseModel):
    """Document search request"""
    query: str = Field(..., description="Search query")
    limit: int = Field(default=5, ge=1, le=20, description="Number of results")
    filters: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Metadata filters"
    )
    score_threshold: float = Field(
        default=0.3,
        ge=0.0,
        le=1.0,
        description="Minimum relevance score"
    )


class SearchResponse(BaseModel):
    """Document search response"""
    query: str
    results: List[Dict[str, Any]]
    total: int
    success: bool
    error: Optional[str] = None


class IndexRequest(BaseModel):
    """Document indexing request"""
    file_path: str = Field(..., description="Path to document file")
    file_type: str = Field(default="pdf", description="File type (pdf, txt)")
    source_name: Optional[str] = Field(
        default=None,
        description="Optional source name override"
    )


class IndexResponse(BaseModel):
    """Document indexing response"""
    status: str
    file: str
    chunks_created: int
    vectors_stored: int
    success: bool
    error: Optional[str] = None


# ============================================================================
# Service Layer
# ============================================================================

class LangGraphAPIService:
    """
    LangGraph API Service

    FastAPI endpoint'leri için business logic.
    """

    def __init__(self):
        # Create components
        self.retriever = create_retrieval_node()
        self.multi_agent = create_multi_agent_graph(retriever=self.retriever)
        self.langraph_service = get_langraph_service()

    async def chat(self, request: ChatRequest) -> ChatResponse:
        """
        Handle chat request

        Args:
            request: Chat request

        Returns:
            Chat response
        """
        try:
            # Use multi-agent graph
            result = await self.multi_agent.ainvoke(
                task=request.question,
                task_type=request.agent_type
            )

            # Get citations if RAG was used
            citations = []
            if result['assigned_agent'] == 'RAG_AGENT':
                agent_outputs = result.get('agent_outputs', [])
                if agent_outputs:
                    citations = agent_outputs[-1].get('citations', [])

            return ChatResponse(
                answer=result['answer'],
                agent_used=result['assigned_agent'],
                citations=citations,
                tools_used=result.get('tools_used', []),
                trace=result['trace'] if request.include_trace else [],
                success=result['error'] is None,
                error=result.get('error')
            )

        except Exception as e:
            return ChatResponse(
                answer="Bir hata oluştu. Lütfen tekrar deneyin.",
                agent_used="error",
                success=False,
                error=str(e)
            )

    async def search(self, request: SearchRequest) -> SearchResponse:
        """
        Handle search request

        Args:
            request: Search request

        Returns:
            Search response
        """
        try:
            result = await self.langraph_service.search_documents(
                query=request.query,
                limit=request.limit,
                filters=request.filters
            )

            return SearchResponse(
                query=result['query'],
                results=result['results'],
                total=result['total'],
                success=True
            )

        except Exception as e:
            return SearchResponse(
                query=request.query,
                results=[],
                total=0,
                success=False,
                error=str(e)
            )

    async def index_document(self, request: IndexRequest) -> IndexResponse:
        """
        Handle document indexing

        Args:
            request: Index request

        Returns:
            Index response
        """
        try:
            from app.langraph.nodes.indexing_node import create_indexing_node

            indexer = create_indexing_node()
            result = indexer.index_file(
                file_path=request.file_path,
                file_type=request.file_type
            )

            return IndexResponse(
                status=result['status'],
                file=result['file'],
                chunks_created=result['chunks_created'],
                vectors_stored=result['vectors_stored'],
                success=result['status'] == 'completed',
                error=result.get('error')
            )

        except Exception as e:
            return IndexResponse(
                status='failed',
                file=request.file_path,
                chunks_created=0,
                vectors_stored=0,
                success=False,
                error=str(e)
            )

    async def get_stats(self) -> Dict[str, Any]:
        """
        Get system statistics

        Returns:
            Stats dict
        """
        try:
            collection_stats = self.retriever.get_collection_stats()

            return {
                "success": True,
                "stats": {
                    "total_documents": collection_stats.get('total_vectors', 0),
                    "vector_size": collection_stats.get('vector_size', 768),
                    "distance_metric": collection_stats.get('distance', 'cosine'),
                },
                "agents": {
                    "rag": "available",
                    "simple_qa": "available",
                    "analysis": "available",
                    "tool": "available"
                }
            }

        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }


# Global service instance
_api_service: Optional[LangGraphAPIService] = None


def get_api_service() -> LangGraphAPIService:
    """Get or create API service"""
    global _api_service
    if _api_service is None:
        _api_service = LangGraphAPIService()
    return _api_service


# ============================================================================
# FastAPI Route Handlers (to be added to main.py)
# ============================================================================

"""
Add these to your FastAPI app (app/main.py):

from app.langraph.api.endpoints import (
    get_api_service,
    ChatRequest,
    ChatResponse,
    SearchRequest,
    SearchResponse,
    IndexRequest,
    IndexResponse
)

api_service = get_api_service()


@app.post("/api/v1/langraph/chat", response_model=ChatResponse)
async def langraph_chat(request: ChatRequest):
    '''
    Chat with multi-agent system

    The system automatically routes your question to the best agent:
    - RAG Agent: Document-based questions
    - Simple QA: General questions
    - Analysis Agent: Complex analysis
    - Tool Agent: Calculations, date queries, etc.
    '''
    return await api_service.chat(request)


@app.post("/api/v1/langraph/search", response_model=SearchResponse)
async def langraph_search(request: SearchRequest):
    '''Search for documents in knowledge base'''
    return await api_service.search(request)


@app.post("/api/v1/langraph/index", response_model=IndexResponse)
async def langraph_index(request: IndexRequest):
    '''Index a new document'''
    return await api_service.index_document(request)


@app.get("/api/v1/langraph/stats")
async def langraph_stats():
    '''Get system statistics'''
    return await api_service.get_stats()
"""
