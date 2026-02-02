"""Generate audio files using Google Text-to-Speech"""
import os
from pathlib import Path
from app.call_scenarios import CALL_SCENARIOS

# Google TTS için (eğer kuruluysa)
try:
    from google.cloud import texttospeech
    GOOGLE_TTS_AVAILABLE = True
except ImportError:
    GOOGLE_TTS_AVAILABLE = False

# gTTS alternatifi (daha basit, API key gerektirmez)
try:
    from gtts import gTTS
    GTTS_AVAILABLE = True
except ImportError:
    GTTS_AVAILABLE = False


def generate_with_gtts(text: str, output_path: str, lang='tr', slow=False):
    """gTTS ile audio oluştur (ücretsiz, API key gerektirmez)"""
    try:
        tts = gTTS(text=text, lang=lang, slow=slow)
        tts.save(output_path)
        print(f"✓ Created: {output_path}")
        return True
    except Exception as e:
        print(f"✗ Error: {e}")
        return False


def generate_with_google_tts(text: str, output_path: str, speaking_rate=1.0, pitch=0.0):
    """Google Cloud TTS ile audio oluştur (daha iyi kalite)"""
    try:
        client = texttospeech.TextToSpeechClient()

        synthesis_input = texttospeech.SynthesisInput(text=text)

        # Türkçe ses (female voice)
        voice = texttospeech.VoiceSelectionParams(
            language_code="tr-TR",
            name="tr-TR-Wavenet-D",  # Female voice
            ssml_gender=texttospeech.SsmlVoiceGender.FEMALE
        )

        # Audio config
        audio_config = texttospeech.AudioConfig(
            audio_encoding=texttospeech.AudioEncoding.MP3,
            speaking_rate=speaking_rate,
            pitch=pitch
        )

        response = client.synthesize_speech(
            input=synthesis_input,
            voice=voice,
            audio_config=audio_config
        )

        with open(output_path, "wb") as out:
            out.write(response.audio_content)

        print(f"✓ Created: {output_path}")
        return True

    except Exception as e:
        print(f"✗ Error with Google TTS: {e}")
        return False


def generate_all_audio_files():
    """Tüm call scenarios için audio dosyaları oluştur"""
    output_dir = Path("static/audio")
    output_dir.mkdir(parents=True, exist_ok=True)

    print("🎙️ Generating audio files for call scenarios...\n")

    if not GTTS_AVAILABLE and not GOOGLE_TTS_AVAILABLE:
        print("❌ Error: Neither gTTS nor Google Cloud TTS is available")
        print("Install: pip install gtts")
        return

    provider = "gtts" if GTTS_AVAILABLE else "google"
    print(f"Using: {provider.upper()}\n")

    for scenario in CALL_SCENARIOS:
        text = scenario["transcription"]["text"]
        audio_file = scenario["audio_file"]
        output_path = output_dir / audio_file.replace('.wav', '.mp3')

        print(f"\n[Scenario {scenario['id']}] {scenario['name']}")
        print(f"Text: {text[:60]}...")

        # Speaking rate'e göre hız ayarla
        speaking_rate = scenario["transcription"]["metadata"]["speaking_rate"]

        # gTTS için slow parametresi
        slow = speaking_rate < 130 if speaking_rate else False

        if provider == "gtts":
            # gTTS ile oluştur (basit ama yeterli)
            success = generate_with_gtts(text, str(output_path), slow=slow)
        else:
            # Google TTS ile (daha iyi kalite)
            # Speaking rate normalize et (0.25 - 4.0 arası)
            rate = max(0.25, min(4.0, speaking_rate / 120))

            # Pitch variance'a göre pitch ayarla
            pitch_variance = scenario["transcription"]["metadata"].get("pitch_variance", 40)
            pitch = (pitch_variance - 40) / 10  # -4.0 to 4.0 range

            success = generate_with_google_tts(text, str(output_path), speaking_rate=rate, pitch=pitch)

        if success:
            # Update scenario audio file path
            scenario["audio_file"] = audio_file.replace('.wav', '.mp3')

    print("\n✅ Audio generation complete!")
    print(f"Files saved to: {output_dir}")


if __name__ == "__main__":
    # Check requirements
    if not GTTS_AVAILABLE:
        print("Installing gTTS...")
        os.system("pip install gtts")

    generate_all_audio_files()
