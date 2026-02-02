"""
RAG Adapter - Mevcut Sistem Entegrasyonu

Mevcut PolicyRetriever ile LangGraph RAG'i birbirine bağlar.
"""
from typing import Dict, Any, List
from app.rag.retriever import PolicyRetriever
from app.langraph.graphs.rag_graph import create_rag_graph
from app.langraph.nodes.retrieval_node import create_retrieval_node


class RAGAdapter:
    """
    RAG Adapter

    Mevcut PolicyRetriever API'sini koruyarak LangGraph backend kullanır.
    """

    def __init__(self, use_langraph: bool = True):
        """
        Args:
            use_langraph: True ise LangGraph kullan, False ise mevcut sistem
        """
        self.use_langraph = use_langraph

        if use_langraph:
            # LangGraph components
            self.retrieval_node = create_retrieval_node()
            self.rag_graph = create_rag_graph(retriever=self)
        else:
            # Mevcut sistem
            self.legacy_retriever = PolicyRetriever()

    def search(
        self,
        query: str,
        limit: int = 5,
        score_threshold: float = 0.3,
        filters: Dict = None
    ) -> List[Any]:
        """
        Search interface - hem eski hem yeni sistem ile uyumlu

        Args:
            query: Search query
            limit: Number of results
            score_threshold: Minimum relevance score
            filters: Metadata filters

        Returns:
            List of search results
        """
        if self.use_langraph:
            # Use LangGraph retrieval node
            results = self.retrieval_node.search(
                query=query,
                limit=limit,
                score_threshold=score_threshold,
                filters=filters
            )

            # Convert to legacy format
            return self._convert_to_legacy_format(results)
        else:
            # Use legacy retriever
            return self.legacy_retriever.search(
                query=query,
                limit=limit,
                threshold=score_threshold
            )

    def query_with_rag(self, question: str, filters: Dict = None) -> Dict[str, Any]:
        """
        Full RAG query - returns answer with citations

        Args:
            question: User question
            filters: Optional filters

        Returns:
            Dict with answer, citations, context
        """
        if not self.use_langraph:
            raise NotImplementedError("RAG query requires LangGraph mode")

        # Use RAG graph
        result = self.rag_graph.invoke(query=question, filters=filters)

        return result

    def _convert_to_legacy_format(self, langraph_results: Dict) -> List[Any]:
        """
        Convert LangGraph results to legacy format

        Args:
            langraph_results: Results from retrieval node

        Returns:
            Legacy format results
        """
        from collections import namedtuple

        # Create mock ScoredPoint
        ScoredPoint = namedtuple('ScoredPoint', ['score', 'payload'])

        legacy_results = []

        for doc, score in zip(
            langraph_results["documents"],
            langraph_results["scores"]
        ):
            # Create payload
            payload = {
                "text": doc["content"],
                "metadata": doc["metadata"],
                "source": doc["source"]
            }

            # Create scored point
            point = ScoredPoint(score=score, payload=payload)
            legacy_results.append(point)

        return legacy_results


class LangGraphService:
    """
    LangGraph Service - Main entry point

    FastAPI endpoint'lerinden kullanılacak ana servis.
    """

    def __init__(self):
        self.adapter = RAGAdapter(use_langraph=True)

    async def ask_question(
        self,
        question: str,
        filters: Dict = None,
        include_context: bool = False
    ) -> Dict[str, Any]:
        """
        Ask a question using RAG

        Args:
            question: User question
            filters: Optional metadata filters
            include_context: Include retrieved context in response

        Returns:
            Answer dict
        """
        result = self.adapter.query_with_rag(question, filters)

        response = {
            "answer": result["answer"],
            "citations": result["citations"],
            "success": result["error"] is None
        }

        if include_context:
            response["context"] = result["context"]
            response["steps"] = result["steps"]

        if result["error"]:
            response["error"] = result["error"]

        return response

    async def search_documents(
        self,
        query: str,
        limit: int = 5,
        filters: Dict = None
    ) -> Dict[str, Any]:
        """
        Search for documents (no generation)

        Args:
            query: Search query
            limit: Number of results
            filters: Metadata filters

        Returns:
            Search results
        """
        results = self.adapter.search(
            query=query,
            limit=limit,
            filters=filters
        )

        return {
            "query": query,
            "results": [
                {
                    "content": r.payload.get("text", ""),
                    "source": r.payload.get("source", "unknown"),
                    "metadata": r.payload.get("metadata", {}),
                    "score": r.score
                }
                for r in results
            ],
            "total": len(results)
        }


# Global service instance
_langraph_service: LangGraphService = None


def get_langraph_service() -> LangGraphService:
    """Get or create global LangGraph service"""
    global _langraph_service
    if _langraph_service is None:
        _langraph_service = LangGraphService()
    return _langraph_service
