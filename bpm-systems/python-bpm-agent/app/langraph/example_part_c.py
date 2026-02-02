"""
LangGraph Part C - Multi-Agent & Tool Calling Examples

LLM node'ları, tool calling ve multi-agent orchestration örnekleri.
"""
import asyncio
from app.langraph.graphs.multi_agent_graph import create_multi_agent_graph
from app.langraph.nodes.llm_node import (
    create_tool_calling_llm_node,
    create_cot_llm_node,
    create_self_critic_llm_node
)
from app.langraph.tools import get_all_tools, get_tools_by_category
from app.langraph.nodes.retrieval_node import create_retrieval_node


async def example_multi_agent():
    """
    Example 1: Multi-Agent Orchestration

    Orchestrator otomatik olarak doğru agent'ı seçer.
    """
    print("\n" + "=" * 60)
    print("Example 1: Multi-Agent Orchestration")
    print("=" * 60)

    # Create retriever for RAG agent
    retriever = create_retrieval_node()

    # Create multi-agent graph
    graph = create_multi_agent_graph(retriever=retriever)

    # Test different types of questions
    questions = [
        "BPM süreçlerinde onay mekanizması nasıl çalışır?",  # RAG
        "Bugün günlerden ne?",  # Tool (date)
        "15 * 24 kaç eder?",  # Tool (calculator)
        "Merhaba, nasılsın?",  # Simple QA
    ]

    for question in questions:
        print(f"\n❓ Soru: {question}")
        print("⏳ Processing...")

        result = await graph.ainvoke(task=question)

        print(f"🤖 Agent: {result['assigned_agent']}")
        print(f"✅ Yanıt: {result['answer']}")

        if result['tools_used']:
            print(f"🔧 Kullanılan Tools: {', '.join(result['tools_used'])}")

        print("-" * 60)


async def example_tool_calling():
    """
    Example 2: Tool Calling LLM

    LLM otomatik olarak tool'ları kullanır.
    """
    print("\n" + "=" * 60)
    print("Example 2: Tool Calling LLM")
    print("=" * 60)

    # Get tools
    tools = get_all_tools()

    # Create tool-calling LLM
    tool_llm = create_tool_calling_llm_node(tools=tools)

    # Test task requiring tools
    task = "Bugünün tarihi nedir ve 2024-01-01 ile arasında kaç gün var?"

    print(f"\n📝 Task: {task}")

    # Create state
    from app.langraph.state import AgentState

    state: AgentState = {
        "task": task,
        "task_type": "tool",
        "assigned_agent": "TOOL_AGENT",
        "agent_outputs": [],
        "tools_used": [],
        "tool_results": [],
        "final_answer": "",
        "confidence": 0.0,
        "next_action": "",
        "iteration_count": 0,
        "max_iterations": 5,
        "trace": [],
        "error": None
    }

    # Execute
    result = tool_llm(state)

    print(f"\n✅ Yanıt: {result['final_answer']}")
    print(f"🔧 Kullanılan Tools: {result['tools_used']}")

    for tool_result in result['tool_results']:
        print(f"  - {tool_result['tool']}: {tool_result.get('result', tool_result.get('error'))}")


async def example_chain_of_thought():
    """
    Example 3: Chain of Thought Reasoning

    LLM adım adım düşünerek cevap verir.
    """
    print("\n" + "=" * 60)
    print("Example 3: Chain of Thought Reasoning")
    print("=" * 60)

    # Create CoT LLM
    cot_llm = create_cot_llm_node()

    task = """Bir BPM sürecinde aşağıdaki senaryoyu değerlendir:

1. Kullanıcı talep oluşturur
2. Talep otomatik olarak kategorize edilir
3. İlgili ekibe atanır
4. Ekip onaylar veya reddeder

Bu süreçte olası darboğazlar nelerdir?
"""

    print(f"\n📝 Task: {task}")

    from app.langraph.state import AgentState

    state: AgentState = {
        "task": task,
        "task_type": "analysis",
        "assigned_agent": "ANALYSIS_AGENT",
        "agent_outputs": [],
        "tools_used": [],
        "tool_results": [],
        "final_answer": "",
        "confidence": 0.0,
        "next_action": "",
        "iteration_count": 0,
        "max_iterations": 5,
        "trace": [],
        "error": None
    }

    result = cot_llm(state)

    print(f"\n✅ Yanıt:\n{result['final_answer']}")

    # Show trace if available
    if result['trace']:
        last_trace = result['trace'][-1]
        if 'reasoning' in last_trace:
            print(f"\n🧠 Reasoning: {last_trace['reasoning']}")


async def example_self_critic():
    """
    Example 4: Self-Critic LLM

    LLM kendi yanıtını değerlendirir ve iyileştirir.
    """
    print("\n" + "=" * 60)
    print("Example 4: Self-Critic LLM")
    print("=" * 60)

    # Create self-critic LLM
    critic_llm = create_self_critic_llm_node()

    task = "BPM nedir ve neden önemlidir?"

    print(f"\n📝 Task: {task}")

    from app.langraph.state import AgentState

    state: AgentState = {
        "task": task,
        "task_type": "simple_qa",
        "assigned_agent": "SIMPLE_QA_AGENT",
        "agent_outputs": [],
        "tools_used": [],
        "tool_results": [],
        "final_answer": "",
        "confidence": 0.0,
        "next_action": "",
        "iteration_count": 0,
        "max_iterations": 5,
        "trace": [],
        "error": None
    }

    result = critic_llm(state)

    print(f"\n✅ Final Answer:\n{result['final_answer']}")

    # Show critique trace
    critique_traces = [t for t in result['trace'] if t.get('node') == 'self_critic']
    if critique_traces:
        print(f"\n📊 Self-Critique:")
        for trace in critique_traces:
            if trace['action'] == 'critique':
                print(f"  Score: {trace.get('score', 'N/A')}/10")
                print(f"  Issues: {trace.get('issues', [])}")
                print(f"  Needs Revision: {trace.get('needs_revision', False)}")


async def example_error_handling():
    """
    Example 5: Error Handling

    Error handler node'u test et.
    """
    print("\n" + "=" * 60)
    print("Example 5: Error Handling")
    print("=" * 60)

    from app.langraph.nodes.error_handler_node import create_error_handler_node
    from app.langraph.state import AgentState

    error_handler = create_error_handler_node()

    # Simulate different error types
    errors = [
        ("Connection timeout", "API çağrısı"),
        ("Document not found", "Politika dökümanı ara"),
        ("Invalid input", "Kullanıcı bilgisi gir"),
    ]

    for error_msg, task in errors:
        print(f"\n❌ Error: {error_msg}")
        print(f"📝 Task: {task}")

        state: AgentState = {
            "task": task,
            "task_type": "auto",
            "assigned_agent": "",
            "agent_outputs": [],
            "tools_used": [],
            "tool_results": [],
            "final_answer": "",
            "confidence": 0.0,
            "next_action": "",
            "iteration_count": 0,
            "max_iterations": 5,
            "trace": [],
            "error": error_msg
        }

        result = error_handler(state)

        print(f"🔧 Recovery: {result['final_answer']}")
        print("-" * 60)


async def example_document_tools():
    """
    Example 6: Document Search Tools

    Document search tool'larını test et.
    """
    print("\n" + "=" * 60)
    print("Example 6: Document Search Tools")
    print("=" * 60)

    # Get document tools
    doc_tools = get_tools_by_category("document")

    print(f"\n📚 Available Document Tools:")
    for tool in doc_tools:
        print(f"  - {tool.name}: {tool.description}")

    # Test search_documents tool
    print(f"\n🔍 Testing search_documents tool:")

    search_tool = next(t for t in doc_tools if t.name == "search_documents")
    result = search_tool.invoke({"query": "BPM onay süreci", "limit": 3})

    print(f"Result: {result}")


async def run_all_examples():
    """Run all Part C examples"""
    print("\n" + "=" * 60)
    print("   LangGraph Part C - Complete Examples")
    print("=" * 60)

    # Example 1: Multi-Agent
    await example_multi_agent()

    # Example 2: Tool Calling
    await example_tool_calling()

    # Example 3: Chain of Thought
    await example_chain_of_thought()

    # Example 4: Self-Critic
    await example_self_critic()

    # Example 5: Error Handling
    await example_error_handling()

    # Example 6: Document Tools
    await example_document_tools()

    print("\n" + "=" * 60)
    print("✅ All Part C examples completed!")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(run_all_examples())
