"""
Error Handler Node - Error Recovery & Retry Logic

Hata durumlarını yönetir ve recovery stratejileri uygular.
"""
from typing import Dict, Any, Optional
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import HumanMessage

from app.langraph.state import AgentState
from app.langraph.prompts.system_prompts import ERROR_RECOVERY_PROMPT
from app.config import get_config


class ErrorHandlerNode:
    """
    Error Handler Node

    Özellikleri:
    - Error classification
    - Recovery strategy selection
    - User-friendly error messages
    - Retry logic
    """

    def __init__(self):
        config = get_config()
        self.llm = ChatGoogleGenerativeAI(
            model=config.gemini.model_name,
            google_api_key=config.gemini.api_key,
            temperature=0.3,
            max_tokens=512
        )

    def __call__(self, state: AgentState) -> AgentState:
        """
        Handle error gracefully

        Args:
            state: Agent state with error

        Returns:
            Updated state with recovery action
        """
        error = state.get("error", "Unknown error")
        task = state.get("task", "")

        # Classify error
        error_type = self._classify_error(error)

        # Get recovery strategy
        recovery = self._get_recovery_strategy(error_type, task, error)

        # Update state
        state["final_answer"] = recovery["message"]
        state["trace"].append({
            "node": "error_handler",
            "error_type": error_type,
            "recovery_strategy": recovery["strategy"],
            "user_message": recovery["message"]
        })

        # Clear error if recovered
        if recovery["strategy"] != "fail":
            state["error"] = None

        return state

    def _classify_error(self, error: str) -> str:
        """
        Classify error type

        Args:
            error: Error message

        Returns:
            Error type
        """
        error_lower = str(error).lower()

        if "timeout" in error_lower or "connection" in error_lower:
            return "network"
        elif "not found" in error_lower or "404" in error_lower:
            return "not_found"
        elif "unauthorized" in error_lower or "401" in error_lower:
            return "auth"
        elif "rate limit" in error_lower or "429" in error_lower:
            return "rate_limit"
        elif "invalid" in error_lower or "validation" in error_lower:
            return "validation"
        elif "api" in error_lower:
            return "api"
        else:
            return "unknown"

    def _get_recovery_strategy(
        self,
        error_type: str,
        task: str,
        error: str
    ) -> Dict[str, Any]:
        """
        Get recovery strategy based on error type

        Args:
            error_type: Classified error type
            task: Original task
            error: Error message

        Returns:
            Recovery dict with strategy and message
        """
        strategies = {
            "network": {
                "strategy": "retry",
                "message": "Bağlantı sorunu yaşandı. Lütfen tekrar deneyin."
            },
            "not_found": {
                "strategy": "alternative",
                "message": self._suggest_alternative(task)
            },
            "auth": {
                "strategy": "fail",
                "message": "Yetkilendirme hatası. Lütfen sistem yöneticisine başvurun."
            },
            "rate_limit": {
                "strategy": "wait",
                "message": "Sistem yoğun. Lütfen birkaç saniye bekleyip tekrar deneyin."
            },
            "validation": {
                "strategy": "clarify",
                "message": self._request_clarification(task, error)
            },
            "api": {
                "strategy": "fallback",
                "message": "API hatası oluştu. Alternatif yöntem deneniyor..."
            },
            "unknown": {
                "strategy": "generic",
                "message": "Beklenmeyen bir hata oluştu. Lütfen sorunuzu yeniden ifade edin."
            }
        }

        return strategies.get(error_type, strategies["unknown"])

    def _suggest_alternative(self, task: str) -> str:
        """Suggest alternative approach when resource not found"""
        prompt = f"""Kullanıcının isteği: {task}

İstenilen kaynak bulunamadı. Kullanıcıya alternatif önerilerde bulun:
- Başka nasıl arayabilir?
- Hangi kaynaklara bakabilir?
- Sorusunu nasıl değiştirebilir?

Kısa ve yardımcı bir yanıt ver.
"""

        try:
            response = self.llm.invoke([HumanMessage(content=prompt)])
            return response.content
        except:
            return "İstenilen bilgi bulunamadı. Lütfen farklı kelimelerle aramayı deneyin."

    def _request_clarification(self, task: str, error: str) -> str:
        """Request clarification from user"""
        prompt = f"""Kullanıcı sorusu: {task}
Hata: {error}

Kullanıcıdan ek bilgi iste. Hangi bilgiler eksik veya belirsiz?
Maksimum 2 soru sor.
"""

        try:
            response = self.llm.invoke([HumanMessage(content=prompt)])
            return response.content
        except:
            return "Sorunuzu daha detaylı açıklayabilir misiniz?"


class RetryNode:
    """
    Retry Node - Automatic Retry Logic

    Belirli hatalarda otomatik retry yapar.
    """

    def __init__(self, max_retries: int = 3, backoff_factor: float = 1.5):
        self.max_retries = max_retries
        self.backoff_factor = backoff_factor

    def __call__(
        self,
        state: AgentState,
        operation_func,
        *args,
        **kwargs
    ) -> AgentState:
        """
        Execute operation with retry logic

        Args:
            state: Agent state
            operation_func: Function to retry
            *args, **kwargs: Function arguments

        Returns:
            Updated state
        """
        import time

        retry_count = 0
        last_error = None

        while retry_count < self.max_retries:
            try:
                # Execute operation
                result = operation_func(*args, **kwargs)
                state["trace"].append({
                    "node": "retry",
                    "attempt": retry_count + 1,
                    "success": True
                })
                return result

            except Exception as e:
                last_error = e
                retry_count += 1

                if retry_count < self.max_retries:
                    # Exponential backoff
                    wait_time = self.backoff_factor ** retry_count
                    time.sleep(wait_time)

                    state["trace"].append({
                        "node": "retry",
                        "attempt": retry_count,
                        "error": str(e),
                        "wait_time": wait_time
                    })

        # All retries failed
        state["error"] = f"Operation failed after {self.max_retries} retries: {last_error}"
        return state


class FallbackNode:
    """
    Fallback Node - Alternative Execution Paths

    Primary method başarısız olursa fallback method kullanır.
    """

    def __init__(self, primary_node, fallback_node):
        """
        Args:
            primary_node: Primary node to try first
            fallback_node: Fallback node if primary fails
        """
        self.primary_node = primary_node
        self.fallback_node = fallback_node

    def __call__(self, state: AgentState) -> AgentState:
        """
        Execute with fallback

        Args:
            state: Agent state

        Returns:
            Updated state
        """
        try:
            # Try primary
            result_state = self.primary_node(state)

            # Check if primary succeeded
            if not result_state.get("error"):
                result_state["trace"].append({
                    "node": "fallback",
                    "executed": "primary",
                    "success": True
                })
                return result_state

            # Primary failed, try fallback
            state["trace"].append({
                "node": "fallback",
                "executed": "primary",
                "success": False,
                "error": result_state.get("error")
            })

            result_state = self.fallback_node(state)
            result_state["trace"].append({
                "node": "fallback",
                "executed": "fallback",
                "success": not result_state.get("error")
            })

            return result_state

        except Exception as e:
            # Both failed
            state["error"] = f"Both primary and fallback failed: {str(e)}"
            return state


def create_error_handler_node() -> ErrorHandlerNode:
    """Factory for error handler node"""
    return ErrorHandlerNode()


def create_retry_node(max_retries: int = 3) -> RetryNode:
    """Factory for retry node"""
    return RetryNode(max_retries=max_retries)


def create_fallback_node(primary_node, fallback_node) -> FallbackNode:
    """Factory for fallback node"""
    return FallbackNode(primary_node, fallback_node)
