"""
RAG Graph - Retrieval Augmented Generation Workflow

PDF ve dökümanlardan bilgi retrieve eden ve yanıt generate eden graph.
"""
from typing import Dict, Any
from langgraph.graph import StateGraph, END
from langchain_core.runnables import RunnableConfig

from app.langraph.state import RAGState
from app.langraph.nodes.rag_node import create_rag_node


class RAGGraph:
    """
    RAG Graph - Document Q&A Workflow

    Flow:
    START → validate_input → retrieve_docs → generate_response → END
             ↓ (invalid)
           error_handler → END
    """

    def __init__(self, retriever=None):
        """
        Args:
            retriever: Document retriever (Qdrant)
        """
        self.retriever = retriever
        self.rag_node = create_rag_node(retriever=retriever)
        self.graph = self._build_graph()

    def _build_graph(self) -> StateGraph:
        """Build the RAG graph"""

        # Create graph
        workflow = StateGraph(RAGState)

        # Add nodes
        workflow.add_node("validate_input", self._validate_input)
        workflow.add_node("rag_process", self.rag_node)
        workflow.add_node("error_handler", self._error_handler)

        # Set entry point
        workflow.set_entry_point("validate_input")

        # Add edges
        workflow.add_conditional_edges(
            "validate_input",
            self._should_process,
            {
                "process": "rag_process",
                "error": "error_handler"
            }
        )

        workflow.add_edge("rag_process", END)
        workflow.add_edge("error_handler", END)

        return workflow.compile()

    def _validate_input(self, state: RAGState) -> RAGState:
        """Validate input query"""
        query = state.get("query", "").strip()

        if not query:
            state["error_message"] = "Boş soru girdiniz."
            state["needs_clarification"] = True
            return state

        if len(query) < 3:
            state["error_message"] = "Soru çok kısa, daha detaylı yazın."
            state["needs_clarification"] = True
            return state

        state["step_history"].append("input_validated")
        return state

    def _should_process(self, state: RAGState) -> str:
        """Decide whether to process or handle error"""
        if state.get("error_message") or state.get("needs_clarification"):
            return "error"
        return "process"

    def _error_handler(self, state: RAGState) -> RAGState:
        """Handle errors gracefully"""
        error_msg = state.get("error_message", "Bilinmeyen hata")
        state["response"] = f"Üzgünüm, bir sorun oluştu: {error_msg}"
        state["citations"] = []
        return state

    def invoke(self, query: str, filters: Dict = None) -> Dict[str, Any]:
        """
        Invoke the RAG graph

        Args:
            query: User question
            filters: Optional filters

        Returns:
            Response dict with answer and citations
        """
        # Initialize state
        initial_state: RAGState = {
            "query": query,
            "filters": filters,
            "raw_documents": [],
            "processed_chunks": [],
            "embeddings": [],
            "retrieved_docs": [],
            "relevance_scores": [],
            "context_text": "",
            "response": "",
            "citations": [],
            "needs_clarification": False,
            "routing_decision": "",
            "step_history": [],
            "error_message": None
        }

        # Run graph
        result = self.graph.invoke(initial_state)

        return {
            "answer": result.get("response", ""),
            "citations": result.get("citations", []),
            "context": result.get("context_text", ""),
            "steps": result.get("step_history", []),
            "error": result.get("error_message")
        }

    async def ainvoke(self, query: str, filters: Dict = None) -> Dict[str, Any]:
        """Async version of invoke"""
        initial_state: RAGState = {
            "query": query,
            "filters": filters,
            "raw_documents": [],
            "processed_chunks": [],
            "embeddings": [],
            "retrieved_docs": [],
            "relevance_scores": [],
            "context_text": "",
            "response": "",
            "citations": [],
            "needs_clarification": False,
            "routing_decision": "",
            "step_history": [],
            "error_message": None
        }

        result = await self.graph.ainvoke(initial_state)

        return {
            "answer": result.get("response", ""),
            "citations": result.get("citations", []),
            "context": result.get("context_text", ""),
            "steps": result.get("step_history", []),
            "error": result.get("error_message")
        }


def create_rag_graph(retriever=None) -> RAGGraph:
    """
    Factory function to create RAG graph

    Args:
        retriever: Document retriever

    Returns:
        RAGGraph instance
    """
    return RAGGraph(retriever=retriever)
