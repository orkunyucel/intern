"""Mock Call Scenarios - Real phone conversation simulations"""

CALL_SCENARIOS = [
    {
        "id": 1,
        "name": "📞 Kızgın Müşteri - İnternet Kesintisi",
        "description": "Yüksek ses tonu, hızlı konuşma, tekrar eden kelimeler",
        "audio_file": "angry_internet_outage.mp3",
        "transcription": {
            "text": "Merhaba, 2 gündür internetim çalışmıyor, modem ışıkları yanmıyor. Bu çok sinir bozucu! İşim duruyor, acilen çözüm lazım. Lütfen birisi gelip baksın, modem arızalı galiba.",
            "confidence": 0.92,
            "duration": 12.5,
            "language": "tr",
            "metadata": {
                "speaking_rate": 180,  # Hızlı (sinirli)
                "pitch_variance": 75,  # Yüksek (vurgulu)
                "interruptions": 1,
                "repetitions": ["internet", "modem", "acil"],
                "emotion_markers": ["sinir bozucu", "acilen", "duruyor"]
            }
        },
        "customer_id": "CUST-PHONE-001",
        "expected": {
            "sentiment": "ANGRY",
            "intensity": 8,
            "justifies_urgency": True,
            "category": "TECH_SUPPORT",
            "priority": "URGENT"
        }
    },
    {
        "id": 2,
        "name": "📞 Sakin Müşteri - Bilgi Talebi",
        "description": "Normal tempo, düşük ses tonu varyansı, soruları net",
        "audio_file": "calm_inquiry.mp3",
        "transcription": {
            "text": "Merhaba, yeni tarifeleriniz hakkında bilgi alabilir miyim? 100 Mbps paketinizin fiyatı nedir? Ayrıca modem ücretsiz mi?",
            "confidence": 0.98,
            "duration": 8.2,
            "language": "tr",
            "metadata": {
                "speaking_rate": 120,  # Normal
                "pitch_variance": 30,  # Düşük (sakin)
                "interruptions": 0,
                "repetitions": [],
                "emotion_markers": []
            }
        },
        "customer_id": "CUST-PHONE-002",
        "expected": {
            "sentiment": "NEUTRAL",
            "intensity": 3,
            "justifies_urgency": False,
            "category": "GENERAL",
            "priority": "LOW"
        }
    },
    {
        "id": 3,
        "name": "📞 Endişeli Müşteri - Fatura Sorunu",
        "description": "Orta hız, artan ses tonu, finansal endişe",
        "audio_file": "worried_billing.mp3",
        "transcription": {
            "text": "Merhaba, son faturamda 500 TL fazla ücret var. Bu çok yüksek bir miktar. Normalde 150 TL ödüyorum ama bu ay 650 TL gelmiş. Kontrol edebilir misiniz? Çok endişelendim, hata olabilir mi?",
            "confidence": 0.94,
            "duration": 15.3,
            "language": "tr",
            "metadata": {
                "speaking_rate": 150,  # Orta-hızlı
                "pitch_variance": 55,  # Orta
                "interruptions": 0,
                "repetitions": ["fatura", "TL", "çok"],
                "emotion_markers": ["endişelendim", "hata olabilir", "yüksek"]
            }
        },
        "customer_id": "CUST-PHONE-003",
        "expected": {
            "sentiment": "ANXIOUS",
            "intensity": 6,
            "justifies_urgency": True,  # 500 TL büyük tutar
            "category": "BILLING",
            "priority": "HIGH"
        }
    },
    {
        "id": 4,
        "name": "📞 Çaresiz Müşteri - Güvenlik",
        "description": "Hızlı konuşma, panik belirtileri, acil yardım isteği",
        "audio_file": "desperate_security.mp3",
        "transcription": {
            "text": "Lütfen yardım edin! Hesabıma yetkisiz giriş yapılmış, şifremi değiştiremedim. Kredi kartı bilgilerim kayıtlı, çok korkuyorum. Hemen hesabımı kapatın veya dondurabilir misiniz? Bu çok acil!",
            "confidence": 0.89,
            "duration": 10.8,
            "language": "tr",
            "metadata": {
                "speaking_rate": 195,  # Çok hızlı (panik)
                "pitch_variance": 85,  # Çok yüksek
                "interruptions": 2,
                "repetitions": ["acil", "hemen", "yardım"],
                "emotion_markers": ["korkuyorum", "lütfen", "yardım edin", "çok acil"]
            }
        },
        "customer_id": "CUST-PHONE-004",
        "expected": {
            "sentiment": "DESPERATE",
            "intensity": 10,
            "justifies_urgency": True,
            "category": "TECH_SUPPORT",
            "priority": "URGENT"
        }
    },
    {
        "id": 5,
        "name": "📞 Sinirli ama Basit Talep",
        "description": "Yüksek ses tonu ama sorun basit - sentiment ≠ urgency",
        "audio_file": "angry_simple_request.mp3",
        "transcription": {
            "text": "Çok sinirliyim! Wi-Fi şifremi unuttum ve bulamıyorum. Her yerde aradım ama yok. Lütfen şifremi söyleyin veya sıfırlayın. Bu kadar basit bir şey için neden bu kadar uğraşıyorum!",
            "confidence": 0.93,
            "duration": 11.2,
            "language": "tr",
            "metadata": {
                "speaking_rate": 170,  # Hızlı
                "pitch_variance": 70,  # Yüksek
                "interruptions": 1,
                "repetitions": ["şifre", "lütfen"],
                "emotion_markers": ["sinirliyim", "neden bu kadar"]
            }
        },
        "customer_id": "CUST-PHONE-005",
        "expected": {
            "sentiment": "ANGRY",
            "intensity": 7,
            "justifies_urgency": False,  # ÖNEMLİ: Basit talep, acil değil!
            "category": "TECH_SUPPORT",
            "priority": "LOW"  # Sentiment'e rağmen LOW kalmalı
        }
    },
    {
        "id": 6,
        "name": "📞 Uzun Hız Şikayeti",
        "description": "Detaylı açıklama, orta tempo, sürekli sorun",
        "audio_file": "speed_complaint.mp3",
        "transcription": {
            "text": "Merhaba, 1 haftadır internet hızım çok düşük. 100 Mbps paketim var ama hız testinde 10 Mbps görünüyor. Modem resetleme denedim, kablo bağlantılarını kontrol ettim, ama düzelme yok. Video konferanslarım kesiliyor, çalışamaz hale geldim.",
            "confidence": 0.96,
            "duration": 18.5,
            "language": "tr",
            "metadata": {
                "speaking_rate": 135,  # Normal
                "pitch_variance": 45,  # Orta
                "interruptions": 0,
                "repetitions": ["hız", "modem", "100"],
                "emotion_markers": ["çalışamaz", "kesiliyor"]
            }
        },
        "customer_id": "CUST-PHONE-006",
        "expected": {
            "sentiment": "FRUSTRATED",
            "intensity": 6,
            "justifies_urgency": True,  # 1 hafta + iş etkileniyor
            "category": "TECH_SUPPORT",
            "priority": "HIGH"
        }
    }
]
