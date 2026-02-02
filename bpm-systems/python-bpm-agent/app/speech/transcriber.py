"""Speech-to-Text Transcriber - Real-time call processing"""
import os
from typing import Dict, Any, List, Optional
from pathlib import Path
import json

# Google Cloud Speech-to-Text için (future)
try:
    import google.generativeai as genai
    GEMINI_AVAILABLE = True
except ImportError:
    GEMINI_AVAILABLE = False

# Whisper API için (OpenAI - daha stable)
try:
    import openai
    OPENAI_AVAILABLE = True
except ImportError:
    OPENAI_AVAILABLE = False


class SpeechTranscriber:
    """
    Real-time speech-to-text transcription

    Supports:
    1. Google Gemini Live API (real-time streaming)
    2. OpenAI Whisper API (file-based, çok iyi accuracy)
    3. Mock mode (demo için)
    """

    def __init__(self, provider: str = "mock"):
        """
        Args:
            provider: "gemini", "whisper", or "mock"
        """
        self.provider = provider

        if provider == "gemini" and GEMINI_AVAILABLE:
            api_key = os.getenv("GEMINI_API_KEY")
            genai.configure(api_key=api_key)
            self.model = genai.GenerativeModel('gemini-2.5-pro')

        elif provider == "whisper" and OPENAI_AVAILABLE:
            openai.api_key = os.getenv("OPENAI_API_KEY")

    def transcribe_audio_file(self, audio_path: str) -> Dict[str, Any]:
        """
        Ses dosyasını transkribe et

        Args:
            audio_path: .wav, .mp3, .m4a dosya yolu

        Returns:
            {
                "text": "Transkribe edilmiş metin",
                "confidence": 0.95,
                "duration": 45.2,  # saniye
                "language": "tr",
                "words": [
                    {"word": "merhaba", "start": 0.0, "end": 0.5, "confidence": 0.98},
                    ...
                ],
                "metadata": {
                    "speaking_rate": 150,  # kelime/dakika
                    "pitch_variance": 60,  # ses perdesi değişimi
                    "interruptions": 2,
                    "repetitions": ["internet", "modem"]
                }
            }
        """
        if self.provider == "whisper" and OPENAI_AVAILABLE:
            return self._transcribe_whisper(audio_path)
        elif self.provider == "gemini" and GEMINI_AVAILABLE:
            return self._transcribe_gemini(audio_path)
        else:
            return self._transcribe_mock(audio_path)

    def transcribe_stream(self, audio_stream) -> Dict[str, Any]:
        """
        Real-time streaming transcription (telefon konuşması için)

        Args:
            audio_stream: Audio stream object (bytes iterator)

        Returns:
            Continuous transcription results
        """
        # TODO: Implement real-time streaming
        # Bu Gemini Live API veya Google Cloud Speech-to-Text Streaming API ile yapılır
        raise NotImplementedError("Streaming transcription will be implemented in Phase 2")

    def _transcribe_whisper(self, audio_path: str) -> Dict[str, Any]:
        """OpenAI Whisper API ile transkripsiyon"""
        try:
            with open(audio_path, "rb") as audio_file:
                response = openai.Audio.transcribe(
                    model="whisper-1",
                    file=audio_file,
                    language="tr",  # Turkish
                    response_format="verbose_json"  # Word-level timestamps
                )

            # Analyze speech patterns
            words = response.get("words", [])
            metadata = self._analyze_speech_patterns(words, response.get("duration", 0))

            return {
                "text": response["text"],
                "confidence": response.get("confidence", 0.95),
                "duration": response.get("duration", 0),
                "language": response.get("language", "tr"),
                "words": words,
                "metadata": metadata
            }

        except Exception as e:
            print(f"Whisper transcription error: {e}")
            return self._transcribe_mock(audio_path)

    def _transcribe_gemini(self, audio_path: str) -> Dict[str, Any]:
        """Google Gemini ile transkripsiyon"""
        try:
            # Gemini Audio API (future implementation)
            # Şu an Gemini dosya yükleyebiliyor ama speech-to-text specialized değil
            # Google Cloud Speech-to-Text daha iyi bu iş için

            # Mock implementation for now
            return self._transcribe_mock(audio_path)

        except Exception as e:
            print(f"Gemini transcription error: {e}")
            return self._transcribe_mock(audio_path)

    def _transcribe_mock(self, audio_path: str) -> Dict[str, Any]:
        """Mock transcription (demo için)"""
        # Örnek telefon konuşması senaryoları
        mock_transcriptions = {
            "angry_customer.wav": {
                "text": "Merhaba, 2 gündür internetim çalışmıyor, modem ışıkları yanmıyor. Bu çok sinir bozucu, lütfen acil çözüm bulun!",
                "confidence": 0.92,
                "duration": 8.5,
                "language": "tr",
                "words": [
                    {"word": "Merhaba", "start": 0.0, "end": 0.4, "confidence": 0.95},
                    {"word": "2", "start": 0.5, "end": 0.7, "confidence": 0.98},
                    {"word": "gündür", "start": 0.7, "end": 1.2, "confidence": 0.97},
                    {"word": "internetim", "start": 1.3, "end": 2.0, "confidence": 0.96},
                    {"word": "çalışmıyor", "start": 2.0, "end": 2.8, "confidence": 0.98},
                ],
                "metadata": {
                    "speaking_rate": 180,  # Hızlı konuşuyor (sinirli)
                    "pitch_variance": 75,  # Yüksek ses tonu değişimi (vurgulu)
                    "interruptions": 0,
                    "repetitions": ["internet", "modem"],
                    "emotion_markers": ["sinir bozucu", "acil"]
                }
            },
            "calm_inquiry.wav": {
                "text": "Merhaba, yeni tarifeleriniz hakkında bilgi alabilir miyim? Hangi paketler var?",
                "confidence": 0.98,
                "duration": 5.2,
                "language": "tr",
                "words": [],
                "metadata": {
                    "speaking_rate": 120,  # Normal hız
                    "pitch_variance": 30,  # Düşük varyans (sakin)
                    "interruptions": 0,
                    "repetitions": [],
                    "emotion_markers": []
                }
            }
        }

        filename = Path(audio_path).name
        return mock_transcriptions.get(filename, {
            "text": "Örnek telefon konuşması transkripti burada görünecek.",
            "confidence": 0.90,
            "duration": 10.0,
            "language": "tr",
            "words": [],
            "metadata": {
                "speaking_rate": 140,
                "pitch_variance": 45,
                "interruptions": 0,
                "repetitions": []
            }
        })

    def _analyze_speech_patterns(self, words: List[Dict], duration: float) -> Dict[str, Any]:
        """
        Konuşma patternlerini analiz et

        Returns:
            Speech metadata for sentiment analysis
        """
        if not words or duration == 0:
            return {
                "speaking_rate": 120,
                "pitch_variance": 40,
                "interruptions": 0,
                "repetitions": []
            }

        # Speaking rate (words per minute)
        speaking_rate = (len(words) / duration) * 60 if duration > 0 else 120

        # Find repetitions
        word_counts = {}
        for w in words:
            word = w.get("word", "").lower()
            word_counts[word] = word_counts.get(word, 0) + 1

        repetitions = [word for word, count in word_counts.items() if count > 2]

        # TODO: Pitch variance analysis (requires actual audio analysis)
        # For now, estimate based on speaking rate
        pitch_variance = 50 if speaking_rate > 160 else 35

        return {
            "speaking_rate": int(speaking_rate),
            "pitch_variance": pitch_variance,
            "interruptions": 0,  # Would need actual audio analysis
            "repetitions": repetitions
        }


class CallProcessor:
    """
    Complete call processing pipeline

    Flow:
    1. Audio input (stream or file)
    2. Speech-to-text transcription
    3. Sentiment analysis (with speech metadata)
    4. Intent detection & routing
    5. BPM actions
    """

    def __init__(self, transcriber: SpeechTranscriber):
        self.transcriber = transcriber

    def process_call(self, audio_source: str, customer_id: str) -> Dict[str, Any]:
        """
        Telefon konuşmasını işle (end-to-end)

        Args:
            audio_source: Ses dosyası yolu veya stream
            customer_id: Müşteri ID

        Returns:
            Complete intake request ready for agent
        """
        # 1. Transcribe
        transcription = self.transcriber.transcribe_audio_file(audio_source)

        # 2. Format for intake agent
        intake_request = {
            "source": "phone",
            "text": transcription["text"],
            "customer_id": customer_id,
            "sentiment": "NEUTRAL",  # Will be overridden by advanced sentiment analyzer
            "metadata": {
                "transcription_confidence": transcription["confidence"],
                "call_duration": transcription["duration"],
                "language": transcription["language"],
                "speech_metadata": transcription["metadata"]
            }
        }

        return intake_request
