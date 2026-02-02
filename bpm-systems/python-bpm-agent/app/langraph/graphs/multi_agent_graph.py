"""
Multi-Agent Orchestration Graph

Birden fazla agent'ı koordine eden master graph.
"""
from typing import Dict, Any, Literal
from langgraph.graph import StateGraph, END
from langchain_core.runnables import RunnableConfig

from app.langraph.state import AgentState
from app.langraph.nodes.orchestrator_node import create_orchestrator_node
from app.langraph.nodes.rag_node import create_rag_node
from app.langraph.nodes.llm_node import (
    create_llm_node,
    create_tool_calling_llm_node,
    create_cot_llm_node
)
from app.langraph.tools import get_all_tools
from app.langraph.prompts.system_prompts import (
    SIMPLE_QA_SYSTEM_PROMPT,
    ANALYSIS_SYSTEM_PROMPT
)


class MultiAgentGraph:
    """
    Multi-Agent Orchestration Graph

    Flow:
    START → orchestrator → route_to_agent → [agent execution] → END

    Agents:
    - RAG_AGENT: Document Q&A
    - SIMPLE_QA_AGENT: General questions
    - ANALYSIS_AGENT: Complex analysis
    - TOOL_AGENT: Tool-based tasks
    """

    def __init__(self, retriever=None):
        """
        Args:
            retriever: Document retriever for RAG agent
        """
        self.retriever = retriever

        # Create nodes
        self.orchestrator = create_orchestrator_node()
        self.rag_agent = create_rag_node(retriever=retriever)
        self.simple_qa_agent = create_llm_node(system_prompt=SIMPLE_QA_SYSTEM_PROMPT)
        self.analysis_agent = create_cot_llm_node()
        self.tool_agent = create_tool_calling_llm_node(tools=get_all_tools())

        # Build graph
        self.graph = self._build_graph()

    def _build_graph(self) -> StateGraph:
        """Build the multi-agent graph"""

        # Create workflow
        workflow = StateGraph(AgentState)

        # Add nodes
        workflow.add_node("orchestrator", self.orchestrator)
        workflow.add_node("rag_agent", self._execute_rag_agent)
        workflow.add_node("simple_qa_agent", self.simple_qa_agent)
        workflow.add_node("analysis_agent", self.analysis_agent)
        workflow.add_node("tool_agent", self._execute_tool_agent)

        # Set entry point
        workflow.set_entry_point("orchestrator")

        # Conditional routing after orchestrator
        workflow.add_conditional_edges(
            "orchestrator",
            self._route_to_agent,
            {
                "rag": "rag_agent",
                "simple_qa": "simple_qa_agent",
                "analysis": "analysis_agent",
                "tool": "tool_agent"
            }
        )

        # All agents go to END
        workflow.add_edge("rag_agent", END)
        workflow.add_edge("simple_qa_agent", END)
        workflow.add_edge("analysis_agent", END)

        # Tool agent might loop
        workflow.add_conditional_edges(
            "tool_agent",
            self._should_continue_tools,
            {
                "continue": "tool_agent",
                "end": END
            }
        )

        return workflow.compile()

    def _route_to_agent(self, state: AgentState) -> str:
        """
        Route to appropriate agent based on orchestrator decision

        Args:
            state: Agent state

        Returns:
            Agent route
        """
        assigned_agent = state.get("assigned_agent", "SIMPLE_QA_AGENT")

        agent_map = {
            "RAG_AGENT": "rag",
            "SIMPLE_QA_AGENT": "simple_qa",
            "ANALYSIS_AGENT": "analysis",
            "TOOL_AGENT": "tool"
        }

        return agent_map.get(assigned_agent, "simple_qa")

    def _execute_rag_agent(self, state: AgentState) -> AgentState:
        """
        Execute RAG agent (adapts AgentState to RAGState)

        Args:
            state: Agent state

        Returns:
            Updated agent state
        """
        from app.langraph.state import RAGState

        # Convert to RAG state
        rag_state: RAGState = {
            "query": state["task"],
            "filters": None,
            "raw_documents": [],
            "processed_chunks": [],
            "embeddings": [],
            "retrieved_docs": [],
            "relevance_scores": [],
            "context_text": "",
            "response": "",
            "citations": [],
            "needs_clarification": False,
            "routing_decision": "",
            "step_history": [],
            "error_message": None
        }

        # Execute RAG
        result_state = self.rag_agent(rag_state)

        # Update agent state
        state["final_answer"] = result_state.get("response", "")
        state["agent_outputs"].append({
            "agent": "rag_agent",
            "output": result_state.get("response", ""),
            "citations": result_state.get("citations", []),
            "context": result_state.get("context_text", "")
        })

        state["trace"].append({
            "node": "rag_agent",
            "action": "complete",
            "retrieved_docs": len(result_state.get("retrieved_docs", []))
        })

        return state

    def _execute_tool_agent(self, state: AgentState) -> AgentState:
        """Execute tool agent with iteration tracking"""
        state = self.tool_agent(state)

        # Update iteration count
        state["iteration_count"] = state.get("iteration_count", 0) + 1

        return state

    def _should_continue_tools(self, state: AgentState) -> str:
        """
        Decide if tool agent should continue

        Args:
            state: Agent state

        Returns:
            "continue" or "end"
        """
        next_action = state.get("next_action", "end")
        iteration = state.get("iteration_count", 0)
        max_iterations = state.get("max_iterations", 5)

        if next_action == "continue" and iteration < max_iterations:
            return "continue"
        else:
            return "end"

    def invoke(self, task: str, task_type: str = "auto") -> Dict[str, Any]:
        """
        Invoke the multi-agent graph

        Args:
            task: User task/question
            task_type: Task type hint (auto, simple_qa, rag, etc.)

        Returns:
            Response dict
        """
        # Initialize state
        initial_state: AgentState = {
            "task": task,
            "task_type": task_type,
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
            "error": None
        }

        # Run graph
        result = self.graph.invoke(initial_state)

        return {
            "answer": result.get("final_answer", ""),
            "assigned_agent": result.get("assigned_agent", "unknown"),
            "agent_outputs": result.get("agent_outputs", []),
            "tools_used": result.get("tools_used", []),
            "trace": result.get("trace", []),
            "error": result.get("error")
        }

    async def ainvoke(self, task: str, task_type: str = "auto") -> Dict[str, Any]:
        """Async version of invoke"""
        initial_state: AgentState = {
            "task": task,
            "task_type": task_type,
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
            "error": None
        }

        result = await self.graph.ainvoke(initial_state)

        return {
            "answer": result.get("final_answer", ""),
            "assigned_agent": result.get("assigned_agent", "unknown"),
            "agent_outputs": result.get("agent_outputs", []),
            "tools_used": result.get("tools_used", []),
            "trace": result.get("trace", []),
            "error": result.get("error")
        }


def create_multi_agent_graph(retriever=None) -> MultiAgentGraph:
    """
    Factory function for multi-agent graph

    Args:
        retriever: Document retriever

    Returns:
        MultiAgentGraph instance
    """
    return MultiAgentGraph(retriever=retriever)
