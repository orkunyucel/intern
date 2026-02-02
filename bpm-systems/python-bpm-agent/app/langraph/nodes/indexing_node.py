"""
Indexing Node - Document Indexing Pipeline

PDF → Parse → Chunk → Embed → Store (Qdrant)
"""
from typing import Dict, Any, List
from app.langraph.state import DocumentProcessingState
from app.langraph.nodes.pdf_processor_node import create_pdf_processor_node
from app.langraph.nodes.embedding_node import create_embedding_node
from app.langraph.nodes.retrieval_node import create_retrieval_node


class IndexingNode:
    """
    Document Indexing Pipeline Node

    Complete pipeline:
    1. PDF Processing
    2. Embedding Generation
    3. Vector Store Insertion

    Tek bir node'da tüm indexing pipeline'ı.
    """

    def __init__(self, collection_name: str = None):
        self.pdf_processor = create_pdf_processor_node()
        self.embedder = create_embedding_node()
        self.retriever = create_retrieval_node(collection_name=collection_name)

    def __call__(self, state: DocumentProcessingState) -> DocumentProcessingState:
        """
        Execute full indexing pipeline

        Args:
            state: Document processing state

        Returns:
            Updated state with vector IDs
        """
        try:
            # Step 1: Process PDF
            state = self.pdf_processor(state)

            if state["processing_status"] == "failed":
                return state

            # Step 2: Generate embeddings
            state = self.embedder(state)

            if state.get("error_message"):
                state["processing_status"] = "failed"
                return state

            # Step 3: Store in Qdrant
            chunks = state["chunks"]
            embeddings = state["embeddings"]

            vector_ids = self.retriever.add_documents(chunks, embeddings)
            state["vector_ids"] = vector_ids

            state["processing_status"] = "completed"

            return state

        except Exception as e:
            state["processing_status"] = "failed"
            state["error_message"] = f"Indexing error: {str(e)}"
            return state

    def index_file(self, file_path: str, file_type: str = "pdf") -> Dict[str, Any]:
        """
        Index a single file

        Args:
            file_path: Path to file
            file_type: File type (pdf, txt)

        Returns:
            Indexing result
        """
        # Create initial state
        state: DocumentProcessingState = {
            "file_path": file_path,
            "file_type": file_type,
            "raw_text": "",
            "cleaned_text": "",
            "chunks": [],
            "document_metadata": {},
            "embeddings": [],
            "vector_ids": [],
            "processing_status": "pending",
            "error_message": None
        }

        # Process
        result_state = self(state)

        return {
            "status": result_state["processing_status"],
            "file": file_path,
            "chunks_created": len(result_state.get("chunks", [])),
            "vectors_stored": len(result_state.get("vector_ids", [])),
            "error": result_state.get("error_message")
        }

    def index_directory(self, directory_path: str) -> List[Dict[str, Any]]:
        """
        Index all PDFs in directory

        Args:
            directory_path: Path to directory

        Returns:
            List of indexing results
        """
        import os

        results = []

        for filename in os.listdir(directory_path):
            if filename.endswith('.pdf') or filename.endswith('.txt'):
                file_path = os.path.join(directory_path, filename)
                file_type = "pdf" if filename.endswith('.pdf') else "txt"

                result = self.index_file(file_path, file_type)
                results.append(result)

        return results


class IncrementalIndexingNode(IndexingNode):
    """
    Incremental Indexing Node

    Aynı dökümanı tekrar index etmez, sadece yeni/değişen dökümanları index eder.
    """

    def __init__(self, collection_name: str = None):
        super().__init__(collection_name)
        self.indexed_sources = self._load_indexed_sources()

    def _load_indexed_sources(self) -> set:
        """Load list of already indexed sources"""
        # Bu basit implementasyon - production'da bir DB'de tutulabilir
        try:
            stats = self.retriever.get_collection_stats()
            # Qdrant'tan source listesi çek
            # Şimdilik basit approach
            return set()
        except:
            return set()

    def index_file(self, file_path: str, file_type: str = "pdf") -> Dict[str, Any]:
        """
        Index file only if not already indexed

        Args:
            file_path: Path to file
            file_type: File type

        Returns:
            Indexing result
        """
        import os
        source = os.path.basename(file_path)

        # Check if already indexed
        if source in self.indexed_sources:
            return {
                "status": "skipped",
                "file": file_path,
                "reason": "Already indexed"
            }

        # Index
        result = super().index_file(file_path, file_type)

        # Add to indexed sources if successful
        if result["status"] == "completed":
            self.indexed_sources.add(source)

        return result


def create_indexing_node(collection_name: str = None):
    """Factory function for indexing node"""
    return IndexingNode(collection_name=collection_name)


def create_incremental_indexing_node(collection_name: str = None):
    """Factory function for incremental indexing node"""
    return IncrementalIndexingNode(collection_name=collection_name)
