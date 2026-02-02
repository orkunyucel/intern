from pydantic import BaseModel, Field
from typing import List, Optional, Literal
from datetime import datetime


class IntakeRequest(BaseModel):
    """Gelen talep modeli"""
    source: str = Field(
        description="Talep kaynağı (form, email, phone, flowable, etc.)"
    )
    text: str = Field(
        description="Talep metni"
    )
    customer_id: str = Field(
        description="Müşteri ID"
    )
    timestamp: str = Field(
        default_factory=lambda: datetime.now().isoformat(),
        description="Talep zamanı"
    )
    sentiment: Optional[Literal["NEUTRAL", "HAPPY", "ANGRY", "FRUSTRATED"]] = Field(
        default="NEUTRAL",
        description="Müşteri duygu durumu (telefon için)"
    )


class ToolCall(BaseModel):
    """LLM'in çağırdığı tool"""
    tool: str = Field(description="Tool adı")
    params: dict = Field(description="Tool parametreleri")


class AgentDecision(BaseModel):
    """Agent'ın kararı"""
    intent: str = Field(description="Talep niyeti")
    category: Literal["TECH_SUPPORT", "BILLING", "HR", "GENERAL"] = Field(
        description="Kategori"
    )
    priority: Literal["LOW", "MEDIUM", "HIGH", "URGENT"] = Field(
        description="Öncelik"
    )
    missing_fields: List[str] = Field(
        default_factory=list,
        description="Eksik alanlar"
    )
    auto_approve: bool = Field(
        default=False,
        description="Otomatik onaylanabilir mi"
    )
    tool_calls: List[ToolCall] = Field(
        default_factory=list,
        description="Çalıştırılacak tool'lar"
    )
    reasoning: str = Field(
        description="Karar açıklaması"
    )


class IntakeResponse(BaseModel):
    """API response modeli"""
    case_id: str = Field(description="Oluşturulan case ID")
    intent: str
    category: str
    priority: str
    missing_fields: List[str]
    auto_approve: bool
    actions_taken: List[str] = Field(
        default_factory=list,
        description="Yapılan aksiyonlar"
    )
    reasoning: str


class PolicyDocument(BaseModel):
    """Politika dokümanı modeli"""
    id: str
    text: str
    metadata: dict = Field(default_factory=dict)
