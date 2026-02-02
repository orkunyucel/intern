"""
Agent Tools - LLM'in kullanabileceği tools

BPM sistemine özel tool'lar.
"""
from typing import Dict, Any, List
from langchain_core.tools import Tool
import json


# ============================================================================
# Document Search Tools
# ============================================================================

def search_documents_tool(query: str, limit: int = 5) -> str:
    """
    Search for documents in knowledge base

    Args:
        query: Search query
        limit: Maximum results

    Returns:
        Search results as JSON string
    """
    try:
        from app.langraph.nodes.retrieval_node import create_retrieval_node

        retriever = create_retrieval_node()
        results = retriever.search(query=query, limit=limit)

        # Format results
        formatted = []
        for doc, score in zip(results["documents"], results["scores"]):
            formatted.append({
                "source": doc["source"],
                "content": doc["content"][:200],
                "score": round(score, 2)
            })

        return json.dumps(formatted, ensure_ascii=False)

    except Exception as e:
        return json.dumps({"error": str(e)})


def get_document_by_source_tool(source_name: str) -> str:
    """
    Get specific document by source name

    Args:
        source_name: Document source name (e.g., "BPM_Policy.pdf")

    Returns:
        Document content
    """
    try:
        from app.langraph.nodes.retrieval_node import create_retrieval_node

        retriever = create_retrieval_node()
        results = retriever.search(
            query=source_name,
            limit=10,
            filters={"source": source_name}
        )

        if results["documents"]:
            content = "\n\n".join([doc["content"] for doc in results["documents"]])
            return content
        else:
            return f"Document '{source_name}' not found."

    except Exception as e:
        return f"Error: {str(e)}"


# ============================================================================
# Calculation Tools
# ============================================================================

def calculator_tool(expression: str) -> str:
    """
    Calculate mathematical expression

    Args:
        expression: Math expression (e.g., "2 + 2", "10 * 5")

    Returns:
        Calculation result
    """
    try:
        # Safe eval (only math operations)
        allowed_names = {
            "abs": abs,
            "round": round,
            "min": min,
            "max": max,
            "sum": sum,
            "pow": pow
        }

        result = eval(expression, {"__builtins__": {}}, allowed_names)
        return str(result)

    except Exception as e:
        return f"Calculation error: {str(e)}"


# ============================================================================
# Date/Time Tools
# ============================================================================

def get_current_date_tool() -> str:
    """
    Get current date and time

    Returns:
        Current date/time
    """
    from datetime import datetime
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def date_difference_tool(date1: str, date2: str) -> str:
    """
    Calculate difference between two dates

    Args:
        date1: First date (YYYY-MM-DD)
        date2: Second date (YYYY-MM-DD)

    Returns:
        Difference in days
    """
    try:
        from datetime import datetime

        d1 = datetime.strptime(date1, "%Y-%m-%d")
        d2 = datetime.strptime(date2, "%Y-%m-%d")

        diff = abs((d2 - d1).days)
        return f"{diff} gün"

    except Exception as e:
        return f"Error: {str(e)}"


# ============================================================================
# BPM Specific Tools
# ============================================================================

def list_categories_tool() -> str:
    """
    List valid BPM categories

    Returns:
        List of categories
    """
    from app.config import get_config

    config = get_config()
    categories = config.valid_categories

    return json.dumps(categories, ensure_ascii=False)


def list_teams_tool() -> str:
    """
    List available teams

    Returns:
        List of teams
    """
    from app.config import get_config

    config = get_config()
    teams = config.valid_teams

    return json.dumps(teams, ensure_ascii=False)


def check_priority_tool(urgency: str) -> str:
    """
    Determine priority level based on urgency

    Args:
        urgency: Urgency description

    Returns:
        Priority level (LOW, MEDIUM, HIGH, URGENT)
    """
    urgency_lower = urgency.lower()

    if any(word in urgency_lower for word in ["acil", "urgent", "kritik", "hemen"]):
        return "URGENT"
    elif any(word in urgency_lower for word in ["yüksek", "high", "önemli"]):
        return "HIGH"
    elif any(word in urgency_lower for word in ["orta", "medium", "normal"]):
        return "MEDIUM"
    else:
        return "LOW"


# ============================================================================
# Tool Registry
# ============================================================================

def get_all_tools() -> List[Tool]:
    """
    Get all available tools

    Returns:
        List of LangChain Tool objects
    """
    tools = [
        Tool(
            name="search_documents",
            func=search_documents_tool,
            description="Search for documents in the knowledge base. Use this when you need to find relevant policies or documentation. Input: search query (string)"
        ),
        Tool(
            name="get_document_by_source",
            func=get_document_by_source_tool,
            description="Get a specific document by its source name. Input: source name (string, e.g., 'BPM_Policy.pdf')"
        ),
        Tool(
            name="calculator",
            func=calculator_tool,
            description="Calculate mathematical expressions. Input: math expression (string, e.g., '2 + 2', '10 * 5')"
        ),
        Tool(
            name="get_current_date",
            func=get_current_date_tool,
            description="Get the current date and time. No input required."
        ),
        Tool(
            name="date_difference",
            func=date_difference_tool,
            description="Calculate difference between two dates. Input: two dates in YYYY-MM-DD format"
        ),
        Tool(
            name="list_categories",
            func=list_categories_tool,
            description="List all valid BPM categories. No input required."
        ),
        Tool(
            name="list_teams",
            func=list_teams_tool,
            description="List all available teams. No input required."
        ),
        Tool(
            name="check_priority",
            func=check_priority_tool,
            description="Determine priority level based on urgency description. Input: urgency description (string)"
        )
    ]

    return tools


def get_tools_by_category(category: str) -> List[Tool]:
    """
    Get tools by category

    Args:
        category: Tool category (document, calculation, date, bpm)

    Returns:
        List of tools in that category
    """
    all_tools = get_all_tools()

    category_map = {
        "document": ["search_documents", "get_document_by_source"],
        "calculation": ["calculator"],
        "date": ["get_current_date", "date_difference"],
        "bpm": ["list_categories", "list_teams", "check_priority"]
    }

    tool_names = category_map.get(category, [])
    return [t for t in all_tools if t.name in tool_names]
