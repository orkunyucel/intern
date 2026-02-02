"""
Production Configuration for BPM Intelligent Intake Agent

Centralizes all configuration with environment variable support.
"""
import os
from dataclasses import dataclass, field
from typing import Optional, List
from dotenv import load_dotenv

load_dotenv()


@dataclass
class GeminiConfig:
    """Gemini LLM Configuration"""
    api_key: str = field(default_factory=lambda: os.getenv("GEMINI_API_KEY", ""))
    model_name: str = field(default_factory=lambda: os.getenv("GEMINI_MODEL", "gemini-2.5-flash"))
    embedding_model: str = "models/text-embedding-004"
    temperature: float = 0.7
    max_tokens: int = 2048


@dataclass
class QdrantConfig:
    """Qdrant Vector Database Configuration"""
    host: str = field(default_factory=lambda: os.getenv("QDRANT_HOST", "localhost"))
    port: int = field(default_factory=lambda: int(os.getenv("QDRANT_PORT", "6333")))
    collection_name: str = field(default_factory=lambda: os.getenv("QDRANT_COLLECTION_NAME", "bpm_policies"))
    vector_size: int = 768


@dataclass
class RAGConfig:
    """RAG Pipeline Configuration"""
    chunk_size: int = 500
    chunk_overlap: int = 100
    min_chunk_size: int = 50
    default_limit: int = 5
    max_limit: int = 20
    score_threshold: float = 0.3
    enable_reranking: bool = True
    enable_query_expansion: bool = True


@dataclass
class BPMConfig:
    """BPM System Configuration"""
    system_name: str = field(default_factory=lambda: os.getenv("BPM_SYSTEM_NAME", "default"))
    api_url: str = field(default_factory=lambda: os.getenv("BPM_API_URL", "http://localhost:8080/api/v1"))
    api_key: Optional[str] = field(default_factory=lambda: os.getenv("BPM_API_KEY"))
    timeout: int = 30
    max_retries: int = 3
    enable_notifications: bool = True
    enable_auto_approve: bool = True
    enable_task_creation: bool = True


@dataclass
class SpeechConfig:
    """Speech Processing Configuration"""
    whisper_model: str = field(default_factory=lambda: os.getenv("WHISPER_MODEL", "base"))
    default_language: str = "tr"
    max_file_size_mb: int = 50


@dataclass
class ServerConfig:
    """FastAPI Server Configuration"""
    host: str = field(default_factory=lambda: os.getenv("SERVER_HOST", "0.0.0.0"))
    port: int = field(default_factory=lambda: int(os.getenv("SERVER_PORT", "8000")))
    debug: bool = field(default_factory=lambda: os.getenv("DEBUG", "false").lower() == "true")
    log_level: str = field(default_factory=lambda: os.getenv("LOG_LEVEL", "INFO"))


@dataclass
class AppConfig:
    """Main Application Configuration"""
    gemini: GeminiConfig = field(default_factory=GeminiConfig)
    qdrant: QdrantConfig = field(default_factory=QdrantConfig)
    rag: RAGConfig = field(default_factory=RAGConfig)
    bpm: BPMConfig = field(default_factory=BPMConfig)
    speech: SpeechConfig = field(default_factory=SpeechConfig)
    server: ServerConfig = field(default_factory=ServerConfig)
    
    # Valid categories and priorities
    valid_categories: List[str] = field(default_factory=lambda: ["TECH_SUPPORT", "BILLING", "HR", "GENERAL"])
    valid_priorities: List[str] = field(default_factory=lambda: ["LOW", "MEDIUM", "HIGH", "URGENT"])
    valid_teams: List[str] = field(default_factory=lambda: ["TechTeam", "BillingTeam", "HRTeam", "GeneralSupport", "SecurityTeam"])


# Global config instance
_config: Optional[AppConfig] = None


def get_config() -> AppConfig:
    """Get or create global configuration"""
    global _config
    if _config is None:
        _config = AppConfig()
    return _config


def reload_config() -> AppConfig:
    """Reload configuration from environment"""
    global _config
    load_dotenv(override=True)
    _config = AppConfig()
    return _config
