"""
LangGraph Example Usage

Part A: Temel RAG Graph kullanımı
"""
import asyncio
from app.langraph import create_rag_graph
from app.rag.retriever import PolicyRetriever
from app.config import get_config


async def example_rag_query():
    """
    Örnek RAG sorgusu

    Mevcut Qdrant retriever ile entegre RAG graph kullanımı.
    """
    print("🚀 LangGraph RAG Example")
    print("=" * 50)

    # Config
    config = get_config()

    # Create retriever (mevcut sistem)
    retriever = PolicyRetriever()

    # Create RAG graph
    rag_graph = create_rag_graph(retriever=retriever)

    # Test query
    query = "BPM süreçlerinde onay mekanizması nasıl çalışır?"

    print(f"\n📝 Soru: {query}")
    print("\n⏳ Processing...")

    # Invoke graph
    result = await rag_graph.ainvoke(query)

    # Print results
    print(f"\n✅ Yanıt:\n{result['answer']}")

    if result['citations']:
        print(f"\n📚 Kaynaklar:")
        for idx, citation in enumerate(result['citations'], 1):
            print(f"  {idx}. {citation}")

    if result['steps']:
        print(f"\n🔍 İşlem Adımları: {' → '.join(result['steps'])}")

    if result['error']:
        print(f"\n❌ Hata: {result['error']}")


def example_simple_usage():
    """
    Basit senkron kullanım

    Retriever olmadan test (mock data)
    """
    print("\n🚀 Simple RAG Example (No Retriever)")
    print("=" * 50)

    # Create RAG graph without retriever
    rag_graph = create_rag_graph(retriever=None)

    # Manuel olarak retrieved docs ekleyelim (test için)
    query = "Test sorusu"

    # Mock initial state with pre-retrieved docs
    from app.langraph.state import RAGState

    test_state: RAGState = {
        "query": query,
        "filters": None,
        "raw_documents": [],
        "processed_chunks": [],
        "embeddings": [],
        "retrieved_docs": [
            {
                "content": "BPM sistemlerinde onay mekanizması workflow engine tarafından yönetilir.",
                "metadata": {"page": 5, "section": "Approval Process"},
                "source": "BPM_Guide.pdf"
            },
            {
                "content": "Kullanıcı rolleri ve yetkilere göre onay adımları dinamik olarak belirlenir.",
                "metadata": {"page": 6, "section": "Dynamic Routing"},
                "source": "BPM_Guide.pdf"
            }
        ],
        "relevance_scores": [0.92, 0.87],
        "context_text": "",
        "response": "",
        "citations": [],
        "needs_clarification": False,
        "routing_decision": "",
        "step_history": [],
        "error_message": None
    }

    # Invoke with pre-populated state
    result = rag_graph.graph.invoke(test_state)

    print(f"\n📝 Soru: {query}")
    print(f"\n✅ Yanıt:\n{result['response']}")
    print(f"\n📚 Citations: {result.get('citations', [])}")


if __name__ == "__main__":
    print("\n" + "=" * 50)
    print("   LangGraph RAG - Example Usage")
    print("=" * 50)

    # Test 1: Simple usage (mock data)
    example_simple_usage()

    # Test 2: Real RAG with retriever (async)
    print("\n\n")
    asyncio.run(example_rag_query())

    print("\n" + "=" * 50)
    print("✅ Examples completed!")
    print("=" * 50)
