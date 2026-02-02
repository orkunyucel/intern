"""
Optimized RAG Retriever for BPM Policies
Features: Semantic chunking, hybrid search, re-ranking, query expansion
"""
import os
import re
import logging
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass
from enum import Enum
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct, Filter, FieldCondition, MatchValue, MatchAny
from dotenv import load_dotenv
import uuid

from app.rag.embedder import GeminiEmbedder

load_dotenv()
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("rag_retriever")


@dataclass
class RAGConfig:
    host: str = "localhost"
    port: int = 6333
    collection_name: str = "bpm_policies"
    chunk_size: int = 500
    chunk_overlap: int = 100
    min_chunk_size: int = 50
    default_limit: int = 5
    max_limit: int = 20
    score_threshold: float = 0.3
    vector_weight: float = 0.7
    keyword_weight: float = 0.3
    enable_reranking: bool = True
    rerank_top_k: int = 10
    enable_query_expansion: bool = True
    
    @classmethod
    def from_env(cls):
        return cls(
            host=os.getenv("QDRANT_HOST", "localhost"),
            port=int(os.getenv("QDRANT_PORT", "6333")),
            collection_name=os.getenv("QDRANT_COLLECTION_NAME", "bpm_policies"),
            chunk_size=int(os.getenv("RAG_CHUNK_SIZE", "500")),
            chunk_overlap=int(os.getenv("RAG_CHUNK_OVERLAP", "100")),
            score_threshold=float(os.getenv("RAG_SCORE_THRESHOLD", "0.3")),
        )


class ChunkType(str, Enum):
    RULE = "rule"
    PROCEDURE = "procedure"
    DEFINITION = "definition"
    EXAMPLE = "example"
    HEADER = "header"
    GENERAL = "general"


class SemanticChunker:
    def __init__(self, config: RAGConfig):
        self.config = config
        self.rule_markers = ["kural", "madde", "sart", "kosul"]
        self.procedure_markers = ["prosedur", "surec", "adim"]
        self.definition_markers = ["tanim", "aciklama", "nedir"]
        self.example_markers = ["ornek", "mesela", "gibi"]
    
    def detect_chunk_type(self, text: str) -> ChunkType:
        text_lower = text.lower()
        for marker in self.rule_markers:
            if marker in text_lower:
                return ChunkType.RULE
        for marker in self.procedure_markers:
            if marker in text_lower:
                return ChunkType.PROCEDURE
        for marker in self.definition_markers:
            if marker in text_lower:
                return ChunkType.DEFINITION
        for marker in self.example_markers:
            if marker in text_lower:
                return ChunkType.EXAMPLE
        return ChunkType.GENERAL
    
    def chunk_document(self, text: str, metadata: Dict[str, Any] = None) -> List[Dict[str, Any]]:
        chunks = []
        metadata = metadata or {}
        text = text.strip()
        if not text:
            return chunks
        
        paragraphs = re.split(r'\n\n+', text)
        current_chunk = ""
        chunk_position = 0
        
        for para in paragraphs:
            para = para.strip()
            if not para:
                continue
            
            if len(current_chunk) + len(para) > self.config.chunk_size:
                if current_chunk and len(current_chunk) >= self.config.min_chunk_size:
                    chunks.append(self._create_chunk(current_chunk, chunk_position, metadata))
                    chunk_position += 1
                
                if len(para) > self.config.chunk_size:
                    sub_chunks = self._split_long_paragraph(para)
                    for sub in sub_chunks:
                        chunks.append(self._create_chunk(sub, chunk_position, metadata))
                        chunk_position += 1
                    current_chunk = ""
                else:
                    if chunks and self.config.chunk_overlap > 0:
                        overlap = chunks[-1]["text"][-self.config.chunk_overlap:]
                        current_chunk = overlap + "\n\n" + para
                    else:
                        current_chunk = para
            else:
                current_chunk = current_chunk + "\n\n" + para if current_chunk else para
        
        if current_chunk and len(current_chunk) >= self.config.min_chunk_size:
            chunks.append(self._create_chunk(current_chunk, chunk_position, metadata))
        
        return chunks
    
    def _split_long_paragraph(self, text: str) -> List[str]:
        chunks = []
        sentences = re.split(r'(?<=[.!?])\s+', text)
        current = ""
        for sentence in sentences:
            if len(current) + len(sentence) > self.config.chunk_size:
                if current:
                    chunks.append(current.strip())
                current = sentence
            else:
                current = current + " " + sentence if current else sentence
        if current:
            chunks.append(current.strip())
        return chunks
    
    def _create_chunk(self, text: str, position: int, metadata: Dict) -> Dict[str, Any]:
        chunk_type = self.detect_chunk_type(text)
        return {
            "text": text.strip(),
            "chunk_type": chunk_type.value,
            "position": position,
            "char_count": len(text),
            "metadata": {**metadata, "chunk_type": chunk_type.value, "position": position}
        }


class QueryExpander:
    def __init__(self):
        self.synonyms = {
            "fatura": ["odeme", "billing", "hesap"],
            "internet": ["baglanti", "wifi", "hiz"],
            "teknik": ["ariza", "sorun", "destek"],
            "acil": ["urgent", "kritik", "hemen"],
            "sikayet": ["problem", "sorun"],
            "iade": ["geri odeme", "iptal"],
            "personel": ["calisan", "hr"],
            "izin": ["tatil", "leave"],
        }
    
    def expand(self, query: str) -> Tuple[str, List[str]]:
        query_lower = query.lower()
        additional_keywords = []
        for term, syns in self.synonyms.items():
            if term in query_lower:
                additional_keywords.extend(syns[:2])
        expanded = query
        if additional_keywords:
            expanded = f"{query} {' '.join(additional_keywords[:3])}"
        return expanded, additional_keywords


class SimpleReranker:
    def __init__(self, config: RAGConfig):
        self.config = config
        self.type_weights = {"rule": 1.3, "procedure": 1.2, "definition": 1.1, "example": 0.9, "header": 0.7, "general": 1.0}
    
    def rerank(self, results: List[Dict], query: str, keywords: List[str] = None) -> List[Dict]:
        if not results:
            return results
        keywords = keywords or []
        query_terms = set(query.lower().split())
        all_terms = query_terms | set(k.lower() for k in keywords)
        scored_results = []
        
        for result in results:
            text = result.get("text", "").lower()
            metadata = result.get("metadata", {})
            base_score = result.get("score", 0.5)
            text_terms = set(text.split())
            keyword_overlap = len(all_terms & text_terms) / max(len(all_terms), 1)
            keyword_score = keyword_overlap * self.config.keyword_weight
            chunk_type = metadata.get("chunk_type", "general")
            type_weight = self.type_weights.get(chunk_type, 1.0)
            position = metadata.get("position", 0)
            position_weight = 1.0 / (1 + position * 0.05)
            final_score = (base_score * self.config.vector_weight + keyword_score) * type_weight * position_weight
            scored_results.append({**result, "final_score": final_score})
        
        scored_results.sort(key=lambda x: x["final_score"], reverse=True)
        return scored_results


class QdrantRetriever:
    VERSION = "2.0.0"
    
    def __init__(self, config: RAGConfig = None):
        self.config = config or RAGConfig.from_env()
        self.host = self.config.host
        self.port = self.config.port
        self.collection_name = self.config.collection_name
        self.client = QdrantClient(host=self.host, port=self.port)
        self.embedder = GeminiEmbedder()
        self.chunker = SemanticChunker(self.config)
        self.query_expander = QueryExpander()
        self.reranker = SimpleReranker(self.config)
        self._ensure_collection()
        logger.info(f"QdrantRetriever v{self.VERSION} initialized")
    
    def _ensure_collection(self):
        try:
            collections = self.client.get_collections().collections
            collection_names = [c.name for c in collections]
            if self.collection_name not in collection_names:
                logger.info(f"Creating collection: {self.collection_name}")
                self.client.create_collection(
                    collection_name=self.collection_name,
                    vectors_config=VectorParams(size=768, distance=Distance.COSINE)
                )
        except Exception as e:
            logger.error(f"Error ensuring collection: {e}")
            raise
    
    def add_documents(self, documents: List[Dict[str, Any]], use_semantic_chunking: bool = True):
        try:
            points = []
            for doc in documents:
                if use_semantic_chunking:
                    chunks = self.chunker.chunk_document(doc["text"], doc.get("metadata", {}))
                    for chunk in chunks:
                        embedding = self.embedder.embed_text(chunk["text"])
                        point = PointStruct(id=str(uuid.uuid4()), vector=embedding,
                            payload={"text": chunk["text"], "chunk_type": chunk["chunk_type"],
                                    "position": chunk["position"], "metadata": chunk["metadata"]})
                        points.append(point)
                else:
                    embedding = self.embedder.embed_text(doc["text"])
                    point = PointStruct(id=str(uuid.uuid4()), vector=embedding,
                        payload={"text": doc["text"], "metadata": doc.get("metadata", {})})
                    points.append(point)
            if points:
                self.client.upsert(collection_name=self.collection_name, points=points)
                logger.info(f"Added {len(points)} documents to collection")
        except Exception as e:
            logger.error(f"Error adding documents: {e}")
            raise
    
    def search(self, query: str, limit: int = None, filter_: Filter = None,
               use_reranking: bool = None, use_query_expansion: bool = None) -> List[Dict[str, Any]]:
        limit = min(limit or self.config.default_limit, self.config.max_limit)
        use_reranking = use_reranking if use_reranking is not None else self.config.enable_reranking
        use_query_expansion = use_query_expansion if use_query_expansion is not None else self.config.enable_query_expansion
        
        try:
            expanded_query, expansion_keywords = (query, [])
            if use_query_expansion:
                expanded_query, expansion_keywords = self.query_expander.expand(query)
            
            query_vector = self.embedder.embed_query(expanded_query)
            fetch_limit = self.config.rerank_top_k if use_reranking else limit
            
            results = self.client.search(collection_name=self.collection_name, query_vector=query_vector,
                limit=fetch_limit, query_filter=filter_, score_threshold=self.config.score_threshold)
            
            documents = [{"text": r.payload.get("text", ""), "score": r.score,
                         "chunk_type": r.payload.get("chunk_type", "general"),
                         "metadata": r.payload.get("metadata", {})} for r in results]
            
            if use_reranking and len(documents) > limit:
                documents = self.reranker.rerank(documents, query, expansion_keywords)[:limit]
            return documents
        except Exception as e:
            logger.error(f"Search error: {e}")
            raise
    
    def build_rag_context(self, query: str, limit: int = 5, include_metadata: bool = False) -> str:
        try:
            documents = self.search(query, limit)
            if not documents:
                return "Ilgili dokuman bulunamadi."
            
            context_parts = []
            for i, doc in enumerate(documents, 1):
                score = doc.get("final_score", doc.get("score", 0))
                context_parts.append(f"Kaynak {i} (skor: {score:.2f}):\n{doc['text']}\n")
            return "\n".join(context_parts)
        except Exception as e:
            logger.error(f"Error building context: {e}")
            return "Context olusturulurken hata olustu."
    
    def clear_collection(self):
        try:
            self.client.delete_collection(self.collection_name)
            self._ensure_collection()
            logger.info(f"Collection {self.collection_name} cleared")
        except Exception as e:
            logger.error(f"Error clearing collection: {e}")
            raise
    
    def get_stats(self) -> Dict[str, Any]:
        try:
            count_result = self.client.count(self.collection_name)
            return {"collection_name": self.collection_name,
                    "documents_count": count_result.count if hasattr(count_result, 'count') else 0,
                    "version": self.VERSION}
        except Exception as e:
            return {"error": str(e)}
