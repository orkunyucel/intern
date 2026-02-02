"""
PDF Processor Node - PDF Parse & Chunking

PDF dosyalarını parse eder, chunk'lara böler ve metadata çıkarır.
"""
import os
from typing import Dict, Any, List
import pdfplumber
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import HumanMessage

from app.langraph.state import DocumentProcessingState
from app.langraph.prompts.system_prompts import DOCUMENT_METADATA_EXTRACTION_PROMPT
from app.config import get_config


class PDFProcessorNode:
    """
    PDF Processing Node

    Özellikleri:
    - PDF text extraction (pdfplumber)
    - Intelligent chunking
    - Metadata extraction per chunk
    - Table detection
    - Image reference tracking
    """

    def __init__(self):
        config = get_config()
        self.config = config
        self.llm = ChatGoogleGenerativeAI(
            model=config.gemini.model_name,
            google_api_key=config.gemini.api_key,
            temperature=0.3,
            max_tokens=1024
        )

        # Text splitter
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=config.rag.chunk_size,
            chunk_overlap=config.rag.chunk_overlap,
            length_function=len,
            separators=["\n\n", "\n", ". ", " ", ""]
        )

    def __call__(self, state: DocumentProcessingState) -> DocumentProcessingState:
        """
        Process PDF file

        Args:
            state: Document processing state

        Returns:
            Updated state with chunks and metadata
        """
        try:
            file_path = state["file_path"]
            file_type = state["file_type"]

            # Validate file
            if not os.path.exists(file_path):
                raise FileNotFoundError(f"File not found: {file_path}")

            state["processing_status"] = "processing"

            # Extract text based on file type
            if file_type == "pdf":
                raw_text, doc_metadata = self._extract_pdf(file_path)
            elif file_type == "txt":
                raw_text, doc_metadata = self._extract_txt(file_path)
            else:
                raise ValueError(f"Unsupported file type: {file_type}")

            state["raw_text"] = raw_text
            state["document_metadata"] = doc_metadata

            # Clean text
            cleaned_text = self._clean_text(raw_text)
            state["cleaned_text"] = cleaned_text

            # Create chunks
            chunks = self._create_chunks(cleaned_text, doc_metadata)
            state["chunks"] = chunks

            state["processing_status"] = "completed"

            return state

        except Exception as e:
            state["processing_status"] = "failed"
            state["error_message"] = f"PDF processing error: {str(e)}"
            return state

    def _extract_pdf(self, file_path: str) -> tuple[str, Dict[str, Any]]:
        """
        Extract text from PDF using pdfplumber

        Args:
            file_path: Path to PDF file

        Returns:
            Tuple of (text, metadata)
        """
        text_parts = []
        metadata = {
            "source": os.path.basename(file_path),
            "file_type": "pdf",
            "total_pages": 0,
            "has_tables": False,
            "has_images": False
        }

        with pdfplumber.open(file_path) as pdf:
            metadata["total_pages"] = len(pdf.pages)

            for page_num, page in enumerate(pdf.pages, 1):
                # Extract text
                page_text = page.extract_text()
                if page_text:
                    # Add page marker
                    text_parts.append(f"\n--- Page {page_num} ---\n")
                    text_parts.append(page_text)

                # Check for tables
                tables = page.extract_tables()
                if tables:
                    metadata["has_tables"] = True
                    # Convert tables to text format
                    for table in tables:
                        table_text = self._format_table(table)
                        text_parts.append(f"\n[TABLE]\n{table_text}\n[/TABLE]\n")

                # Check for images
                if page.images:
                    metadata["has_images"] = True

        full_text = "".join(text_parts)
        return full_text, metadata

    def _extract_txt(self, file_path: str) -> tuple[str, Dict[str, Any]]:
        """Extract text from TXT file"""
        with open(file_path, 'r', encoding='utf-8') as f:
            text = f.read()

        metadata = {
            "source": os.path.basename(file_path),
            "file_type": "txt",
            "total_pages": 1
        }

        return text, metadata

    def _format_table(self, table: List[List]) -> str:
        """Format table data as text"""
        if not table:
            return ""

        # Simple table formatting
        rows = []
        for row in table:
            if row:
                clean_row = [str(cell) if cell else "" for cell in row]
                rows.append(" | ".join(clean_row))

        return "\n".join(rows)

    def _clean_text(self, text: str) -> str:
        """
        Clean extracted text

        Args:
            text: Raw text

        Returns:
            Cleaned text
        """
        # Remove excessive whitespace
        lines = text.split('\n')
        cleaned_lines = []

        for line in lines:
            line = line.strip()
            if line:
                cleaned_lines.append(line)

        # Join with proper spacing
        cleaned = "\n".join(cleaned_lines)

        # Remove multiple spaces
        cleaned = " ".join(cleaned.split())

        return cleaned

    def _create_chunks(
        self,
        text: str,
        doc_metadata: Dict[str, Any]
    ) -> List[Dict[str, Any]]:
        """
        Create intelligent chunks from text

        Args:
            text: Cleaned text
            doc_metadata: Document metadata

        Returns:
            List of chunks with metadata
        """
        # Split text into chunks
        text_chunks = self.text_splitter.split_text(text)

        chunks = []
        for idx, chunk_text in enumerate(text_chunks):
            # Skip very small chunks
            if len(chunk_text) < self.config.rag.min_chunk_size:
                continue

            # Extract page number from chunk (if available)
            page_num = self._extract_page_number(chunk_text)

            # Create chunk with metadata
            chunk = {
                "chunk_id": f"{doc_metadata['source']}_chunk_{idx}",
                "text": chunk_text,
                "metadata": {
                    "source": doc_metadata["source"],
                    "chunk_index": idx,
                    "page": page_num,
                    "total_chunks": len(text_chunks),
                    "char_count": len(chunk_text),
                    **doc_metadata
                }
            }

            chunks.append(chunk)

        return chunks

    def _extract_page_number(self, chunk_text: str) -> int:
        """Extract page number from chunk text"""
        import re

        # Look for page markers
        match = re.search(r'--- Page (\d+) ---', chunk_text)
        if match:
            return int(match.group(1))

        return 1  # Default to page 1

    def extract_chunk_metadata_with_llm(self, chunk_text: str) -> Dict[str, Any]:
        """
        Use LLM to extract semantic metadata from chunk

        Args:
            chunk_text: Chunk text

        Returns:
            Extracted metadata
        """
        try:
            prompt = DOCUMENT_METADATA_EXTRACTION_PROMPT.format(
                chunk_text=chunk_text[:1000]  # Limit for token usage
            )

            response = self.llm.invoke([HumanMessage(content=prompt)])

            # Parse JSON response
            import json
            metadata = json.loads(response.content)
            return metadata

        except Exception as e:
            # Fallback to basic metadata
            return {
                "title": "Unknown",
                "keywords": [],
                "category": "General",
                "concepts": []
            }


class PDFLoaderNode:
    """
    PDF Loader Node - Batch PDF Loading

    Birden fazla PDF'i batch olarak yükler.
    """

    def __init__(self):
        self.processor = PDFProcessorNode()

    def load_directory(self, directory_path: str) -> List[DocumentProcessingState]:
        """
        Load all PDFs from directory

        Args:
            directory_path: Path to directory

        Returns:
            List of processed document states
        """
        results = []

        for filename in os.listdir(directory_path):
            if filename.endswith('.pdf'):
                file_path = os.path.join(directory_path, filename)

                # Create initial state
                state: DocumentProcessingState = {
                    "file_path": file_path,
                    "file_type": "pdf",
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
                processed_state = self.processor(state)
                results.append(processed_state)

        return results


def create_pdf_processor_node():
    """Factory function for PDF processor node"""
    return PDFProcessorNode()


def create_pdf_loader_node():
    """Factory function for PDF loader node"""
    return PDFLoaderNode()
