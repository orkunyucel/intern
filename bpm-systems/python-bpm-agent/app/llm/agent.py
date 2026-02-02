"""LLM Agent - Gemini ile karar verme (Advanced Sentiment-Aware)"""
import os
import json
import logging
from typing import Dict, Any
import google.generativeai as genai
from dotenv import load_dotenv

from app.models import IntakeRequest, AgentDecision, ToolCall
from app.rag.retriever import QdrantRetriever
from app.llm.sentiment_analyzer import SentimentAnalyzer
from app.config import get_config

load_dotenv()

logger = logging.getLogger("intake_agent")


class IntakeAgent:
    """BPM Intake Agent - Talep analizi ve karar verme (Context-aware sentiment)"""
    
    VERSION = "2.0.0"

    def __init__(self):
        config = get_config()
        api_key = config.gemini.api_key
        if not api_key:
            raise ValueError("GEMINI_API_KEY not found in environment")

        genai.configure(api_key=api_key)
        self.model = genai.GenerativeModel(config.gemini.model_name)
        self.retriever = QdrantRetriever()
        self.sentiment_analyzer = SentimentAnalyzer()
        self.config = config
        logger.info(f"IntakeAgent v{self.VERSION} initialized")

    def _build_prompt(self, request: IntakeRequest, rag_context: str, sentiment_analysis: Dict[str, Any]) -> str:
        """LLM için prompt oluştur (sentiment-aware)"""

        # Sentiment summary
        sentiment_summary = f"""
SENTIMENT ANALİZİ:
- Duygu: {sentiment_analysis['sentiment']} (Yoğunluk: {sentiment_analysis['intensity']}/10)
- Duygular: {', '.join(sentiment_analysis['emotions'])}
- Acillik gerekçesi var mı: {'EVET' if sentiment_analysis['justifies_urgency'] else 'HAYIR'}
- Açıklama: {sentiment_analysis['reasoning']}

ÖNEMLİ NOTLAR:
{f"✓ Acillik faktörleri: {', '.join(sentiment_analysis['urgency_factors'])}" if sentiment_analysis.get('urgency_factors') else ""}
{f"✗ Acil olmayan faktörler: {', '.join(sentiment_analysis['non_urgency_factors'])}" if sentiment_analysis.get('non_urgency_factors') else ""}

⚠️ UYARI: SİNİRLİ OLMAK ≠ ACİL DURUM
Müşteri çok sinirli olabilir ama sorun basit/önemsiz olabilir.
Öncelik SORUNUN kendisinden gelir, duygudan değil.
"""

        prompt = f"""Sen bir BPM Intake Agent'ısın. Görevin gelen talepleri analiz edip şirket kurallarına göre karar vermek.

GÖREV:
Gelen talebi analiz et ve şu kararları ver:
1. Intent (talep niyeti)
2. Kategori (TECH_SUPPORT, BILLING, HR, GENERAL)
3. Öncelik (LOW, MEDIUM, HIGH, URGENT)
4. Eksik bilgiler var mı
5. Otomatik onaylanabilir mi
6. Hangi tool'ları çalıştırmalı

ŞİRKET KURALLARI (RAG Context):
{rag_context}

{sentiment_summary}

MEVCUT TOOL'LAR (MCP v2.0):
- updateCategory(category, reason): Kategori günceller
- setPriority(priority, reason, escalation_note): Öncelik ayarlar
- createTask(team, description, due_date, assignee, tags): Görev oluşturur
- askMissingInfo(fields, message, deadline_hours): Eksik bilgi ister
- autoApprove(reason, conditions_met): Otomatik onaylar
- sendNotification(to, channel, message, subject, priority): Bildirim gönderir
- escalate(level, reason, notify_stakeholders): Yöneticiye escale eder
- addComment(comment, visibility, author): Dahili yorum ekler
- scheduleFollowUp(action, schedule_hours, assignee): Takip planlar

GELEN TALEP:
- Kaynak: {request.source}
- Metin: "{request.text}"
- Müşteri ID: {request.customer_id}
- Zaman: {request.timestamp}
- Duygu (basic): {request.sentiment}

ÖNEMLİ:
- Şirket kurallarına göre karar ver
- Kendi bilgini kullanma, sadece yukarıdaki context'e dayanarak karar ver
- Öncelik belirlerken zamanı ve SORUNUN CİDDİYETİNİ dikkate al
- Sentiment analizi "justifies_urgency=HAYIR" diyorsa, duygu yüzünden priority yükseltme
- Sentiment analizi "justifies_urgency=EVET" ve intensity>7 ise, priority'yi bir seviye yükselt
- Eksik bilgi varsa askMissingInfo tool'unu kullan
- Her zaman updateCategory ve setPriority çalıştır
- Uygun ekibe createTask oluştur

CEVAP FORMATI (JSON):
{{
  "intent": "kısa intent açıklaması",
  "category": "TECH_SUPPORT veya BILLING veya HR veya GENERAL",
  "priority": "LOW veya MEDIUM veya HIGH veya URGENT",
  "missing_fields": ["eksik alan1", "eksik alan2"],
  "auto_approve": true veya false,
  "tool_calls": [
    {{"tool": "updateCategory", "params": {{"category": "TECH_SUPPORT"}}}},
    {{"tool": "setPriority", "params": {{"priority": "HIGH"}}}},
    {{"tool": "createTask", "params": {{"team": "TechTeam", "description": "..."}}}}
  ],
  "reasoning": "Karar açıklaması (sentiment ve acillik ilişkisini açıkla)"
}}

Sadece JSON formatında cevap ver, başka açıklama yapma."""

        return prompt

    def analyze(self, request: IntakeRequest) -> AgentDecision:
        """Talebi analiz et ve karar ver (sentiment-aware)"""
        try:
            # 1. Advanced sentiment analysis
            logger.info(f"[SENTIMENT ANALYSIS] Starting for: {request.text[:50]}...")
            sentiment_analysis = self.sentiment_analyzer.analyze(
                request.text,
                source=request.source
            )
            logger.info(f"  Sentiment: {sentiment_analysis['sentiment']} (intensity: {sentiment_analysis['intensity']}/10)")
            logger.info(f"  Justifies urgency: {sentiment_analysis['justifies_urgency']}")
            logger.debug(f"  Reasoning: {sentiment_analysis['reasoning'][:100]}...")

            # 2. RAG context oluştur
            rag_context = self.retriever.build_rag_context(request.text, limit=5)

            # 3. Prompt oluştur (sentiment-aware)
            prompt = self._build_prompt(request, rag_context, sentiment_analysis)

            # 4. LLM call
            response = self.model.generate_content(prompt)
            response_text = response.text.strip()

            # JSON parse
            if response_text.startswith("```"):
                response_text = response_text.split("```")[1]
                if response_text.startswith("json"):
                    response_text = response_text[4:]
                response_text = response_text.strip()

            decision_data = json.loads(response_text)

            # ToolCall objelerine çevir
            tool_calls = [
                ToolCall(**tc) for tc in decision_data.get("tool_calls", [])
            ]

            # 5. Priority adjustment (sentiment-aware)
            initial_priority = decision_data["priority"]
            adjusted_priority = self.sentiment_analyzer.should_escalate_priority(
                sentiment_analysis,
                initial_priority
            )

            if adjusted_priority != initial_priority:
                logger.info(f"[PRIORITY ADJUSTMENT] {initial_priority} -> {adjusted_priority}")
                logger.info(f"  Reason: Sentiment justifies urgency (intensity: {sentiment_analysis['intensity']})")

            # AgentDecision oluştur
            decision = AgentDecision(
                intent=decision_data["intent"],
                category=decision_data["category"],
                priority=adjusted_priority,  # Adjusted priority
                missing_fields=decision_data.get("missing_fields", []),
                auto_approve=decision_data.get("auto_approve", False),
                tool_calls=tool_calls,
                reasoning=decision_data["reasoning"] + f"\n\n[Sentiment-based adjustment: {sentiment_analysis['sentiment']} (intensity: {sentiment_analysis['intensity']}) → Priority: {adjusted_priority}]"
            )

            return decision

        except Exception as e:
            logger.error(f"Agent analysis error: {e}")
            logger.error(f"Raw response: {response_text if 'response_text' in locals() else 'N/A'}")
            raise

    def analyze_dict(self, request_dict: Dict[str, Any]) -> Dict[str, Any]:
        """Dict input için helper method"""
        request = IntakeRequest(**request_dict)
        decision = self.analyze(request)
        return decision.model_dump()
