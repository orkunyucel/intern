"""Politika dokümanlarını Qdrant'a yükle"""
import os
from pathlib import Path
from app.rag.retriever import QdrantRetriever


def chunk_text(text: str, chunk_size: int = 500) -> list[str]:
    """Metni chunk'lara böl (paragraph bazlı)"""
    # Önce paragraf bazlı böl
    paragraphs = [p.strip() for p in text.split('\n\n') if p.strip()]

    chunks = []
    current_chunk = ""

    for para in paragraphs:
        if len(current_chunk) + len(para) < chunk_size:
            current_chunk += para + "\n\n"
        else:
            if current_chunk:
                chunks.append(current_chunk.strip())
            current_chunk = para + "\n\n"

    if current_chunk:
        chunks.append(current_chunk.strip())

    return chunks


def load_policies():
    """data/policies klasöründeki dosyaları yükle"""
    policies_dir = Path("data/policies")

    if not policies_dir.exists():
        print(f"Error: {policies_dir} klasörü bulunamadı")
        return

    # Retriever oluştur
    retriever = QdrantRetriever()

    # Mevcut collection'ı temizle
    print("Mevcut collection temizleniyor...")
    retriever.clear_collection()

    # Tüm txt dosyalarını oku
    documents = []

    for file_path in policies_dir.glob("*.txt"):
        print(f"Okunuyor: {file_path.name}")

        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Metni chunk'lara böl
        chunks = chunk_text(content)

        # Her chunk'ı bir doküman olarak ekle
        for i, chunk in enumerate(chunks):
            doc = {
                "text": chunk,
                "metadata": {
                    "source": file_path.name,
                    "chunk_index": i
                }
            }
            documents.append(doc)

    # Qdrant'a yükle
    print(f"\nToplam {len(documents)} doküman yükleniyor...")
    retriever.add_documents(documents)

    print("\n✅ Politika dokümanları başarıyla yüklendi!")

    # Test search
    print("\n--- Test Arama ---")
    query = "internet kesintisi"
    print(f"Query: '{query}'")
    results = retriever.search(query, limit=3)

    for i, result in enumerate(results, 1):
        print(f"\n{i}. Sonuç (skor: {result['score']:.3f}):")
        print(f"   {result['text'][:200]}...")


if __name__ == "__main__":
    load_policies()
