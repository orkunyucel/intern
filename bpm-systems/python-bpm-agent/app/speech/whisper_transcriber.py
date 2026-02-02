"""Real Whisper Speech-to-Text Implementation"""
import os
import whisper
from pathlib import Path
from typing import Dict, Any


class WhisperTranscriber:
    """OpenAI Whisper for real speech-to-text"""

    def __init__(self, model_size: str = "base"):
        """
        Args:
            model_size: tiny, base, small, medium, large
                       (base is good balance: 74MB, fast, decent accuracy)
        """
        print(f"Loading Whisper model: {model_size}...")
        self.model = whisper.load_model(model_size)
        print(f"✓ Whisper {model_size} model loaded")

    def transcribe_audio_file(self, audio_path: str) -> Dict[str, Any]:
        """Alias for transcribe() to verify CallProcessor compatibility"""
        return self.transcribe(audio_path)

    def transcribe(self, audio_path: str, language: str = "tr") -> Dict[str, Any]:
        """
        Real speech-to-text transcription

        Args:
            audio_path: Path to audio file (.mp3, .wav, .m4a, etc.)
            language: Language code (tr for Turkish)

        Returns:
            {
                "text": "Transcribed text",
                "segments": [word-level timestamps],
                "language": "tr",
                "duration": 12.5
            }
        """
        try:
            print(f"\n[WHISPER] Transcribing: {audio_path}")

            # Transcribe with word-level timestamps
            result = self.model.transcribe(
                audio_path,
                language=language,
                word_timestamps=True,
                verbose=False
            )

            print(f"[WHISPER] ✓ Complete: {len(result['text'])} chars")

            # Extract metadata
            segments = result.get("segments", [])
            words = []

            for segment in segments:
                if "words" in segment:
                    for word_data in segment["words"]:
                        words.append({
                            "word": word_data.get("word", "").strip(),
                            "start": word_data.get("start", 0),
                            "end": word_data.get("end", 0),
                            "confidence": word_data.get("probability", 0.9)
                        })

            # Calculate speech metadata
            duration = segments[-1]["end"] if segments else 0
            total_words = len(words)
            speaking_rate = int((total_words / duration) * 60) if duration > 0 else 120

            # Detect repetitions
            word_counts = {}
            for w in words:
                word_lower = w["word"].lower()
                if len(word_lower) > 3:  # Only count words > 3 chars
                    word_counts[word_lower] = word_counts.get(word_lower, 0) + 1

            repetitions = [word for word, count in word_counts.items() if count >= 2]

            return {
                "text": result["text"].strip(),
                "segments": segments,
                "words": words,
                "language": result.get("language", language),
                "duration": duration,
                "confidence": result.get("confidence", 0.9),
                "metadata": {
                    "speaking_rate": speaking_rate,
                    "pitch_variance": 50,  # Would need actual audio analysis
                    "interruptions": 0,
                    "repetitions": repetitions[:5]  # Top 5
                }
            }

        except Exception as e:
            print(f"[WHISPER] ✗ Error: {e}")
            raise


# Global instance (lazy loaded)
_whisper_instance = None


def get_whisper_transcriber() -> WhisperTranscriber:
    """Get or create Whisper transcriber (singleton)"""
    global _whisper_instance
    if _whisper_instance is None:
        _whisper_instance = WhisperTranscriber(model_size="base")
    return _whisper_instance
