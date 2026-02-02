"""
LangGraph Integration for BPM Agent

Complete LangChain + LangGraph integration:
- Multi-agent orchestration (Part A + C)
- RAG workflows (Part A + B)
- PDF processing (Part B)
- Tool calling (Part C)
- Error handling (Part C)
- API integration (Part C)

Quick Start:
    from app.langraph import create_multi_agent_graph

    graph = create_multi_agent_graph()
    result = await graph.ainvoke(task="Your question here")

Documentation:
    - README.md: Overview
    - PART_A_README.md: State, Prompts, Basic Nodes
    - PART_B_README.md: PDF, Embedding, Retrieval
    - PART_C_README.md: LLM, Tools, Multi-Agent, API
"""

# Part A: Graphs & Basic Nodes
from app.langraph.graphs.rag_graph import create_rag_graph, RAGGraph
from app.langraph.nodes.orchestrator_node import create_orchestrator_node
from app.langraph.nodes.rag_node import create_rag_node

# Part B: PDF & Retrieval
from app.langraph.nodes.pdf_processor_node import create_pdf_processor_node
from app.langraph.nodes.embedding_node import create_embedding_node
from app.langraph.nodes.retrieval_node import (
    create_retrieval_node,
    create_hybrid_retrieval_node
)
from app.langraph.nodes.indexing_node import (
    create_indexing_node,
    create_incremental_indexing_node
)

# Part C: Multi-Agent & Tools
from app.langraph.graphs.multi_agent_graph import create_multi_agent_graph
from app.langraph.nodes.llm_node import (
    create_llm_node,
    create_tool_calling_llm_node,
    create_cot_llm_node,
    create_self_critic_llm_node
)
from app.langraph.tools import get_all_tools, get_tools_by_category
from app.langraph.nodes.error_handler_node import (
    create_error_handler_node,
    create_retry_node,
    create_fallback_node
)

# Integration & API
from app.langraph.integration import (
    RAGAdapter,
    LangGraphService,
    get_langraph_service
)
from app.langraph.api import (
    get_api_service,
    LangGraphAPIService
)

__all__ = [
    # Graphs
    "create_rag_graph",
    "RAGGraph",
    "create_multi_agent_graph",

    # Nodes - Basic
    "create_orchestrator_node",
    "create_rag_node",

    # Nodes - PDF & Processing
    "create_pdf_processor_node",
    "create_embedding_node",
    "create_retrieval_node",
    "create_hybrid_retrieval_node",
    "create_indexing_node",
    "create_incremental_indexing_node",

    # Nodes - LLM
    "create_llm_node",
    "create_tool_calling_llm_node",
    "create_cot_llm_node",
    "create_self_critic_llm_node",

    # Nodes - Error Handling
    "create_error_handler_node",
    "create_retry_node",
    "create_fallback_node",

    # Tools
    "get_all_tools",
    "get_tools_by_category",

    # Integration
    "RAGAdapter",
    "LangGraphService",
    "get_langraph_service",

    # API
    "get_api_service",
    "LangGraphAPIService",
]
