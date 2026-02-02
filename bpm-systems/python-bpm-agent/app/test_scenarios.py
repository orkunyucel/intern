"""Test senaryoları koleksiyonu"""

TEST_SCENARIOS = [
    {
        "id": 1,
        "name": "🔴 İnternet Kesintisi (2 gün - Acil)",
        "description": "Uzun süreli internet kesintisi, kızgın müşteri",
        "request": {
            "source": "form",
            "text": "İnternetim 2 gündür çalışmıyor, modem ışıkları yanmıyor",
            "customer_id": "CUST-12345",
            "sentiment": "ANGRY"
        },
        "expected": {
            "category": "TECH_SUPPORT",
            "priority": "URGENT"
        }
    },
    {
        "id": 2,
        "name": "💰 Fatura İtirazı (500 TL)",
        "description": "Yüksek tutarlı fatura hatası",
        "request": {
            "source": "email",
            "text": "Son faturamda 500 TL fazla ücret var, lütfen düzeltin",
            "customer_id": "CUST-67890",
            "sentiment": "FRUSTRATED"
        },
        "expected": {
            "category": "BILLING",
            "priority": "HIGH"
        }
    },
    {
        "id": 3,
        "name": "📅 İzin Talebi (3 gün)",
        "description": "Standart personel izin talebi",
        "request": {
            "source": "form",
            "text": "Önümüzdeki hafta 3 gün izin kullanmak istiyorum",
            "customer_id": "EMP-11111",
            "sentiment": "NEUTRAL"
        },
        "expected": {
            "category": "HR",
            "priority": "MEDIUM"
        }
    },
    {
        "id": 4,
        "name": "❓ Genel Bilgi Talebi",
        "description": "Tarife bilgilendirme, mutlu müşteri",
        "request": {
            "source": "chat",
            "text": "Yeni tarifeleriniz hakkında bilgi alabilir miyim?",
            "customer_id": "CUST-99999",
            "sentiment": "HAPPY"
        },
        "expected": {
            "category": "GENERAL",
            "priority": "LOW"
        }
    },
    {
        "id": 5,
        "name": "⚡ Karmaşık Talep (Teknik + Fatura)",
        "description": "Birden fazla konu içeren talep",
        "request": {
            "source": "phone",
            "text": "İnternetim yavaş ve son 3 aydır fazla para ödüyorum, modem değişikliği gerekiyor",
            "customer_id": "CUST-55555",
            "sentiment": "FRUSTRATED"
        },
        "expected": {
            "category": "TECH_SUPPORT",
            "priority": "HIGH"
        }
    },
    {
        "id": 6,
        "name": "🔧 Basit Teknik Sorun",
        "description": "Wi-Fi bağlantı problemi",
        "request": {
            "source": "chat",
            "text": "Wi-Fi şifremi unuttum, nasıl öğrenebilirim?",
            "customer_id": "CUST-22222",
            "sentiment": "NEUTRAL"
        },
        "expected": {
            "category": "TECH_SUPPORT",
            "priority": "LOW"
        }
    },
    {
        "id": 7,
        "name": "💸 Küçük Fatura İtirazı (30 TL)",
        "description": "Düşük tutarlı ücret iadesi",
        "request": {
            "source": "form",
            "text": "Geçen ay iptal ettiğim ekstra paket için 30 TL ücret almışsınız",
            "customer_id": "CUST-33333",
            "sentiment": "NEUTRAL"
        },
        "expected": {
            "category": "BILLING",
            "priority": "MEDIUM"
        }
    },
    {
        "id": 8,
        "name": "👥 Performans Değerlendirme",
        "description": "HR - personel performans talebi",
        "request": {
            "source": "email",
            "text": "Bu yılki performans değerlendirmem ne zaman yapılacak?",
            "customer_id": "EMP-44444",
            "sentiment": "NEUTRAL"
        },
        "expected": {
            "category": "HR",
            "priority": "MEDIUM"
        }
    },
    {
        "id": 9,
        "name": "🚨 Acil Güvenlik Sorunu",
        "description": "Hesap güvenliği - acil müdahale",
        "request": {
            "source": "phone",
            "text": "Hesabıma yetkisiz giriş yapıldı, şifremi değiştirmem gerekiyor acil!",
            "customer_id": "CUST-77777",
            "sentiment": "ANGRY"
        },
        "expected": {
            "category": "TECH_SUPPORT",
            "priority": "URGENT"
        }
    },
    {
        "id": 10,
        "name": "📱 Hız Düşüklüğü Şikayeti",
        "description": "İnternet hız problemi - 1 haftalık",
        "request": {
            "source": "email",
            "text": "1 haftadır internet hızım çok düşük, 100 Mbps paketim var ama 10 Mbps alıyorum",
            "customer_id": "CUST-88888",
            "sentiment": "FRUSTRATED"
        },
        "expected": {
            "category": "TECH_SUPPORT",
            "priority": "HIGH"
        }
    }
]
