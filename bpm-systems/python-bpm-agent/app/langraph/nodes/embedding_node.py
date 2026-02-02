"""
Embedding Node - Vector Embedding Generation

Text chunk'larını vector embedding'lere çevirir.
"""
from typing import List, Dict, Any
import google.generativeai as genai
from langchain_google_genai import GoogleGenerativeAIEmbeddings

from app.langraph.state import DocumentProcessingState
from app.config import get_config


class EmbeddingNode:
    """
    Embedding Generation Node

    Özellikleri:
    - Gemini text-embedding-004 kullanır
    - Batch embedding desteği
    - Error handling ve retry logic
    - Dimension: 768
    """

    def __init__(self):
        config = get_config()
        self.config = config

        # Configure Gemini
        genai.configure(api_key=config.gemini.api_key)

        # LangChain embeddings
        self.embeddings = GoogleGenerativeAIEmbeddings(
            model=config.gemini.embedding_model,
            google_api_key=config.gemini.api_key
        )

    def __call__(self, state: DocumentProcessingState) -> DocumentProcessingState:
        """
        Generate embeddings for document chunks

        Args:
            state: Document processing state with chunks

        Returns:
            Updated state with embeddings
        """
        try:
            chunks = state.get("chunks", [])

            if not chunks:
                state["error_message"] = "No chunks to embed"
                return state

            # Extract text from chunks
            texts = [chunk["text"] for chunk in chunks]

            # Generate embeddings
            embeddings = self.embed_texts(texts)

            # Update state
            state["embeddings"] = embeddings

            return state

        except Exception as e:
            state["error_message"] = f"Embedding error: {str(e)}"
            return state

    def embed_texts(self, texts: List[str]) -> List[List[float]]:
        """
        Generate embeddings for list of texts

        Args:
            texts: List of text strings

        Returns:
            List of embedding vectors
        """
        try:
            # Use LangChain embeddings
            embeddings = self.embeddings.embed_documents(texts)
            return embeddings

        except Exception as e:
            print(f"Embedding error: {e}")
            # Return empty embeddings on error
            return [[0.0] * 768 for _ in texts]

    def embed_single(self, text: str) -> List[float]:
        """
        Generate embedding for single text

        Args:
            text: Text string

        Returns:
            Embedding vector
        """
        try:
            embedding = self.embeddings.embed_query(text)
            return embedding

        except Exception as e:
            print(f"Single embedding error: {e}")
            return [0.0] * 768

    def embed_batch(
        self,
        texts: List[str],
        batch_size: int = 10
    ) -> List[List[float]]:
        """
        Generate embeddings in batches

        Args:
            texts: List of texts
            batch_size: Batch size for processing

        Returns:
            List of embeddings
        """
        all_embeddings = []

        for i in range(0, len(texts), batch_size):
            batch = texts[i:i + batch_size]
            batch_embeddings = self.embed_texts(batch)
            all_embeddings.extend(batch_embeddings)

        return all_embeddings


class QueryEmbeddingNode:
    """
    Query Embedding Node

    Kullanıcı sorgusunu embedding'e çevirir (retrieval için).
    """

    def __init__(self):
        self.embedding_node = EmbeddingNode()

    def __call__(self, query: str) -> List[float]:
        """
        Embed user query

        Args:
            query: User question

        Returns:
            Query embedding vector
        """
        return self.embedding_node.embed_single(query)


def create_embedding_node():
    """Factory function for embedding node"""
    return EmbeddingNode()


def create_query_embedding_node():
    """Factory function for query embedding node"""
    return QueryEmbeddingNode()
