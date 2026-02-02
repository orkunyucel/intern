"""
Orchestrator Node - Multi-Agent Coordination

Bu node, gelen soruları analiz eder ve doğru agent'a yönlendirir.
Kritik routing kararlarını verir.
"""
import json
from typing import Dict, Any
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import SystemMessage, HumanMessage

from app.langraph.state import AgentState
from app.langraph.prompts.system_prompts import (
    ORCHESTRATOR_SYSTEM_PROMPT,
    ORCHESTRATOR_ROUTING_PROMPT
)
from app.config import get_config


class OrchestratorNode:
    """
    Orchestrator Node - Agent Routing & Coordination

    Özellikleri:
    - Soruyu analiz eder
    - Complexity level belirler
    - En uygun agent'ı seçer
    - Multi-agent coordination sağlar
    """

    def __init__(self):
        config = get_config()
        self.llm = ChatGoogleGenerativeAI(
            model=config.gemini.model_name,
            google_api_key=config.gemini.api_key,
            temperature=0.3,  # Routing için düşük temperature
            max_tokens=1024
        )

    def __call__(self, state: AgentState) -> AgentState:
        """
        Ana orchestrator logic

        Args:
            state: Current agent state

        Returns:
            Updated state with routing decision
        """
        try:
            task = state["task"]
            chat_history = state.get("agent_outputs", [])

            # Routing decision
            routing_decision = self._route_task(task, chat_history)

            # Update state
            state["assigned_agent"] = routing_decision["agent"]
            state["routing_decision"] = routing_decision["agent"]
            state["trace"].append({
                "node": "orchestrator",
                "action": "route",
                "decision": routing_decision,
                "reasoning": routing_decision.get("reasoning", "")
            })

            return state

        except Exception as e:
            state["error"] = f"Orchestrator error: {str(e)}"
            state["assigned_agent"] = "SIMPLE_QA_AGENT"  # Fallback
            return state

    def _route_task(self, task: str, chat_history: list) -> Dict[str, Any]:
        """
        Task routing logic

        Args:
            task: User task/question
            chat_history: Previous conversation

        Returns:
            Routing decision with agent name and reasoning
        """
        # Format chat history
        history_str = self._format_chat_history(chat_history)

        # Create prompt
        prompt_text = ORCHESTRATOR_ROUTING_PROMPT.format(
            question=task,
            chat_history=history_str
        )

        messages = [
            SystemMessage(content=ORCHESTRATOR_SYSTEM_PROMPT),
            HumanMessage(content=prompt_text)
        ]

        # Get routing decision
        response = self.llm.invoke(messages)

        try:
            # Parse JSON response
            decision = json.loads(response.content)

            # Validate agent name
            valid_agents = ["RAG_AGENT", "SIMPLE_QA_AGENT", "ANALYSIS_AGENT", "TOOL_AGENT"]
            if decision["agent"] not in valid_agents:
                decision["agent"] = self._fallback_routing(task)

            return decision

        except json.JSONDecodeError:
            # Fallback routing
            return {
                "agent": self._fallback_routing(task),
                "reasoning": "JSON parse error, using fallback routing",
                "complexity": "unknown"
            }

    def _fallback_routing(self, task: str) -> str:
        """
        Basit keyword-based fallback routing

        Args:
            task: User task

        Returns:
            Agent name
        """
        task_lower = task.lower()

        # RAG keywords
        rag_keywords = ["policy", "politika", "prosedür", "döküman", "document", "pdf"]
        if any(keyword in task_lower for keyword in rag_keywords):
            return "RAG_AGENT"

        # Analysis keywords
        analysis_keywords = ["analiz", "karşılaştır", "özetle", "compare", "analyze"]
        if any(keyword in task_lower for keyword in analysis_keywords):
            return "ANALYSIS_AGENT"

        # Tool keywords
        tool_keywords = ["hesapla", "listele", "filtrele", "calculate", "list"]
        if any(keyword in task_lower for keyword in tool_keywords):
            return "TOOL_AGENT"

        # Default
        return "SIMPLE_QA_AGENT"

    def _format_chat_history(self, chat_history: list) -> str:
        """Format chat history for prompt"""
        if not chat_history:
            return "No previous conversation"

        formatted = []
        for entry in chat_history[-3:]:  # Son 3 mesaj
            agent = entry.get("agent", "unknown")
            output = entry.get("output", "")
            formatted.append(f"[{agent}]: {output[:200]}")

        return "\n".join(formatted)


def create_orchestrator_node():
    """Factory function for orchestrator node"""
    orchestrator = OrchestratorNode()
    return orchestrator
