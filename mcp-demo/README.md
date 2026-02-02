# 🚀 MCP + CAMARA POC

**Model Context Protocol (MCP)** standartlarını kullanarak Yapay Zeka (LLM) ile Telekomünikasyon Ağ API'leri (CAMARA) arasında güvenli ve standart entegrasyon sağlayan bir Proof of Concept çalışmasıdır.

> This POC implements the **MCP 2025-11-25 draft specification** (tool discovery + tool execution subset).

---

## ✨ Özellikler

| Özellik                         | Açıklama                                                    |
| ------------------------------- | ----------------------------------------------------------- |
| **🔌 Gerçek MCP Protokolü**     | JSON-RPC 2.0 üzerinden `tools/list` ve `tools/call` desteği |
| **🤖 AI Agent (ReAct Pattern)** | LLM'in kendi kendine tool keşfetmesi ve kullanması          |
| **📡 CAMARA API Entegrasyonu**  | QoD (Quality on Demand) API v1.1.0 uyumlu                   |
| **🔐 OAuth 2.0**                | Client credentials flow ile güvenli kimlik doğrulama        |
| **📺 Real-time Streaming**      | SSE (Server-Sent Events) ile canlı durum takibi             |
| **🔄 Dual Mode**                | REAL (gerçek CAMARA) ve MOCK (simülasyon) mod desteği       |

---

## 🔐 Execution Model (Critical Design Principle)

> ⚠️ **Important Design Principle**
> LLM hiçbir zaman tool çalıştırmaz.

Tüm tool execution işlemleri **sadece Agent tarafından** MCP Client üzerinden yapılır.

LLM yalnızca:

* Reasoning (akıl yürütme)
* Tool seçimi
* Parametre üretimi

rollerini üstlenir.

Bu mimari şunları garanti eder:

* Deterministic execution
* Güvenlik izolasyonu
* Tam audit edilebilirlik
* Hallucination’a karşı koruma

---

## 🔄 Sistem Akışı (Flow)

Aşağıdaki diyagram, kullanıcının isteğinin (örn: "Hızımı 700 yap") sistemde nasıl işlendiğini **adım adım** göstermektedir:

```
  USER        CLIENT (Agent)        LLM        MCP SERVER        CAMARA API     NETWORK
  |               |               |              |               |             |
  | ① "700 yap"   |               |              |               |             |
  |-------------->|               |              |               |             |
  |               |               |              |               |             |
  |               | ② tool list   |              |               |             |
  |               |------------------------------>|               |             |
  |               |               |              |               |             |
  |               |<------------------------------|               |             |
  |               | ③ tools info  |              |               |             |
  |               |               |              |               |             |
  |               | ④ ask + tools |              |               |             |
  |               |-------------->|              |               |             |
  |               |               |              |               |             |
  |               |<--------------|              |               |             |
  |               | ⑤ ACTION plan |              |               |             |
  |               |               |              |               |             |
  |               | ⑥ execute     |              |               |             |
  |               |------------------------------>|               |             |
  |               |               |              |               |             |
  |               |               |              | ⑦ API call    |             |
  |               |               |              |-------------->|             |
  |               |               |              |               |             |
  |               |               |              |               | ⑧ ACTUAL    |
  |               |               |              |               |   WORK      |
  |               |               |              |               | (Bandwidth) |
  |               |               |              |               |  değişiyor  |
  |               |               |              |<--------------|             |
  |               |               |              | ⑨ done        |             |
  |               |               |<-------------|               |             |
  |               |               | ⑩ response   |               |             |
  |               |<--------------|              |               |             |
  |               | ⑪ action done |              |               |             |
  |               |-------------->|              |               |             |
  |               |               |              |               |             |
  |               |<--------------|              |               |             |
  |               | ⑫ final msg   |              |               |             |
  |               |               |              |               |             |
  |<--------------|               |              |               |             |
  | ⑬ "500 → 700" |               |              |               |             |
```

> In **MOCK mode**, step ⑧ updates the in-memory `QodState`.
> In **REAL mode**, CAMARA QoD API performs actual network configuration.

---

## 📋 Akış Adımları

| Adım  | Kaynak → Hedef          | Açıklama                                                    |
| ----- | ----------------------- | ----------------------------------------------------------- |
| **①** | User → Agent            | Kullanıcı doğal dilde istek gönderir: *"Hızımı 700 yap"*    |
| **②** | Agent → MCP Server      | Agent, MCP Server'dan mevcut tool'ları ister (`tools/list`) |
| **③** | MCP Server → Agent      | Server, kayıtlı CAMARA tool tanımlarını döner               |
| **④** | Agent → LLM             | Kullanıcı sorusu + tool tanımları LLM'e gönderilir          |
| **⑤** | LLM → Agent             | LLM hangi tool'u çağıracağına karar verir (Function Call)   |
| **⑥** | Agent → MCP Server      | Agent, belirlenen tool'u çalıştırır (`tools/call`)          |
| **⑦** | MCP Server → CAMARA API | MCP Server, gerçek CAMARA API'sine istek atar               |
| **⑧** | CAMARA API → Network    | API, telekom ağında bandwidth değişikliği yapar             |
| **⑨** | CAMARA API → MCP Server | İşlem sonucu döner                                          |
| **⑩** | MCP Server → Agent      | Tool sonucu Agent'a iletilir                                |
| **⑪** | Agent → LLM             | Tool sonucu LLM'e gönderilir                                |
| **⑫** | LLM → Agent             | LLM son kullanıcı mesajını üretir                           |
| **⑬** | Agent → User            | *"Hızınız 500 Mbps'den 700 Mbps'e yükseltildi"*             |

---

## 🧩 Bileşenler

### 1. AI Agent (`agent/`)

| Dosya                     | Açıklama                                                            |
| ------------------------- | ------------------------------------------------------------------- |
| `AiAgent.java`            | Sistemin beyni. **ReAct (Reasoning + Acting)** döngüsünü çalıştırır |
| `AgentController.java`    | REST API endpoints (SSE streaming + sync)                           |

**ReAct Döngüsü:**

1. MCP Server'dan mevcut tool'ları öğrenir (`tools/list`)
2. Kullanıcı isteğini ve tool tanımlarını LLM'e gönderir
3. LLM'in functionCall isteğini yakalar
4. Tool execution işlemini MCP Client üzerinden yapar
5. Tool sonucunu tekrar LLM'e besleyerek nihai yanıtı üretir

---

### 2. MCP Client (`mcp/client/`)

| Dosya            | Açıklama                                                  |
| ---------------- | --------------------------------------------------------- |
| `McpClient.java` | MCP Server ile **JSON-RPC 2.0** formatında iletişim kurar |

**Endpoint:**
* `/mcp/jsonrpc` (MCP Server'a istek gönderir)

Desteklenen metodlar:

* `initialize`
* `tools/list`
* `tools/call`

---

### 3. MCP Server (`mcp/server/`)

| Dosya                      | Açıklama                                       |
| -------------------------- | ---------------------------------------------- |
| `McpServerController.java` | JSON-RPC 2.0 endpoint (`/mcp/jsonrpc`)         |
| `ToolRegistry.java`        | CAMARA tool tanımlarını tutar                  |
| `ToolExecutor.java`        | Tool çağrılarını gerçek servislere yönlendirir |

---

### 4. Adapter Layer (`camara/adapter/`)

| Dosya                   | Açıklama                                                         |
| ----------------------- | ---------------------------------------------------------------- |
| `CamaraAdapter.java`    | CAMARA API ↔ MCP arası adaptör. **REAL** ve **MOCK** mod desteği |

**Özellikler:**
* Mode detection (`isRealCamaraConfigured()`)
* REAL mode: `CamaraApiClient` kullanır
* MOCK mode: `CamaraMockController` + `QodState` kullanır

---

### 5. CAMARA Layer (`camara/`)

| Dosya                       | Açıklama                               |
| --------------------------- | -------------------------------------- |
| `client/CamaraApiClient.java` | Gerçek CAMARA QoD API v1.1.0 istemcisi (OAuth 2.0, session management) |
| `mock/CamaraMockController.java` | Mock CAMARA endpoints (device-location, qod/status, qod/set-bandwidth) |

---

### 6. Network Layer (`camara/mock/`)

| Dosya           | Açıklama                                  |
| --------------- | ----------------------------------------- |
| `QodState.java` | In-memory bandwidth state (100-1000 Mbps) |

**Not:** MOCK mode'da network state'i simüle eder. REAL mode'da gerçek telekom ağı kullanılır.

---

### 7. History & API Layer (`api/`, `history/`)

| Dosya                          | Açıklama                                    |
| ------------------------------ | ------------------------------------------- |
| `api/agent/AgentController.java` | REST endpoints (`/mcp/agent/run`, `/mcp/agent/run-sync`) |
| `api/history/HistoryController.java` | Execution history endpoints (opsiyonel)     |
| `history/ExecutionHistoryService.java` | Execution trace kayıt servisi               |

---

## 🔧 Kayıtlı MCP Tool'ları

| Tool                  | Açıklama                             | Parametreler                            | Mode      |
| --------------------- | ------------------------------------ | --------------------------------------- | --------- |
| `get_network_context` | Cihaz konum ve ağ bilgisini alır     | `msisdn`                                | REAL/MOCK |
| `get_qod_context`     | Mevcut QoS session durumunu gösterir | -                                       | REAL/MOCK |
| `create_qos_session`  | Yeni QoS session oluşturur           | `qosProfile`, `duration`, `phoneNumber` | REAL only |
| `end_qos_session`     | Aktif session'ı sonlandırır          | -                                       | REAL only |
| `extend_qos_session`  | Session süresini uzatır              | `additionalSeconds`                     | REAL only |
| `set_bandwidth`       | Doğrudan bandwidth ayarlar (MOCK)    | `bandwidthMbps` (100-1000)              | MOCK only |

**Not:** 
* `set_bandwidth` sadece MOCK mode'da çalışır. REAL mode'da QoS session'lar üzerinden bandwidth kontrolü yapılır.
* REAL mode için CAMARA API yapılandırması gereklidir (`application.yml`).

---

## 🛡️ Tool Selection & Safety

Tool seçimi şu mekanizmalarla sınırlandırılmıştır:

* System Instruction
* MCP tool registry
* JSON Schema parametre validasyonu

> LLM, tools listesi dışında hiçbir operasyon üretemez.
> Tool hallucination mimari olarak engellenmiştir.

---

## 📺 Streaming (SSE)

Streaming modda her ReAct adımı gerçek zamanlı olarak client’a gönderilir:

* Tool discovery (`TOOL_DISCOVERY` event)
* Tool call (`LLM_TOOL_CALL` event)
* Tool result (`TOOL_RESULT` event)
* Final response (`FINAL_RESPONSE` event)

**Endpoint:** `GET /mcp/agent/run?question=<query>&includeStatus=<true|false>`

**Event Types:**
* `status`: Agent başlangıç durumu
* `trace`: Her execution adımı (type, description, timestamp, data)
* `result`: Final sonuç (success, response, traceCount)
* `error`: Hata durumları



