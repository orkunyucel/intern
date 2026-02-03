# MCP + CAMARA POC

Kullanıcı doğal dil komutları ile network bandwidth ayarı yapabildiği basit bir POC.

## Mimari

```
USER-UI (HTML) ←→ AI-AGENT (Python/Flask) ←→ MCP-SERVER (Node.js) ←→ CAMARA-API (Node.js)
                         ↓
                   Gemini 2.5 Flash
```

## Hızlı Başlatma

Tüm servisleri tek komutla başlat:

```bash
./run_all.sh
```

Durdurmak için:

```bash
./stop_all.sh
```

## UI

Tarayıcıdan aç:

```
http://localhost:3000
```

## Kullanım

1. UI'da mesaj yaz
2. Agent onay isteyecek
3. "evet" yaz
4. SSE ile durum güncellemelerini izle

## Test Promptları

| Tool | Örnek Promptlar |
|------|-----------------|
| `create_qos_session` | "hızımı 800 yap", "700 Mbps istiyorum", "limitimi artır" |
| `get_qos_status` | Bu tool sessionId gerektirir (otomatik kullanılmaz) |
| `delete_qos_session` | Bu tool sessionId gerektirir (otomatik kullanılmaz) |
| `get_network_context` | "hızım nedir", "mevcut bandwidth", "internet hızım kaç" |

### QoS Profil Mapping

| Profil | Bandwidth | Fiyat | Örnek Prompt |
|--------|-----------|-------|--------------|
| QOS_S | 50 Mbps | 100 TL | "hızımı 50 yap", "temel paket" |
| QOS_M | 200 Mbps | 200 TL | "hızımı 200 yap", "standart paket" |
| QOS_L | 700 Mbps | 300 TL | "hızımı 800 yap", "700 istiyorum" |
| QOS_E | 1500 Mbps | 400 TL | "1500 yap", "ultra paket" |

## Portlar

| Servis | Port |
|--------|------|
| User UI | 3000 |
| AI Agent | 5003 |
| MCP Server | 5001 |
| CAMARA API | 5002 |

## .env

Kök dizinde `.env` dosyası oluştur:

```
GEMINI_API_KEY=your_gemini_api_key
```

## Flow

```
① User "700 yap" → UI
② Agent → MCP: tool list request
③ MCP → Agent: tools info
④ Agent → Gemini: ask + tools
⑤ Gemini → Agent: action plan
⑥ Agent → UI: offer
⑦ User "evet" → Agent
⑧ Agent → MCP: execute
⑨ MCP → CAMARA: create session
⑩ CAMARA → MCP: 201 Created
⑪ MCP → Agent → UI: SSE (WORKING)
⑫ CAMARA → MCP: /notify callback
⑬ MCP → Agent → UI: SSE (SUCCESS)
⑭ Task done
```
