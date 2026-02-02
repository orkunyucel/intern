"""Advanced Sentiment Analyzer - Context-aware emotion detection"""
import os
import google.generativeai as genai
from dotenv import load_dotenv
from typing import Dict, Any
import json

load_dotenv()


class SentimentAnalyzer:
    """
    Gelişmiş sentiment analizi - ses tonu, vurgu, context için hazır

    Speech-to-text'ten gelen metni analiz eder:
    - Duygusal durum (NEUTRAL, HAPPY, FRUSTRATED, ANGRY, ANXIOUS, DESPERATE)
    - Intensity (1-10 skala)
    - Urgency justification (sinir ≠ acil)
    - Speech patterns (hızlı konuşma, tekrar, kesintiler)
    """

    def __init__(self):
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            raise ValueError("GEMINI_API_KEY not found")

        genai.configure(api_key=api_key)
        self.model = genai.GenerativeModel('gemini-2.5-flash')

    def analyze(self, text: str, source: str = "text") -> Dict[str, Any]:
        """
        Metinden sentiment ve context analizi

        Args:
            text: Analiz edilecek metin (customer request)
            source: "text", "phone", "speech" (ses kayıtları için ekstra analiz)

        Returns:
            {
                "sentiment": "ANGRY",
                "intensity": 8,
                "emotions": ["frustration", "urgency", "desperation"],
                "justifies_urgency": True,
                "reasoning": "...",
                "speech_indicators": {...}  # sadece phone/speech için
            }
        """
        prompt = f"""Sen bir sentiment analiz uzmanısın. Müşteri talebini analiz et.

GÖREV:
Metnin duygusal durumunu, yoğunluğunu ve ACİLLİK gerekçelendirmesini değerlendir.

ÖNEMLİ:
- SİNİRLİ OLMAK ≠ ACİL DURUM
- Müşteri çok sinirli olabilir ama talep basit/önemsiz olabilir
- Acillik SORUNUN kendisinden gelir, duygudan değil
- Örnek: "Şifre unuttum çok sinirliyim!" → ANGRY ama NOT urgent
- Örnek: "2 gündür internet yok işim duruyor" → Maybe FRUSTRATED ama URGENT

METİN:
"{text}"

KAYNAK: {source}

CEVAP FORMATI (JSON):
{{
  "sentiment": "NEUTRAL/HAPPY/FRUSTRATED/ANGRY/ANXIOUS/DESPERATE",
  "intensity": 1-10,
  "emotions": ["emotion1", "emotion2"],
  "justifies_urgency": true/false,
  "reasoning": "Neden bu sentiment? Acillik gerekçesi var mı?",
  "urgency_factors": [
    "faktör1: açıklama",
    "faktör2: açıklama"
  ],
  "non_urgency_factors": [
    "faktör1: açıklama"
  ]
}}

Sadece JSON döndür, başka açıklama yapma."""

        try:
            response = self.model.generate_content(prompt)
            response_text = response.text.strip()

            # Clean JSON
            if response_text.startswith("```"):
                response_text = response_text.split("```")[1]
                if response_text.startswith("json"):
                    response_text = response_text[4:]
                response_text = response_text.strip()

            result = json.loads(response_text)
            return result

        except Exception as e:
            print(f"Sentiment analysis error: {e}")
            # Fallback to basic sentiment
            return {
                "sentiment": "NEUTRAL",
                "intensity": 5,
                "emotions": ["unknown"],
                "justifies_urgency": False,
                "reasoning": f"Error: {str(e)}",
                "urgency_factors": [],
                "non_urgency_factors": []
            }

    def analyze_speech_patterns(self, audio_transcription: str, audio_metadata: Dict[str, Any] = None) -> Dict[str, Any]:
        """
        Ses kayıtları için gelişmiş analiz (future: speech-to-text entegrasyonu)

        Args:
            audio_transcription: Metne çevrilmiş konuşma
            audio_metadata: {
                "speaking_rate": float,  # kelime/dakika
                "pitch_variance": float,  # ses perdesi değişimi
                "interruptions": int,    # kesinti sayısı
                "repetitions": List[str] # tekrarlanan kelimeler
            }

        Returns:
            Enhanced sentiment analysis with speech indicators
        """
        base_analysis = self.analyze(audio_transcription, source="speech")

        # Ses özellikleri varsa ekstra analiz
        if audio_metadata:
            speech_indicators = {
                "fast_speech": audio_metadata.get("speaking_rate", 120) > 160,
                "voice_stress": audio_metadata.get("pitch_variance", 0) > 50,
                "multiple_interruptions": audio_metadata.get("interruptions", 0) > 2,
                "repetitive_words": len(audio_metadata.get("repetitions", [])) > 0
            }

            base_analysis["speech_indicators"] = speech_indicators

            # Ses özelliklerine göre intensity ayarla
            if speech_indicators["fast_speech"] and speech_indicators["voice_stress"]:
                base_analysis["intensity"] = min(10, base_analysis["intensity"] + 2)

        return base_analysis

    def should_escalate_priority(self, sentiment_analysis: Dict[str, Any], current_priority: str) -> str:
        """
        Sentiment'a göre priority'yi akıllıca ayarla

        Rules:
        - Sadece justifies_urgency=True ise priority artır
        - Intensity > 7 ve justifies_urgency → +1 seviye
        - ANGRY/DESPERATE ama justifies_urgency=False → priority aynı kalır

        Args:
            sentiment_analysis: analyze() output
            current_priority: Mevcut priority (RAG'den gelen)

        Returns:
            Adjusted priority
        """
        priority_levels = ["LOW", "MEDIUM", "HIGH", "URGENT"]
        current_idx = priority_levels.index(current_priority)

        # Acillik gerekçesi yoksa priority değişmez
        if not sentiment_analysis.get("justifies_urgency", False):
            return current_priority

        # Intensity > 7 ve justifies_urgency → +1 seviye
        if sentiment_analysis.get("intensity", 0) > 7:
            new_idx = min(len(priority_levels) - 1, current_idx + 1)
            return priority_levels[new_idx]

        return current_priority
