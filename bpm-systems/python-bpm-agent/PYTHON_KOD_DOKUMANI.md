# 🐍 Python Kod Yapısı Dokümantasyonu

> **BPM Intelligent Intake Agent** — Tüm Python dosyalarının kapsamlı açıklaması

---

## 📋 Genel Bakış

| Metrik | Değer |
|--------|-------|
| **Toplam Python dosyası** | 48 |
| **Toplam kod satırı** | ~4,500+ |
| **Framework** | FastAPI + LangGraph |
| **LLM** | Google Gemini 2.5 Flash |
| **Vector DB** | Qdrant |
| **Embedding** | Gemini text-embedding-004 |
| **STT** | OpenAI Whisper |

---

## 🏗️ Mimari Şema

```
python-bpm-agent/
├── app/
│   ├── main.py              ← FastAPI ana uygulama
│   ├── models.py            ← Pydantic veri modelleri
│   ├── config.py            ← Merkezi konfigürasyon
│   ├── microservices.py     ← Flowable entegrasyon endpoint'leri
│   ├── test_scenarios.py    ← Text test senaryoları
│   ├── call_scenarios.py    ← Phone call senaryoları
│   │
│   ├── llm/                 ← 🤖 LLM Katmanı
│   │   ├── agent.py         ← IntakeAgent (ana karar verici)
│   │   └── sentiment_analyzer.py ← Duygu analizi
│   │
│   ├── rag/                 ← 📚 RAG Katmanı
│   │   ├── embedder.py      ← Gemini embedding oluşturucu
│   │   └── retriever.py     ← Qdrant arama + reranking
│   │
│   ├── speech/              ← 🎤 Ses İşleme
│   │   ├── transcriber.py   ← Multi-provider STT
│   │   └── whisper_transcriber.py ← Whisper implementasyonu
│   │
│   ├── tools/               ← ⚙️ MCP Araçları
│   │   └── mcp_tools.py     ← 10 BPM aksiyon aracı
│   │
│   └── langraph/            ← 🔄 LangGraph Multi-Agent
│       ├── graphs/          ← Graph tanımları
│       ├── nodes/           ← Node implementasyonları
│       ├── state/           ← State tanımları
│       ├── tools/           ← Agent tool'ları
│       └── prompts/         ← System prompt'ları
│
├── flowable/                ← Flowable BPMN & deploy
├── load_policies.py         ← Qdrant'a politika yükleme
├── test_demo.py             ← Demo test script
└── templates/index.html     ← Web UI
```

---

## 1️⃣ Ana Uygulama Katmanı

### 📄 `app/main.py` — FastAPI Ana Uygulama (538 satır)

Tüm sistemin giriş noktası. HTTP isteklerini alır, AI pipeline'ını çalıştırır, sonuçları döner.

| Endpoint | Method | Açıklama |
|----------|--------|----------|
| `/` | GET | Web UI (index.html) |
| `/api/intake` | POST | **Ana endpoint** — Metin → AI Analiz → MCP Tool'lar → Sonuç |
| `/api/flowable/start-process` | POST | AI analiz + Flowable'da süreç başlat |
| `/api/stats` | GET | Sistem istatistikleri (Qdrant, Flowable durumu) |
| `/api/health` | GET | Detaylı health check |
| `/api/scenarios` | GET | Test senaryolarını listele |
| `/api/call/scenarios` | GET | Telefon senaryolarını listele |
| `/api/call/process` | POST | Telefon çağrısı işle (STT + Analiz) |
| `/api/upload-audio` | POST | Gerçek ses dosyası yükle + işle |

**Ana akış (`/api/intake`):**

```
İstek gelir → Agent.analyze() → Sentiment + RAG + LLM → Karar → MCP Tool'lar çalışır → Response
```

**Flowable akışı (`/api/flowable/start-process`):**

```
İstek gelir → Agent.analyze() → Karar → HTTP POST → Flowable'da süreç başlar
```

**Global nesneler:**

- `agent = IntakeAgent()` — Ana AI agent
- `tool_executor = MCPToolExecutor()` — BPM aksiyon araçları
- `speech_transcriber = SpeechTranscriber()` — Ses-metin dönüşümü

---

### 📄 `app/models.py` — Veri Modelleri (79 satır)

Pydantic ile tanımlanan tüm veri yapıları:

| Model | Alanlar | Kullanım |
|-------|---------|----------|
| `IntakeRequest` | `source`, `text`, `customer_id`, `timestamp`, `sentiment` | Gelen müşteri talebi |
| `AgentDecision` | `intent`, `category`, `priority`, `missing_fields`, `auto_approve`, `tool_calls`, `reasoning` | AI'ın verdiği karar |
| `ToolCall` | `tool`, `params` | LLM'in çağırdığı araç |
| `IntakeResponse` | `case_id`, `intent`, `category`, `priority`, `actions_taken`, `reasoning` | API yanıtı |
| `PolicyDocument` | `id`, `text`, `metadata` | Qdrant'taki doküman |

**Kategori değerleri:** `TECH_SUPPORT`, `BILLING`, `HR`, `GENERAL`
**Öncelik değerleri:** `LOW`, `MEDIUM`, `HIGH`, `URGENT`

---

### 📄 `app/config.py` — Merkezi Konfigürasyon (110 satır)

Tüm ayarları tek yerden yönetir. Environment variable desteği var.

| Config Sınıfı | Parametre Örnekleri |
|---------------|---------------------|
| `GeminiConfig` | `api_key`, `model_name=gemini-2.5-flash`, `embedding_model=text-embedding-004` |
| `QdrantConfig` | `host=localhost`, `port=6333`, `collection_name=bpm_policies`, `vector_size=768` |
| `RAGConfig` | `chunk_size=500`, `chunk_overlap=100`, `score_threshold=0.3`, `enable_reranking=True` |
| `BPMConfig` | `api_url`, `enable_auto_approve=True`, `enable_task_creation=True` |
| `SpeechConfig` | `whisper_model=base`, `default_language=tr` |
| `ServerConfig` | `host=0.0.0.0`, `port=8000`, `debug`, `log_level` |

**Kullanım:** `config = get_config()` → Singleton pattern, tüm modüller aynı config'i paylaşır.

---

## 2️⃣ LLM Katmanı (`app/llm/`)

### 📄 `app/llm/agent.py` — IntakeAgent (191 satır)

**Sistemin beyni.** Gelen talebi analiz edip karar veren ana AI agent.

**Akış:**

```
1. SentimentAnalyzer.analyze()     → Duygu durumu analizi
2. QdrantRetriever.build_rag_context() → Şirket politikalarından context
3. _build_prompt()                 → Sentiment + RAG + Talep → Prompt
4. Gemini LLM çağrısı             → JSON karar
5. should_escalate_priority()      → Sentiment'a göre öncelik ayarı
6. AgentDecision döner             → Kategori, Öncelik, Tool'lar
```

**Prompt yapısı:**

- Görev tanımı (kategori, öncelik belirle)
- Şirket kuralları (RAG context)
- Sentiment analiz sonucu
- 10 MCP tool açıklaması
- JSON çıktı formatı

**Kritik özellik — "Sinirli ≠ Acil" kuralı:**

```python
# Sentiment analizi "justifies_urgency=HAYIR" diyorsa → priority yükseltme
# Sentiment analizi "justifies_urgency=EVET" ve intensity>7 → +1 seviye
```

---

### 📄 `app/llm/sentiment_analyzer.py` — Duygu Analizi (174 satır)

Müşterinin duygusal durumunu analiz eder. **Gemini LLM ile çalışır.**

**Çıktı:**

```json
{
  "sentiment": "ANGRY",
  "intensity": 8,
  "emotions": ["frustration", "urgency"],
  "justifies_urgency": true,
  "reasoning": "2 gündür internet yok, iş etkileniyor",
  "urgency_factors": ["Uzun süreli kesinti", "İş kaybı"],
  "non_urgency_factors": []
}
```

**Özel metotlar:**

| Metot | İşlev |
|-------|-------|
| `analyze(text, source)` | Metin sentiment analizi |
| `analyze_speech_patterns(text, metadata)` | Ses kaydı: konuşma hızı, vurgu, tekrar analizi |
| `should_escalate_priority(sentiment, priority)` | Sentiment'a göre öncelik yükseltme kararı |

---

## 3️⃣ RAG Katmanı (`app/rag/`)

### 📄 `app/rag/embedder.py` — Embedding Oluşturucu (53 satır)

Metinleri Gemini API ile 768 boyutlu vektörlere çevirir.

| Metot | İşlev |
|-------|-------|
| `embed_text(text)` | Doküman embedding (task: `retrieval_document`) |
| `embed_query(query)` | Sorgu embedding (task: `retrieval_query`) |
| `embed_batch(texts)` | Toplu embedding |

**Model:** `models/text-embedding-004` — 768 boyutlu vektör

---

### 📄 `app/rag/retriever.py` — Qdrant Arama Motoru (322 satır)

**En karmaşık dosya.** 4 ana bileşen içerir:

#### `SemanticChunker` — Akıllı Metin Bölme

```
Metin → Paragraflara böl → Tip belirle (Kural/Prosedür/Tanım) → Chunk oluştur
```

Chunk tipleri: `rule`, `procedure`, `definition`, `example`, `header`, `general`

#### `QueryExpander` — Sorgu Genişletme

```
"internet" → ["internet", "bağlantı", "wifi", "hız"]
"fatura"   → ["fatura", "ödeme", "billing", "hesap"]
```

Türkçe eş anlamlı kelimeler ile arama kalitesini artırır.

#### `SimpleReranker` — Sonuç Yeniden Sıralama

```
Vector score × Keyword bonus × Chunk type weight = Final score
```

- `rule` → 1.3x (kurallar daha önemli)
- `procedure` → 1.2x
- `header` → 0.7x (başlıklar daha az önemli)

#### `QdrantRetriever` — Ana Retriever

| Metot | İşlev |
|-------|-------|
| `add_documents(docs)` | Dokümanları semantic chunking ile Qdrant'a ekle |
| `search(query, limit)` | Embedding → Qdrant arama → Reranking → Sonuçlar |
| `build_rag_context(query)` | Arama sonuçlarını LLM için formatlı context'e çevir |

---

## 4️⃣ Ses İşleme (`app/speech/`)

### 📄 `app/speech/transcriber.py` — STT Yöneticisi (272 satır)

Multi-provider Speech-to-Text sistemi.

| Provider | Durum | Kullanım |
|----------|-------|----------|
| `whisper` | ✅ Gerçek | OpenAI Whisper ile ses dosyası transkripsiyon |
| `gemini` | 🔮 Gelecek | Google Gemini Live API (streaming) |
| `mock` | ✅ Demo | Senaryo bazlı simülasyon |

**`CallProcessor` sınıfı — Uçtan uca çağrı işleme:**

```
Ses dosyası → STT transkripsiyon → Konuşma pattern analizi → IntakeRequest oluştur
```

### 📄 `app/speech/whisper_transcriber.py` — Whisper (112 satır)

OpenAI Whisper ile **gerçek** ses-metin dönüşümü.

```python
# Desteklenen formatlar: .mp3, .wav, .m4a, .ogg
# Model boyutları: tiny (39MB), base (74MB), small (244MB), medium (769MB), large (1.5GB)
# Varsayılan: base — hız/doğruluk dengesi
```

**Çıktı:** Transkripsiyon + kelime bazlı zaman damgaları + konuşma hızı + tekrar analizi

---

## 5️⃣ MCP Araçları (`app/tools/`)

### 📄 `app/tools/mcp_tools.py` — BPM Aksiyon Araçları (625 satır)

LLM'in çağırabileceği **10 BPM aracı**. Her araç Pydantic validation + error handling + audit logging içerir.

| Araç | İşlev | Parametreler |
|------|-------|--------------|
| `updateCategory` | Kategori güncelle | `category`, `reason` |
| `setPriority` | Öncelik belirle | `priority`, `reason`, `escalation_note` |
| `createTask` | Görev oluştur | `team`, `description`, `due_date`, `assignee`, `tags` |
| `askMissingInfo` | Eksik bilgi iste | `fields`, `message`, `deadline_hours` |
| `autoApprove` | Otomatik onayla | `reason`, `conditions_met` |
| `sendNotification` | Bildirim gönder | `to`, `channel`, `message`, `priority` |
| `escalate` | Yöneticiye ilet | `level`, `reason`, `notify_stakeholders` |
| `addComment` | Dahili yorum ekle | `comment`, `visibility` |
| `scheduleFollowUp` | Takip planla | `action`, `schedule_hours`, `assignee` |
| `storeToVectorDb` | Vektör DB'ye kaydet | `text`, `metadata`, `collection` |

**Mimari özellikler:**

- `ToolMetadata` — Her araç için versiyon, açıklama, parametre şeması
- `@with_retry` decorator — Otomatik yeniden deneme (max 3)
- `BPMConfig` — Farklı BPM sistemleri için yapılandırma

---

## 6️⃣ LangGraph Multi-Agent (`app/langraph/`)

### 📄 Node'lar (`app/langraph/nodes/`)

| Node | Dosya | İşlev |
|------|-------|-------|
| `OrchestratorNode` | `orchestrator_node.py` | Gelen soruyu analiz edip doğru agent'a yönlendirir |
| `RAGNode` | `rag_node.py` | Qdrant'tan doküman çekip LLM ile yanıt üretir |
| `LLMNode` | `llm_node.py` | Genel LLM çağrıları (QA, analiz, tool-calling) |
| `EmbeddingNode` | `embedding_node.py` | Embedding oluşturma |
| `RetrievalNode` | `retrieval_node.py` | Doküman arama |
| `IndexingNode` | `indexing_node.py` | PDF/doküman indexleme |
| `PDFProcessorNode` | `pdf_processor_node.py` | PDF okuma ve chunk'lama |
| `ErrorHandlerNode` | `error_handler_node.py` | Hata yönetimi |

### 📄 Graph'lar (`app/langraph/graphs/`)

#### `rag_graph.py` — RAG Workflow (173 satır)

```
START → validate_input → rag_process → END
         ↓ (invalid)
       error_handler → END
```

Tek bir soruyu alır → Qdrant'tan doküman arar → LLM ile yanıtlar.

#### `multi_agent_graph.py` — Multi-Agent Orchestration (281 satır)

```
START → orchestrator → route_to_agent → [agent] → END
                          │
                          ├─→ RAG_AGENT (doküman soruları)
                          ├─→ SIMPLE_QA_AGENT (genel sorular)
                          ├─→ ANALYSIS_AGENT (analiz görevleri)
                          └─→ TOOL_AGENT (hesaplama, filtreleme)
```

### 📄 State Tanımları (`app/langraph/state/`)

| State | Alanlar |
|-------|---------|
| `RAGState` | `query`, `retrieved_docs`, `relevance_scores`, `context_text`, `response`, `citations` |
| `AgentState` | `task`, `assigned_agent`, `routing_decision`, `agent_outputs`, `trace` |

---

## 7️⃣ Yardımcı Dosyalar

### 📄 `app/microservices.py` — Flowable Mikroservis Endpoint'leri (592 satır)

Her AI adımı ayrı bir endpoint olarak sunulur. Flowable BPMN'den HTTP ile çağrılabilir.

| Endpoint | İşlev |
|----------|-------|
| `/api/microservices/embedding` | Metin → 768-dim vektör |
| `/api/microservices/qdrant-search` | Vektör → Qdrant arama |
| `/api/microservices/sentiment` | Metin → Duygu analizi |
| `/api/microservices/llm-call` | Context + Metin → LLM karar |
| `/api/microservices/mcp/update-category` | Kategori güncelle |
| `/api/microservices/mcp/set-priority` | Öncelik belirle |
| `/api/microservices/mcp/create-task` | Görev oluştur |
| `/api/microservices/tts` | Metin → Ses (placeholder) |
| `/api/microservices/store-vector` | Vektör DB'ye kaydet |

### 📄 `app/test_scenarios.py` — Text Test Senaryoları (155 satır)

10 önceden tanımlı senaryo: İnternet kesintisi, fatura itirazı, izin talebi, güvenlik sorunu, vb.

### 📄 `app/call_scenarios.py` — Phone Call Senaryoları (167 satır)

6 telefon senaryosu: Kızgın müşteri, sakin bilgi talebi, endişeli fatura, çaresiz güvenlik, vb.
Her senaryo konuşma hızı, ses perdesi, tekrar eden kelimeler gibi metadata içerir.

### 📄 `load_policies.py` — Politika Yükleyici (86 satır)

`data/policies/*.txt` dosyalarını okur → chunk'lara böler → Qdrant'a yükler.

### 📄 `flowable/deploy_to_flowable.py` — Flowable Deploy Script

BPMN dosyalarını Flowable'a otomatik deploy eder + kullanıcı grupları oluşturur.

---

## 🔄 Uçtan Uca Akış

```
┌──────────────┐
│ Müşteri      │ "3 gündür internetim yok!"
│ Talebi       │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ FastAPI      │ main.py → /api/intake
│ (main.py)    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Sentiment    │ sentiment_analyzer.py
│ Analizi      │ → ANGRY, intensity: 8, justifies_urgency: True
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ RAG          │ retriever.py
│ Pipeline     │ → QueryExpander → Embedding → Qdrant → Reranker
│              │ → "İnternet kesintisi kuralı: 2+ gün = URGENT"
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Gemini LLM   │ agent.py → _build_prompt()
│ Karar        │ → category: TECH_SUPPORT
│              │ → priority: URGENT
│              │ → tool_calls: [updateCategory, setPriority, createTask]
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ MCP Tools    │ mcp_tools.py
│ Execution    │ → updateCategory(TECH_SUPPORT)
│              │ → setPriority(URGENT)
│              │ → createTask(TechTeam, "İnternet kesintisi")
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Flowable     │ HTTP POST → demoIntakeProcess
│ BPM          │ → User Task oluşur → Emergency Team'e atanır
└──────────────┘
```

---

## 📊 Teknoloji Stack Özeti

| Katman | Teknoloji | Dosya |
|--------|-----------|-------|
| **Web Framework** | FastAPI | `main.py` |
| **LLM** | Google Gemini 2.5 Flash | `agent.py`, `sentiment_analyzer.py` |
| **Embedding** | Gemini text-embedding-004 | `embedder.py` |
| **Vector DB** | Qdrant | `retriever.py` |
| **Graph Engine** | LangGraph | `langraph/graphs/` |
| **STT** | OpenAI Whisper | `whisper_transcriber.py` |
| **BPM** | Flowable | `deploy_to_flowable.py` |
| **Veri Modelleri** | Pydantic v2 | `models.py` |
| **Frontend** | HTML/CSS/JS | `templates/index.html` |
| **Altyapı** | Docker Compose | `docker-compose.yml` |
