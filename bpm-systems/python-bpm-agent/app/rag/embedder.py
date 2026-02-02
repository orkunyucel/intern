import os
from typing import List
import google.generativeai as genai
from dotenv import load_dotenv

load_dotenv()


class GeminiEmbedder:
    """Gemini API ile text embedding"""

    def __init__(self):
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            raise ValueError("GEMINI_API_KEY not found in environment")

        genai.configure(api_key=api_key)
        self.model = "models/text-embedding-004"

    def embed_text(self, text: str) -> List[float]:
        """Tek bir metni embedding'e çevir"""
        try:
            result = genai.embed_content(
                model=self.model,
                content=text,
                task_type="retrieval_document"
            )
            return result['embedding']
        except Exception as e:
            print(f"Embedding error: {e}")
            raise

    def embed_query(self, query: str) -> List[float]:
        """Query için embedding (arama için)"""
        try:
            result = genai.embed_content(
                model=self.model,
                content=query,
                task_type="retrieval_query"
            )
            return result['embedding']
        except Exception as e:
            print(f"Query embedding error: {e}")
            raise

    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        """Birden fazla metni embedding'e çevir"""
        embeddings = []
        for text in texts:
            embedding = self.embed_text(text)
            embeddings.append(embedding)
        return embeddings
