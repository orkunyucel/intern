"""Demo test script - Farklı senaryoları test et"""
import requests
import json
from typing import Dict, Any


def test_intake(scenario: Dict[str, Any]):
    """Intake endpoint'ini test et"""
    print(f"\n{'='*70}")
    print(f"SENARYO: {scenario['name']}")
    print(f"{'='*70}")

    response = requests.post(
        "http://localhost:8000/api/intake",
        json=scenario['request']
    )

    if response.status_code == 200:
        result = response.json()
        print(f"\n✅ BAŞARILI")
        print(f"\nCase ID: {result['case_id']}")
        print(f"Intent: {result['intent']}")
        print(f"Kategori: {result['category']}")
        print(f"Öncelik: {result['priority']}")
        print(f"Otomatik Onay: {result['auto_approve']}")
        if result['missing_fields']:
            print(f"Eksik Alanlar: {', '.join(result['missing_fields'])}")

        print(f"\nYapılan Aksiyonlar:")
        for action in result['actions_taken']:
            print(f"  • {action}")

        print(f"\nKarar Açıklaması:")
        print(f"  {result['reasoning']}")

    else:
        print(f"\n❌ HATA: {response.status_code}")
        print(response.text)

    print(f"\n{'='*70}\n")


def main():
    """Test senaryolarını çalıştır"""

    scenarios = [
        {
            "name": "Senaryo 1: İnternet Kesintisi (2 gün)",
            "request": {
                "source": "form",
                "text": "İnternetim 2 gündür çalışmıyor, modem ışıkları yanmıyor",
                "customer_id": "CUST-12345",
                "sentiment": "ANGRY"
            }
        },
        {
            "name": "Senaryo 2: Fatura İtirazı (Büyük Tutar)",
            "request": {
                "source": "email",
                "text": "Son faturamda 500 TL fazla ücret var, lütfen düzeltin",
                "customer_id": "CUST-67890",
                "sentiment": "FRUSTRATED"
            }
        },
        {
            "name": "Senaryo 3: İzin Talebi",
            "request": {
                "source": "form",
                "text": "Önümüzdeki hafta 3 gün izin kullanmak istiyorum",
                "customer_id": "EMP-11111",
                "sentiment": "NEUTRAL"
            }
        },
        {
            "name": "Senaryo 4: Genel Bilgi Talebi",
            "request": {
                "source": "chat",
                "text": "Yeni tarifeleriniz hakkında bilgi alabilir miyim?",
                "customer_id": "CUST-99999",
                "sentiment": "HAPPY"
            }
        },
        {
            "name": "Senaryo 5: Karmaşık Talep (Teknik + Fatura)",
            "request": {
                "source": "phone",
                "text": "İnternetim yavaş ve son 3 aydır fazla para ödüyorum, modem değişikliği gerekiyor",
                "customer_id": "CUST-55555",
                "sentiment": "FRUSTRATED"
            }
        }
    ]

    print("\n🚀 BPM INTELLIGENT INTAKE AGENT - DEMO TEST\n")

    # Health check
    try:
        response = requests.get("http://localhost:8000/api/health")
        if response.status_code == 200:
            health = response.json()
            print("✅ API çalışıyor")
            print(f"   Qdrant: {health['services']['qdrant']}")
            print(f"   Doküman sayısı: {health['services']['documents']}")
        else:
            print("❌ API çalışmıyor!")
            return
    except Exception as e:
        print(f"❌ API'ye bağlanılamadı: {e}")
        print("\nLütfen önce API'yi başlatın:")
        print("  source venv/bin/activate")
        print("  uvicorn app.main:app --reload --port 8000")
        return

    # Test senaryoları
    for scenario in scenarios:
        test_intake(scenario)
        input("\n[Enter] ile devam et...")


if __name__ == "__main__":
    main()
