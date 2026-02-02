"""
RAG Node - Retrieval Augmented Generation

PDF ve dökümanlardan bilgi retrieve eder ve yanıt generate eder.
"""
from typing import Dict, Any, List
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import SystemMessage, HumanMessage

from app.langraph.state import RAGState
from app.langraph.prompts.system_prompts import (
    RAG_SYSTEM_PROMPT,
    RAG_GENERATION_PROMPT
)
from app.config import get_config


class RAGNode:
    """
    RAG Node - Document Retrieval + Generation

    Özellikleri:
    - Qdrant'tan döküman retrieval
    - Context oluşturma
    - Citation'lı yanıt üretme
    - PDF desteği
    """

    def __init__(self, retriever=None):
        """
        Args:
            retriever: Document retriever instance (Qdrant)
        """
        config = get_config()
        self.llm = ChatGoogleGenerativeAI(
            model=config.gemini.model_name,
            google_api_key=config.gemini.api_key,
            temperature=config.gemini.temperature,
            max_tokens=config.gemini.max_tokens
        )
        self.retriever = retriever
        self.config = config

    def __call__(self, state: RAGState) -> RAGState:
        """
        Ana RAG logic

        Args:
            state: Current RAG state

        Returns:
            Updated state with response and citations
        """
        try:
            query = state["query"]

            # Step 1: Retrieve documents
            if self.retriever:
                retrieved = self._retrieve_documents(query, state.get("filters"))
                state["retrieved_docs"] = retrieved["documents"]
                state["relevance_scores"] = retrieved["scores"]
            else:
                # Fallback: use pre-retrieved docs if available
                if not state.get("retrieved_docs"):
                    state["response"] = "Döküman retriever yapılandırılmamış."
                    state["error_message"] = "No retriever configured"
                    return state

            # Step 2: Build context
            context_text = self._build_context(state["retrieved_docs"])
            state["context_text"] = context_text

            # Step 3: Generate response
            if context_text:
                response = self._generate_response(query, context_text, state["retrieved_docs"])
                state["response"] = response["answer"]
                state["citations"] = response["citations"]
            else:
                state["response"] = "Bu konuda ilgili döküman bulunamadı."
                state["citations"] = []

            # Update step history
            state["step_history"].append("rag_node_completed")

            return state

        except Exception as e:
            state["error_message"] = f"RAG Node error: {str(e)}"
            state["response"] = "Bir hata oluştu. Lütfen tekrar deneyin."
            return state

    def _retrieve_documents(self, query: str, filters: Dict = None) -> Dict[str, Any]:
        """
        Retrieve documents from vector store

        Args:
            query: Search query
            filters: Optional filters

        Returns:
            Dict with documents and scores
        """
        # Bu method Part B'de tam implement edilecek
        # Şimdilik placeholder
        results = self.retriever.search(
            query=query,
            limit=self.config.rag.default_limit,
            score_threshold=self.config.rag.score_threshold,
            filters=filters
        )

        documents = []
        scores = []

        for result in results:
            documents.append({
                "content": result.payload.get("text", ""),
                "metadata": result.payload.get("metadata", {}),
                "source": result.payload.get("source", "unknown")
            })
            scores.append(result.score)

        return {
            "documents": documents,
            "scores": scores
        }

    def _build_context(self, documents: List[Dict[str, Any]]) -> str:
        """
        Build context from retrieved documents

        Args:
            documents: Retrieved documents

        Returns:
            Formatted context string
        """
        if not documents:
            return ""

        context_parts = []
        for idx, doc in enumerate(documents, 1):
            content = doc.get("content", "")
            source = doc.get("source", "unknown")
            metadata = doc.get("metadata", {})

            # Format: [Source] Content
            page = metadata.get("page", "N/A")
            context_part = f"[Kaynak {idx}: {source}, Sayfa: {page}]\n{content}\n"
            context_parts.append(context_part)

        return "\n---\n".join(context_parts)

    def _generate_response(
        self,
        query: str,
        context: str,
        documents: List[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """
        Generate response using LLM

        Args:
            query: User query
            context: Retrieved context
            documents: Source documents

        Returns:
            Dict with answer and citations
        """
        # Prepare metadata string
        metadata_str = self._format_metadata(documents)

        # Create prompt
        prompt_text = RAG_GENERATION_PROMPT.format(
            question=query,
            context=context,
            metadata=metadata_str
        )

        messages = [
            SystemMessage(content=RAG_SYSTEM_PROMPT),
            HumanMessage(content=prompt_text)
        ]

        # Generate
        response = self.llm.invoke(messages)
        answer = response.content

        # Extract citations
        citations = self._extract_citations(documents)

        return {
            "answer": answer,
            "citations": citations
        }

    def _format_metadata(self, documents: List[Dict[str, Any]]) -> str:
        """Format document metadata for prompt"""
        metadata_parts = []
        for idx, doc in enumerate(documents, 1):
            metadata = doc.get("metadata", {})
            source = doc.get("source", "unknown")
            metadata_parts.append(
                f"Döküman {idx}: {source} (Sayfa: {metadata.get('page', 'N/A')})"
            )
        return "\n".join(metadata_parts)

    def _extract_citations(self, documents: List[Dict[str, Any]]) -> List[str]:
        """Extract citation information"""
        citations = []
        for doc in documents:
            source = doc.get("source", "unknown")
            metadata = doc.get("metadata", {})
            page = metadata.get("page", "N/A")
            citations.append(f"{source} (Sayfa: {page})")
        return citations


def create_rag_node(retriever=None):
    """Factory function for RAG node"""
    rag_node = RAGNode(retriever=retriever)
    return rag_node
