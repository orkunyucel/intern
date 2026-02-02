"""
LangGraph State Definitions
"""
from typing import TypedDict, List, Optional, Dict, Any, Annotated
from operator import add


class GraphState(TypedDict):
    """
    Base Graph State

    Tüm LangGraph workflow'larında kullanılan temel state yapısı.
    """
    # Input
    question: str
    chat_history: Annotated[List[Dict[str, str]], add]

    # Context & Retrieval
    documents: Annotated[List[Dict[str, Any]], add]
    context: str
    retrieval_score: float

    # Generation
    generation: str

    # Metadata
    error: Optional[str]
    steps: Annotated[List[str], add]
    metadata: Dict[str, Any]


class RAGState(TypedDict):
    """
    RAG Pipeline State

    PDF ve döküman retrieval için özelleştirilmiş state.
    """
    # User Input
    query: str
    filters: Optional[Dict[str, Any]]

    # Document Processing
    raw_documents: List[Dict[str, Any]]
    processed_chunks: Annotated[List[Dict[str, Any]], add]
    embeddings: List[List[float]]

    # Retrieval
    retrieved_docs: Annotated[List[Dict[str, Any]], add]
    relevance_scores: List[float]
    context_text: str

    # Generation
    response: str
    citations: List[str]

    # Control Flow
    needs_clarification: bool
    routing_decision: str

    # Metadata
    step_history: Annotated[List[str], add]
    error_message: Optional[str]


class AgentState(TypedDict):
    """
    Multi-Agent Orchestration State

    Birden fazla agent'ın koordinasyonu için kullanılır.
    """
    # Input
    task: str
    task_type: str  # "simple_qa", "complex_analysis", "multi_step"

    # Agent Routing
    assigned_agent: str
    agent_outputs: Annotated[List[Dict[str, Any]], add]

    # Tool Usage
    tools_used: Annotated[List[str], add]
    tool_results: Annotated[List[Dict[str, Any]], add]

    # Final Output
    final_answer: str
    confidence: float

    # Control
    next_action: str
    iteration_count: int
    max_iterations: int

    # Metadata
    trace: Annotated[List[Dict[str, Any]], add]
    error: Optional[str]


class DocumentProcessingState(TypedDict):
    """
    Document Processing State

    PDF parse, chunking ve embedding için state.
    """
    # Input
    file_path: str
    file_type: str  # "pdf", "txt", "docx"

    # Processing
    raw_text: str
    cleaned_text: str
    chunks: List[Dict[str, Any]]

    # Metadata Extraction
    document_metadata: Dict[str, Any]

    # Vector Store
    embeddings: List[List[float]]
    vector_ids: List[str]

    # Status
    processing_status: str  # "pending", "processing", "completed", "failed"
    error_message: Optional[str]
