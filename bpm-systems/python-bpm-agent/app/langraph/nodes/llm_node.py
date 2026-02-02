"""
LLM Node - Language Model Integration

Gemini LLM ile etkileşim, tool calling, function calling.
"""
import json
from typing import Dict, Any, List, Optional, Callable
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import SystemMessage, HumanMessage, AIMessage
from langchain_core.tools import Tool

from app.langraph.state import AgentState
from app.langraph.prompts.system_prompts import SIMPLE_QA_SYSTEM_PROMPT
from app.config import get_config


class LLMNode:
    """
    LLM Node - Basic LLM Interaction

    Özellikleri:
    - Gemini 2.5 Flash integration
    - System prompt support
    - Chat history management
    - Streaming support (optional)
    """

    def __init__(
        self,
        system_prompt: str = None,
        temperature: float = None,
        max_tokens: int = None
    ):
        config = get_config()

        self.system_prompt = system_prompt or SIMPLE_QA_SYSTEM_PROMPT
        self.temperature = temperature or config.gemini.temperature
        self.max_tokens = max_tokens or config.gemini.max_tokens

        self.llm = ChatGoogleGenerativeAI(
            model=config.gemini.model_name,
            google_api_key=config.gemini.api_key,
            temperature=self.temperature,
            max_tokens=self.max_tokens
        )

    def __call__(self, state: AgentState) -> AgentState:
        """
        Execute LLM call

        Args:
            state: Agent state

        Returns:
            Updated state with LLM response
        """
        try:
            task = state["task"]
            agent_outputs = state.get("agent_outputs", [])

            # Build messages
            messages = self._build_messages(task, agent_outputs)

            # Call LLM
            response = self.llm.invoke(messages)

            # Update state
            state["final_answer"] = response.content
            state["agent_outputs"].append({
                "agent": "llm_node",
                "output": response.content,
                "timestamp": self._get_timestamp()
            })

            state["trace"].append({
                "node": "llm_node",
                "action": "generate",
                "input": task,
                "output": response.content[:200]
            })

            return state

        except Exception as e:
            state["error"] = f"LLM error: {str(e)}"
            state["final_answer"] = "Üzgünüm, bir hata oluştu."
            return state

    def _build_messages(self, task: str, history: List[Dict]) -> List:
        """Build message list for LLM"""
        messages = [SystemMessage(content=self.system_prompt)]

        # Add history (son 5 mesaj)
        for entry in history[-5:]:
            content = entry.get("output", "")
            messages.append(AIMessage(content=content))

        # Add current task
        messages.append(HumanMessage(content=task))

        return messages

    def _get_timestamp(self) -> str:
        """Get current timestamp"""
        from datetime import datetime
        return datetime.now().isoformat()


class ToolCallingLLMNode(LLMNode):
    """
    Tool Calling LLM Node

    Özellikleri:
    - Function calling
    - Tool execution
    - Multi-step reasoning
    - Tool result integration
    """

    def __init__(
        self,
        system_prompt: str = None,
        tools: List[Tool] = None,
        max_iterations: int = 5
    ):
        super().__init__(system_prompt=system_prompt)

        self.tools = tools or []
        self.max_iterations = max_iterations

        # Create tool map
        self.tool_map = {tool.name: tool for tool in self.tools}

        # Bind tools to LLM if available
        if self.tools:
            self.llm_with_tools = self.llm.bind_tools(self.tools)
        else:
            self.llm_with_tools = self.llm

    def __call__(self, state: AgentState) -> AgentState:
        """
        Execute LLM with tool calling

        Args:
            state: Agent state

        Returns:
            Updated state with tool results
        """
        try:
            task = state["task"]
            iteration = state.get("iteration_count", 0)
            max_iterations = state.get("max_iterations", self.max_iterations)

            # Check iteration limit
            if iteration >= max_iterations:
                state["final_answer"] = "Maximum iterations reached."
                state["next_action"] = "end"
                return state

            # Build messages
            messages = self._build_messages(task, state.get("agent_outputs", []))

            # Call LLM
            response = self.llm_with_tools.invoke(messages)

            # Check for tool calls
            if hasattr(response, 'tool_calls') and response.tool_calls:
                # Execute tools
                tool_results = self._execute_tools(response.tool_calls)

                # Update state
                state["tools_used"].extend([tc["name"] for tc in response.tool_calls])
                state["tool_results"].extend(tool_results)

                state["next_action"] = "continue"  # More iterations needed
                state["iteration_count"] = iteration + 1

                # Add to trace
                state["trace"].append({
                    "node": "tool_calling_llm",
                    "action": "tool_call",
                    "tools": [tc["name"] for tc in response.tool_calls],
                    "results": tool_results
                })

            else:
                # No tool calls, final answer
                state["final_answer"] = response.content
                state["next_action"] = "end"

                state["agent_outputs"].append({
                    "agent": "tool_calling_llm",
                    "output": response.content,
                    "timestamp": self._get_timestamp()
                })

            return state

        except Exception as e:
            state["error"] = f"Tool calling LLM error: {str(e)}"
            state["final_answer"] = "Tool execution failed."
            state["next_action"] = "end"
            return state

    def _execute_tools(self, tool_calls: List[Dict]) -> List[Dict[str, Any]]:
        """
        Execute tool calls

        Args:
            tool_calls: List of tool calls from LLM

        Returns:
            List of tool results
        """
        results = []

        for tool_call in tool_calls:
            tool_name = tool_call["name"]
            tool_args = tool_call.get("args", {})

            if tool_name in self.tool_map:
                try:
                    # Execute tool
                    tool = self.tool_map[tool_name]
                    result = tool.invoke(tool_args)

                    results.append({
                        "tool": tool_name,
                        "args": tool_args,
                        "result": result,
                        "success": True
                    })

                except Exception as e:
                    results.append({
                        "tool": tool_name,
                        "args": tool_args,
                        "error": str(e),
                        "success": False
                    })
            else:
                results.append({
                    "tool": tool_name,
                    "error": f"Tool {tool_name} not found",
                    "success": False
                })

        return results


class ChainOfThoughtLLMNode(LLMNode):
    """
    Chain of Thought LLM Node

    Step-by-step reasoning için optimize edilmiş LLM node.
    """

    def __init__(self, system_prompt: str = None):
        cot_prompt = system_prompt or """Sen bir BPM uzmanısın.

Soruları cevaplarken:
1. Problemi parçalara böl
2. Her adımı açıkla
3. Reasoning'ini göster
4. Final cevabı net ver

Format:
**Düşünce:** [reasoning]
**Adımlar:** [step by step]
**Cevap:** [final answer]
"""
        super().__init__(system_prompt=cot_prompt, temperature=0.3)

    def __call__(self, state: AgentState) -> AgentState:
        """Execute with chain of thought"""
        state = super().__call__(state)

        # Parse structured response
        if not state.get("error"):
            response = state["final_answer"]
            parsed = self._parse_cot_response(response)

            state["trace"].append({
                "node": "chain_of_thought",
                "reasoning": parsed.get("reasoning", ""),
                "steps": parsed.get("steps", []),
                "answer": parsed.get("answer", "")
            })

        return state

    def _parse_cot_response(self, response: str) -> Dict[str, Any]:
        """Parse chain of thought response"""
        import re

        parsed = {
            "reasoning": "",
            "steps": [],
            "answer": ""
        }

        # Extract reasoning
        reasoning_match = re.search(r'\*\*Düşünce:\*\*\s*(.+?)(?=\*\*|$)', response, re.DOTALL)
        if reasoning_match:
            parsed["reasoning"] = reasoning_match.group(1).strip()

        # Extract steps
        steps_match = re.search(r'\*\*Adımlar:\*\*\s*(.+?)(?=\*\*|$)', response, re.DOTALL)
        if steps_match:
            steps_text = steps_match.group(1).strip()
            parsed["steps"] = [s.strip() for s in steps_text.split('\n') if s.strip()]

        # Extract answer
        answer_match = re.search(r'\*\*Cevap:\*\*\s*(.+?)$', response, re.DOTALL)
        if answer_match:
            parsed["answer"] = answer_match.group(1).strip()
        else:
            parsed["answer"] = response  # Fallback

        return parsed


class SelfCriticLLMNode:
    """
    Self-Critic LLM Node

    LLM kendi yanıtını değerlendirir ve gerekirse düzeltir.
    """

    def __init__(self):
        self.generator = LLMNode(temperature=0.7)
        self.critic = LLMNode(
            system_prompt="Sen bir yanıt değerlendirme uzmanısın. Yanıtları doğruluk, tamlık ve netlik açısından değerlendir.",
            temperature=0.3
        )

    def __call__(self, state: AgentState) -> AgentState:
        """
        Generate → Critique → Revise

        Args:
            state: Agent state

        Returns:
            Updated state with refined answer
        """
        # Step 1: Generate initial answer
        state = self.generator(state)
        initial_answer = state.get("final_answer", "")

        # Step 2: Critique
        critique_task = f"""Aşağıdaki yanıtı değerlendir:

Soru: {state['task']}
Yanıt: {initial_answer}

Değerlendirme kriterleri:
- Doğruluk
- Tamlık
- Netlik
- İyileştirme önerileri

JSON formatında döndür:
{{
    "score": 0-10,
    "issues": ["issue1", "issue2"],
    "suggestions": ["suggestion1"],
    "needs_revision": true/false
}}
"""

        critique_state = {
            "task": critique_task,
            "agent_outputs": [],
            "trace": []
        }

        critique_state = self.critic(critique_state)
        critique_response = critique_state.get("final_answer", "{}")

        # Parse critique
        try:
            critique = json.loads(critique_response)
            needs_revision = critique.get("needs_revision", False)

            # Add critique to trace
            state["trace"].append({
                "node": "self_critic",
                "action": "critique",
                "score": critique.get("score", 0),
                "issues": critique.get("issues", []),
                "needs_revision": needs_revision
            })

            # Step 3: Revise if needed
            if needs_revision and critique.get("suggestions"):
                revision_task = f"""Önceki yanıtını şu önerilere göre düzelt:

Orijinal Soru: {state['task']}
Orijinal Yanıt: {initial_answer}
Öneriler: {', '.join(critique.get('suggestions', []))}

Düzeltilmiş yanıt:
"""
                revision_state = {
                    "task": revision_task,
                    "agent_outputs": [],
                    "trace": []
                }

                revision_state = self.generator(revision_state)
                state["final_answer"] = revision_state.get("final_answer", initial_answer)

                state["trace"].append({
                    "node": "self_critic",
                    "action": "revise",
                    "original": initial_answer[:100],
                    "revised": state["final_answer"][:100]
                })

        except json.JSONDecodeError:
            # Critique parsing failed, keep original
            pass

        return state


def create_llm_node(system_prompt: str = None) -> LLMNode:
    """Factory for basic LLM node"""
    return LLMNode(system_prompt=system_prompt)


def create_tool_calling_llm_node(tools: List[Tool] = None) -> ToolCallingLLMNode:
    """Factory for tool calling LLM node"""
    return ToolCallingLLMNode(tools=tools)


def create_cot_llm_node() -> ChainOfThoughtLLMNode:
    """Factory for chain of thought LLM node"""
    return ChainOfThoughtLLMNode()


def create_self_critic_llm_node() -> SelfCriticLLMNode:
    """Factory for self-critic LLM node"""
    return SelfCriticLLMNode()
