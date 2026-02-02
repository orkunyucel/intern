"""
Retrieval Node - Qdrant Vector Store Integration

Qdrant'tan döküman retrieval yapar.
"""
from typing import List, Dict, Any, Optional
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance,
    VectorParams,
    PointStruct,
    Filter,
    FieldCondition,
    MatchValue,
    SearchRequest
)

from app.langraph.state import RAGState
from app.langraph.nodes.embedding_node import create_query_embedding_node
from app.config import get_config


class RetrievalNode:
    """
    Retrieval Node - Qdrant Integration

    Özellikleri:
    - Semantic search
    - Metadata filtering
    - Re-ranking
    - Score threshold
    """

    def __init__(self, collection_name: str = None):
        config = get_config()
        self.config = config

        # Qdrant client
        self.client = QdrantClient(
            host=config.qdrant.host,
            port=config.qdrant.port
        )

        self.collection_name = collection_name or config.qdrant.collection_name

        # Query embedder
        self.query_embedder = create_query_embedding_node()

        # Ensure collection exists
        self._ensure_collection()

    def _ensure_collection(self):
        """Ensure Qdrant collection exists"""
        try:
            collections = self.client.get_collections().collections
            collection_names = [c.name for c in collections]

            if self.collection_name not in collection_names:
                # Create collection
                self.client.create_collection(
                    collection_name=self.collection_name,
                    vectors_config=VectorParams(
                        size=self.config.qdrant.vector_size,
                        distance=Distance.COSINE
                    )
                )
                print(f"✅ Created Qdrant collection: {self.collection_name}")

        except Exception as e:
            print(f"⚠️  Collection check error: {e}")

    def __call__(self, state: RAGState) -> RAGState:
        """
        Retrieve documents for query

        Args:
            state: RAG state with query

        Returns:
            Updated state with retrieved documents
        """
        try:
            query = state["query"]
            filters = state.get("filters")
            limit = self.config.rag.default_limit

            # Retrieve
            results = self.search(
                query=query,
                limit=limit,
                filters=filters
            )

            # Update state
            state["retrieved_docs"] = results["documents"]
            state["relevance_scores"] = results["scores"]

            # Add to step history
            state["step_history"].append(
                f"retrieved_{len(results['documents'])}_docs"
            )

            return state

        except Exception as e:
            state["error_message"] = f"Retrieval error: {str(e)}"
            return state

    def search(
        self,
        query: str,
        limit: int = 5,
        score_threshold: float = None,
        filters: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """
        Search for relevant documents

        Args:
            query: Search query
            limit: Number of results
            score_threshold: Minimum relevance score
            filters: Metadata filters

        Returns:
            Dict with documents and scores
        """
        # Get query embedding
        query_vector = self.query_embedder(query)

        # Prepare search params
        score_threshold = score_threshold or self.config.rag.score_threshold

        # Build filter
        search_filter = self._build_filter(filters) if filters else None

        # Search
        search_results = self.client.search(
            collection_name=self.collection_name,
            query_vector=query_vector,
            limit=limit,
            score_threshold=score_threshold,
            query_filter=search_filter
        )

        # Format results
        documents = []
        scores = []

        for result in search_results:
            doc = {
                "content": result.payload.get("text", ""),
                "metadata": result.payload.get("metadata", {}),
                "source": result.payload.get("source", "unknown"),
                "chunk_id": result.payload.get("chunk_id", ""),
            }
            documents.append(doc)
            scores.append(result.score)

        return {
            "documents": documents,
            "scores": scores
        }

    def _build_filter(self, filters: Dict[str, Any]) -> Optional[Filter]:
        """
        Build Qdrant filter from dict

        Args:
            filters: Filter dict (e.g., {"source": "policy.pdf"})

        Returns:
            Qdrant Filter object
        """
        if not filters:
            return None

        conditions = []

        for key, value in filters.items():
            condition = FieldCondition(
                key=f"metadata.{key}",
                match=MatchValue(value=value)
            )
            conditions.append(condition)

        return Filter(must=conditions)

    def add_documents(
        self,
        chunks: List[Dict[str, Any]],
        embeddings: List[List[float]]
    ) -> List[str]:
        """
        Add documents to Qdrant

        Args:
            chunks: List of document chunks
            embeddings: Corresponding embeddings

        Returns:
            List of vector IDs
        """
        points = []

        for idx, (chunk, embedding) in enumerate(zip(chunks, embeddings)):
            point = PointStruct(
                id=idx,
                vector=embedding,
                payload={
                    "text": chunk["text"],
                    "metadata": chunk["metadata"],
                    "source": chunk["metadata"].get("source", "unknown"),
                    "chunk_id": chunk["chunk_id"]
                }
            )
            points.append(point)

        # Upsert to Qdrant
        self.client.upsert(
            collection_name=self.collection_name,
            points=points
        )

        return [str(p.id) for p in points]

    def delete_by_source(self, source: str):
        """
        Delete all documents from a source

        Args:
            source: Source name (e.g., "policy.pdf")
        """
        # Build filter
        delete_filter = Filter(
            must=[
                FieldCondition(
                    key="source",
                    match=MatchValue(value=source)
                )
            ]
        )

        # Delete
        self.client.delete(
            collection_name=self.collection_name,
            points_selector=delete_filter
        )

    def get_collection_stats(self) -> Dict[str, Any]:
        """Get collection statistics"""
        try:
            collection_info = self.client.get_collection(self.collection_name)
            return {
                "total_vectors": collection_info.points_count,
                "vector_size": collection_info.config.params.vectors.size,
                "distance": collection_info.config.params.vectors.distance
            }
        except Exception as e:
            return {"error": str(e)}


class HybridRetrievalNode(RetrievalNode):
    """
    Hybrid Retrieval Node

    Semantic search + keyword matching kombinasyonu
    """

    def search(
        self,
        query: str,
        limit: int = 5,
        score_threshold: float = None,
        filters: Dict[str, Any] = None
    ) -> Dict[str, Any]:
        """
        Hybrid search: semantic + keyword

        Args:
            query: Search query
            limit: Number of results
            score_threshold: Minimum score
            filters: Metadata filters

        Returns:
            Combined results
        """
        # 1. Semantic search
        semantic_results = super().search(
            query=query,
            limit=limit * 2,  # Get more for re-ranking
            score_threshold=score_threshold,
            filters=filters
        )

        # 2. Re-rank based on keyword overlap
        if self.config.rag.enable_reranking:
            reranked = self._rerank_by_keywords(
                query=query,
                documents=semantic_results["documents"],
                scores=semantic_results["scores"]
            )
            return {
                "documents": reranked["documents"][:limit],
                "scores": reranked["scores"][:limit]
            }

        return semantic_results

    def _rerank_by_keywords(
        self,
        query: str,
        documents: List[Dict],
        scores: List[float]
    ) -> Dict[str, Any]:
        """
        Re-rank documents based on keyword overlap

        Args:
            query: Original query
            documents: Retrieved documents
            scores: Semantic scores

        Returns:
            Re-ranked documents and scores
        """
        import re

        # Extract keywords from query
        query_lower = query.lower()
        query_words = set(re.findall(r'\w+', query_lower))
        query_words = {w for w in query_words if len(w) > 3}  # Skip short words

        # Calculate keyword scores
        keyword_scores = []
        for doc in documents:
            content_lower = doc["content"].lower()
            content_words = set(re.findall(r'\w+', content_lower))

            # Jaccard similarity
            if query_words and content_words:
                overlap = query_words & content_words
                keyword_score = len(overlap) / len(query_words)
            else:
                keyword_score = 0.0

            keyword_scores.append(keyword_score)

        # Combine scores (70% semantic, 30% keyword)
        combined_scores = [
            0.7 * sem_score + 0.3 * kw_score
            for sem_score, kw_score in zip(scores, keyword_scores)
        ]

        # Sort by combined score
        sorted_indices = sorted(
            range(len(combined_scores)),
            key=lambda i: combined_scores[i],
            reverse=True
        )

        reranked_docs = [documents[i] for i in sorted_indices]
        reranked_scores = [combined_scores[i] for i in sorted_indices]

        return {
            "documents": reranked_docs,
            "scores": reranked_scores
        }


def create_retrieval_node(collection_name: str = None):
    """Factory function for retrieval node"""
    return RetrievalNode(collection_name=collection_name)


def create_hybrid_retrieval_node(collection_name: str = None):
    """Factory function for hybrid retrieval node"""
    return HybridRetrievalNode(collection_name=collection_name)
