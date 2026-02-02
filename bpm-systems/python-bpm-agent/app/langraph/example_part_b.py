"""
LangGraph Part B - PDF Processing & Retrieval Examples

PDF indexing ve retrieval örnekleri.
"""
import asyncio
from app.langraph.nodes.indexing_node import create_indexing_node
from app.langraph.integration.rag_adapter import get_langraph_service


def example_pdf_indexing():
    """
    Example 1: PDF Indexing

    Bir PDF'i parse et, chunk'la ve Qdrant'a yükle.
    """
    print("\n" + "=" * 60)
    print("Example 1: PDF Indexing")
    print("=" * 60)

    # Create indexing node
    indexer = create_indexing_node()

    # Index a single PDF
    pdf_path = "/path/to/your/document.pdf"

    print(f"\n📄 Indexing: {pdf_path}")

    result = indexer.index_file(pdf_path, file_type="pdf")

    print(f"\n✅ Status: {result['status']}")
    print(f"📊 Chunks created: {result['chunks_created']}")
    print(f"💾 Vectors stored: {result['vectors_stored']}")

    if result['error']:
        print(f"❌ Error: {result['error']}")


def example_directory_indexing():
    """
    Example 2: Directory Indexing

    Bir klasördeki tüm PDF'leri index et.
    """
    print("\n" + "=" * 60)
    print("Example 2: Directory Indexing")
    print("=" * 60)

    indexer = create_indexing_node()

    # Index all PDFs in directory
    directory = "./data/policies"  # Mevcut data klasörü

    print(f"\n📁 Indexing directory: {directory}")

    results = indexer.index_directory(directory)

    print(f"\n✅ Indexed {len(results)} files:")
    for r in results:
        status_icon = "✅" if r['status'] == 'completed' else "❌"
        print(f"  {status_icon} {r['file']}: {r['chunks_created']} chunks")


async def example_rag_query():
    """
    Example 3: RAG Query

    Index edilmiş dökümanlardan soru sor.
    """
    print("\n" + "=" * 60)
    print("Example 3: RAG Query with Citations")
    print("=" * 60)

    # Get LangGraph service
    service = get_langraph_service()

    # Ask a question
    question = "BPM süreçlerinde onay mekanizması nasıl çalışır?"

    print(f"\n❓ Soru: {question}")
    print("\n⏳ Processing...")

    result = await service.ask_question(
        question=question,
        include_context=True
    )

    print(f"\n✅ Yanıt:\n{result['answer']}")

    if result.get('citations'):
        print(f"\n📚 Kaynaklar:")
        for idx, citation in enumerate(result['citations'], 1):
            print(f"  {idx}. {citation}")

    if result.get('steps'):
        print(f"\n🔍 İşlem Adımları: {' → '.join(result['steps'])}")


async def example_filtered_search():
    """
    Example 4: Filtered Search

    Metadata filter ile arama.
    """
    print("\n" + "=" * 60)
    print("Example 4: Filtered Search")
    print("=" * 60)

    service = get_langraph_service()

    # Search with filters
    query = "onay süreci"
    filters = {
        "source": "BPM_Policy.pdf"  # Sadece bu dökümanı ara
    }

    print(f"\n🔍 Query: {query}")
    print(f"🎯 Filter: {filters}")

    result = await service.search_documents(
        query=query,
        limit=3,
        filters=filters
    )

    print(f"\n📊 Found {result['total']} results:")

    for idx, doc in enumerate(result['results'], 1):
        print(f"\n{idx}. [{doc['source']}] Score: {doc['score']:.2f}")
        print(f"   {doc['content'][:150]}...")


def example_incremental_indexing():
    """
    Example 5: Incremental Indexing

    Sadece yeni dökümanları index et.
    """
    print("\n" + "=" * 60)
    print("Example 5: Incremental Indexing")
    print("=" * 60)

    from app.langraph.nodes.indexing_node import create_incremental_indexing_node

    # Create incremental indexer
    indexer = create_incremental_indexing_node()

    directory = "./data/policies"

    print(f"\n📁 Incremental indexing: {directory}")

    results = indexer.index_directory(directory)

    for r in results:
        if r['status'] == 'skipped':
            print(f"  ⏭️  {r['file']}: {r['reason']}")
        elif r['status'] == 'completed':
            print(f"  ✅ {r['file']}: {r['chunks_created']} chunks")
        else:
            print(f"  ❌ {r['file']}: {r.get('error', 'Unknown error')}")


def example_collection_stats():
    """
    Example 6: Collection Statistics

    Qdrant collection istatistikleri.
    """
    print("\n" + "=" * 60)
    print("Example 6: Collection Statistics")
    print("=" * 60)

    from app.langraph.nodes.retrieval_node import create_retrieval_node

    retriever = create_retrieval_node()

    stats = retriever.get_collection_stats()

    print(f"\n📊 Collection Stats:")
    print(f"  Total vectors: {stats.get('total_vectors', 0)}")
    print(f"  Vector size: {stats.get('vector_size', 0)}")
    print(f"  Distance metric: {stats.get('distance', 'unknown')}")


async def run_all_examples():
    """Run all examples"""
    print("\n" + "=" * 60)
    print("   LangGraph Part B - Complete Examples")
    print("=" * 60)

    # Example 1: PDF Indexing
    # example_pdf_indexing()  # Uncomment when you have PDF

    # Example 2: Directory Indexing
    # example_directory_indexing()  # Uncomment when you have directory

    # Example 3: RAG Query
    await example_rag_query()

    # Example 4: Filtered Search
    await example_filtered_search()

    # Example 5: Incremental Indexing
    # example_incremental_indexing()  # Uncomment when you have directory

    # Example 6: Collection Stats
    example_collection_stats()

    print("\n" + "=" * 60)
    print("✅ All examples completed!")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(run_all_examples())
