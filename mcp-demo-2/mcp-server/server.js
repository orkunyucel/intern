/**
 * MCP (Model Context Protocol) Server - JSON-RPC 2.0
 *
 * Bu dosya MCP protokolünü JSON-RPC 2.0 standardına uygun şekilde implement eder.
 * MCP, LLM'lerin external tool'lara erişmesini sağlayan bir standarttır.
 *
 * JSON-RPC 2.0 Methods:
 * - initialize: Handshake, capabilities
 * - tools/list: Tool listesi al
 * - tools/call: Tool çalıştır
 *
 * Port: 5001
 */

const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
app.use(cors());

// JSON body parser - hem application/json hem application/cloudevents+json için
app.use(express.json({
  type: ['application/json', 'application/cloudevents+json']
}));

const PORT = 5001;
const CAMARA_API = 'http://localhost:5002';

// POC MOCK DATA - CAMARA request için sabit değerler
// Gerçek sistemde bunlar authentication'dan veya context'ten gelir
const MOCK_DEVICE = {
  phoneNumber: "+905551234567"
};

const MOCK_APPLICATION_SERVER = {
  ipv4Address: "192.168.1.100"
};

// QoS profil → bandwidth/fiyat mapping
// CAMARA bu bilgileri vermez, biz local olarak tutuyoruz
const QOS_PROFILE_INFO = {
  QOS_S: { bandwidth: '50 Mbps', latency: '100ms', price: 100 },
  QOS_M: { bandwidth: '200 Mbps', latency: '50ms', price: 200 },
  QOS_L: { bandwidth: '700 Mbps', latency: '20ms', price: 300 },
  QOS_E: { bandwidth: '1500 Mbps', latency: '10ms', price: 400 }
};

// Kullanıcının mevcut durumu (fiyat farkı hesabı için)
let currentUserPlan = 'QOS_M';
let currentUserPrice = 200;

// SSE CONNECTION STORAGE
const sseConnections = new Map();

// TASK STORAGE
const tasks = new Map();

// JSON-RPC 2.0 ERROR CODES
const JSON_RPC_ERRORS = {
  PARSE_ERROR: { code: -32700, message: 'Parse error' },
  INVALID_REQUEST: { code: -32600, message: 'Invalid Request' },
  METHOD_NOT_FOUND: { code: -32601, message: 'Method not found' },
  INVALID_PARAMS: { code: -32602, message: 'Invalid params' },
  INTERNAL_ERROR: { code: -32603, message: 'Internal error' }
};

// MCP TOOL DEFINITIONS
const TOOLS = [
  {
    name: 'create_qos_session',
    description: `Creates a Quality-on-Demand (QoD) session to optimize network bandwidth.

    BANDWIDTH MAPPING:
    - QOS_S: For bandwidth needs under 100 Mbps (basic streaming, browsing)
    - QOS_M: For bandwidth needs 100-500 Mbps (HD streaming, video calls)
    - QOS_L: For bandwidth needs 500-1000 Mbps (4K streaming, large downloads)
    - QOS_E: For bandwidth needs over 1000 Mbps (ultra-high performance, gaming)

    PRICE MAPPING (Monthly):
    - QOS_S: 100 TL
    - QOS_M: 200 TL  
    - QOS_L: 300 TL
    - QOS_E: 400 TL

    Note: User pays the DIFFERENCE between current plan and new plan.
    For Example Purposes: If user is on QOS_M (200 TL) and upgrades to QOS_L (300 TL), they pay 100 TL difference.

    For Example Purposes: If user says "700 yap" or "700 Mbps istiyorum", use QOS_L profile.`,
    inputSchema: {
      type: 'object',
      properties: {
        qosProfile: {
          type: 'string',
          enum: ['QOS_S', 'QOS_M', 'QOS_L', 'QOS_E'],
          description: 'QoS profile based on bandwidth needs'
        },
        duration: {
          type: 'integer',
          description: 'Session duration in seconds (default: 3600)'
        }
      },
      required: ['qosProfile']
    }
  },
  {
    name: 'get_qos_status',
    description: 'Get the current status of a QoS session by its session ID.',
    inputSchema: {
      type: 'object',
      properties: {
        sessionId: {
          type: 'string',
          description: 'The session ID returned from create_qos_session'
        }
      },
      required: ['sessionId']
    }
  },
  {
    name: 'delete_qos_session',
    description: 'Terminate/delete an active QoS session.',
    inputSchema: {
      type: 'object',
      properties: {
        sessionId: {
          type: 'string',
          description: 'The session ID to delete'
        }
      },
      required: ['sessionId']
    }
  },
  {
    name: 'get_network_context',
    description: `Kullanıcının mevcut ağ durumunu ve bandwidth bilgisini alır.

    ÖNEMLİ: Bu tool'u şu durumlarda KULLAN:
    - "mevcut bandwidth", "şu anki hız", "internet hızım", "bandwithim nedir"
    - "ağ durumum", "network durumu", "bağlantı durumu"
    - "konum", "neredeyim", "roaming"
    - "aktif session var mı", "oturum durumu"

    Dönen bilgiler: currentBandwidth (örn: 200 Mbps), city, country, networkType, activeSession.`,
    inputSchema: {
      type: 'object',
      properties: {
        msisdn: {
          type: 'string',
          description: 'Opsiyonel telefon numarası'
        }
      },
      required: []
    }
  }
];

// JSON-RPC 2.0 ROUTER
// Tek endpoint: POST /rpc  
app.post('/rpc', async (req, res) => {
  const { jsonrpc, method, params, id } = req.body;

  // JSON-RPC 2.0 version kontrolü
  if (jsonrpc !== '2.0') {
    return res.json({
      jsonrpc: '2.0',
      error: JSON_RPC_ERRORS.INVALID_REQUEST,
      id: id || null
    });
  }

  console.log(`[MCP] JSON-RPC request: ${method}`, params);

  // Method routing
  switch (method) {
    // INITIALIZE - Handshake + Capabilities
    case 'initialize':
      return res.json({
        jsonrpc: '2.0',
        result: {
          protocolVersion: '2025-03-26',
          capabilities: {
            tools: {}
          },
          serverInfo: {
            name: 'camara-mcp-server',
            version: 'mcp-demo'
          }
        },
        id
      });

    // TOOLS/LIST - Tool listesi döner
    case 'tools/list':
      return res.json({
        jsonrpc: '2.0',
        result: {
          tools: TOOLS
        },
        id
      });

    // TOOLS/CALL - Tool çalıştır
    case 'tools/call':
      const { name, arguments: args } = params || {};

      if (!name) {
        return res.json({
          jsonrpc: '2.0',
          error: { ...JSON_RPC_ERRORS.INVALID_PARAMS, message: 'Missing tool name' },
          id
        });
      }

      // Tool'u bul
      const tool = TOOLS.find(t => t.name === name);
      if (!tool) {
        return res.json({
          jsonrpc: '2.0',
          error: { ...JSON_RPC_ERRORS.METHOD_NOT_FOUND, message: `Tool not found: ${name}` },
          id
        });
      }

      // Task ID oluştur
      const taskId = uuidv4();

      console.log(`[MCP] tools/call: ${name}`, args);

      // Task bilgisini sakla
      tasks.set(taskId, {
        taskId,
        tool: name,
        arguments: args,
        status: 'ACCEPTED',  // HTTP response için - SSE'de WORKING kullanılır
        createdAt: new Date().toISOString()
      });

      // HEMEN taskId ile response döner - ACCEPTED = işlem kabul edildi, SSE'den takip et
      res.json({
        jsonrpc: '2.0',
        result: {
          taskId,
          status: 'ACCEPTED'  // SSE değil, HTTP response
        },
        id
      });

      // Async olarak tool'u çalıştır
      executeToolAsync(taskId, name, args || {});
      return;

    // UNKNOWN METHOD
    default:
      return res.json({
        jsonrpc: '2.0',
        error: JSON_RPC_ERRORS.METHOD_NOT_FOUND,
        id
      });
  }
});

// ASYNC TOOL EXECUTION
async function executeToolAsync(taskId, toolName, args) {
  // CREATE QOS SESSION
  if (toolName === 'create_qos_session') {
    try {
      await sleep(500);

      sendSSE(taskId, {
        type: 'update',
        status: 'WORKING',
        step: 1,
        message: 'İstek alındı, CAMARA API\'ye bağlanılıyor...',
        userMessage: 'İnternet hızınız ayarlanıyor...',
        currentBandwidth: '0 Mbps'
      });

      await sleep(1500); // network tarafı için

      // ⑨ CAMARA API'ye istek gönderiliyor
      const camaraRequestBody = {
        // CAMARA CreateSession schema
        device: MOCK_DEVICE,                    // POC için sabit device
        applicationServer: MOCK_APPLICATION_SERVER,  // POC için sabit backend IP
        qosProfile: args.qosProfile,
        duration: args.duration || 3600,
        sink: `http://localhost:${PORT}/notify/${taskId}`  // Callback URL (CAMARA formatı)
      };

      sendSSE(taskId, {
        type: 'update',
        status: 'WORKING',
        step: '1.5',
        message: `CAMARA API çağrısı yapılıyor: POST /sessions (QoS: ${args.qosProfile})`,
        // userMessage YOK - bu sadece Admin Panel için
        apiCall: 'CAMARA_CREATE_SESSION',
        camaraRequest: {
          method: 'POST',
          url: `${CAMARA_API}/sessions`,
          headers: { 'Content-Type': 'application/json' },
          body: camaraRequestBody
        }
      });

      await sleep(500);

      // CAMARA API'ye gerçek formatta request gönder
      const response = await fetch(`${CAMARA_API}/sessions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(camaraRequestBody)
      });

      if (!response.ok) {
        const errorText = await response.text();
        sendSSE(taskId, {
          type: 'error',
          message: `CAMARA API hata: ${response.status} ${response.statusText} - ${errorText}`,
          camaraStatus: response.status,
          camaraError: errorText
        });
        return;
      }

      const data = await response.json();

      // ⑩ CAMARA API'den 201 Created alındı
      sendSSE(taskId, {
        type: 'update',
        status: 'WORKING',
        step: '1.7',
        message: `CAMARA API yanıtı: 201 Created (sessionId: ${data.sessionId})`,
        // userMessage YOK - bu sadece Admin Panel için
        apiResponse: 'CAMARA_201_CREATED',
        sessionId: data.sessionId,
        camaraStatus: response.status,
        camaraResponse: data
      });

      const task = tasks.get(taskId);
      task.sessionId = data.sessionId;
      task.qosProfile = data.qosProfile;
      tasks.set(taskId, task);

      await sleep(500);

      sendSSE(taskId, {
        type: 'update',
        status: 'WORKING',
        step: 2,
        message: 'QoS session oluşturuldu, ağ konfigürasyonu uygulanıyor...',
        userMessage: 'Bağlantınız optimize ediliyor...',
        sessionId: data.sessionId,
        qosProfile: data.qosProfile,
        currentBandwidth: '0 Mbps'
      });

    } catch (err) {
      sendSSE(taskId, { type: 'error', message: err.message });
    }
  }

  // GET QOS STATUS
  else if (toolName === 'get_qos_status') {
    try {
      const camaraUrl = `${CAMARA_API}/sessions/${args.sessionId}`;
      const response = await fetch(camaraUrl);

      if (response.status === 404) {
        const errorText = await response.text();
        sendSSE(taskId, {
          type: 'update',
          status: 'WORKING',
          step: 'status',
          message: `CAMARA API yanıtı: 404 Not Found (sessionId: ${args.sessionId})`,
          camaraRequest: { method: 'GET', url: camaraUrl },
          camaraStatus: response.status,
          camaraResponseText: errorText
        });
        sendSSE(taskId, {
          type: 'complete',
          status: 'SUCCESS',
          message: 'Session bulunamadı.',
          userMessage: 'Hız artırımı bulunamadı.',
          result: { error: 'Session not found' }
        });
      } else if (!response.ok) {
        const errorText = await response.text();
        sendSSE(taskId, {
          type: 'update',
          status: 'WORKING',
          step: 'status',
          message: `CAMARA API hata: ${response.status} ${response.statusText}`,
          camaraRequest: { method: 'GET', url: camaraUrl },
          camaraStatus: response.status,
          camaraResponseText: errorText
        });
        sendSSE(taskId, {
          type: 'error',
          message: `CAMARA API hata: ${response.status} ${response.statusText} - ${errorText}`
        });
      } else {
        const data = await response.json();
        sendSSE(taskId, {
          type: 'update',
          status: 'WORKING',
          step: 'status',
          message: `CAMARA API yanıtı: ${response.status} OK`,
          camaraRequest: { method: 'GET', url: camaraUrl },
          camaraStatus: response.status,
          camaraResponse: data
        });
        const statusText = data.qosStatus === 'AVAILABLE' ? 'aktif' : 'işleniyor';
        sendSSE(taskId, {
          type: 'complete',
          status: 'SUCCESS',
          message: `Session durumu: ${data.qosStatus}, Profil: ${data.qosProfile}`,
          userMessage: `Hız artırımınız ${statusText}. Mevcut hızınız: ${data.bandwidth || 'bilinmiyor'}.`,
          result: data
        });
      }
    } catch (err) {
      sendSSE(taskId, { type: 'error', message: err.message });
    }
  }

  // DELETE QOS SESSION
  else if (toolName === 'delete_qos_session') {
    try {
      const camaraUrl = `${CAMARA_API}/sessions/${args.sessionId}`;
      const response = await fetch(camaraUrl, {
        method: 'DELETE'
      });

      if (response.status === 404) {
        const errorText = await response.text();
        sendSSE(taskId, {
          type: 'update',
          status: 'WORKING',
          step: 'delete',
          message: `CAMARA API yanıtı: 404 Not Found (sessionId: ${args.sessionId})`,
          camaraRequest: { method: 'DELETE', url: camaraUrl },
          camaraStatus: response.status,
          camaraResponseText: errorText
        });
        sendSSE(taskId, {
          type: 'complete',
          status: 'SUCCESS',
          message: 'Session bulunamadı veya zaten silinmiş.',
          userMessage: 'Hız artırımı bulunamadı veya zaten sonlandırılmış.',
          result: { error: 'Session not found' }
        });
      } else if (!response.ok && response.status !== 204) {
        const errorText = await response.text();
        sendSSE(taskId, {
          type: 'update',
          status: 'WORKING',
          step: 'delete',
          message: `CAMARA API hata: ${response.status} ${response.statusText}`,
          camaraRequest: { method: 'DELETE', url: camaraUrl },
          camaraStatus: response.status,
          camaraResponseText: errorText
        });
        sendSSE(taskId, {
          type: 'error',
          message: `CAMARA API hata: ${response.status} ${response.statusText} - ${errorText}`
        });
      } else {
        sendSSE(taskId, {
          type: 'update',
          status: 'WORKING',
          step: 'delete',
          message: `CAMARA API yanıtı: ${response.status} No Content`,
          camaraRequest: { method: 'DELETE', url: camaraUrl },
          camaraStatus: response.status
        });
        sendSSE(taskId, {
          type: 'complete',
          status: 'SUCCESS',
          message: 'QoS session başarıyla sonlandırıldı.',
          userMessage: 'Hız artırımınız sonlandırıldı. Normal hıza döndünüz (200 Mbps).',
          finalBandwidth: '200 Mbps',
          result: { deleted: true, sessionId: args.sessionId }
        });
      }
    } catch (err) {
      sendSSE(taskId, { type: 'error', message: err.message });
    }
  }

  // GET NETWORK CONTEXT
  else if (toolName === 'get_network_context') {
    try {
      const camaraUrl = `${CAMARA_API}/network-context`;

      // Timeout ile fetch (hang engellemek için)
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);

      const response = await fetch(camaraUrl, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (!response.ok) {
        const errorText = await response.text();
        sendSSE(taskId, {
          type: 'update',
          status: 'WORKING',
          step: 'context',
          message: `CAMARA API hata: ${response.status} ${response.statusText}`,
          camaraRequest: { method: 'GET', url: camaraUrl },
          camaraStatus: response.status,
          camaraResponseText: errorText
        });
        sendSSE(taskId, {
          type: 'error',
          message: `CAMARA API hata: ${response.status} ${response.statusText} - ${errorText}`
        });
        return;
      }

      const data = await response.json();
      sendSSE(taskId, {
        type: 'update',
        status: 'WORKING',
        step: 'context',
        message: `CAMARA API yanıtı: ${response.status} OK`,
        camaraRequest: { method: 'GET', url: camaraUrl },
        camaraStatus: response.status,
        camaraResponse: data
      });

      const activeText = data.activeSessions > 0
        ? 'Şu anda hız artırımınız aktif.'
        : 'Şu anda normal hızda bağlısınız.';

      sendSSE(taskId, {
        type: 'complete',
        status: 'SUCCESS',
        message: `Mevcut bandwidth: ${data.currentBandwidth}. Konum: ${data.city}, ${data.country}.`,
        userMessage: `İnternet hızınız: ${data.currentBandwidth}. Konum: ${data.city}. ${activeText}`,
        currentBandwidth: data.currentBandwidth,
        result: data
      });
    } catch (err) {
      sendSSE(taskId, { type: 'error', message: err.message });
    }
  }
}

// HELPER FUNCTIONS
function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function sendSSE(taskId, data) {
  const connection = sseConnections.get(taskId);

  if (connection) {
    connection.write(`data: ${JSON.stringify(data)}\n\n`); // akış olarak algılanan satır 

    if (data.type === 'complete' || data.type === 'error') {
      setTimeout(() => {
        connection.end();
        sseConnections.delete(taskId);
      }, 100);
    }
  }
}

// CAMARA CALLBACK HANDLER - CloudEvents formatında callback alır
// CAMARA async işlem tamamlandığında buraya POST yapar
app.post('/notify/:taskId', (req, res) => {
  const { taskId } = req.params;
  const cloudEvent = req.body;  // CloudEvents formatında gelir

  // Debug: Gelen veriyi tam logla
  console.log(`[MCP] ✓ CloudEvent received for task ${taskId}`);
  console.log(`[MCP]   Full body:`, JSON.stringify(cloudEvent, null, 2));

  // CloudEvents formatından verileri çıkar
  const eventData = cloudEvent.data || {};
  const customData = cloudEvent._custom || {};

  console.log(`[MCP]   Event type: ${cloudEvent.type}`);
  console.log(`[MCP]   Event data:`, eventData);
  console.log(`[MCP]   Custom data:`, customData);

  // Task'ı güncelle
  const task = tasks.get(taskId);
  if (task) {
    task.status = 'SUCCESS';
    task.result = { cloudEvent, eventData, customData };
    tasks.set(taskId, task);
  }

  // Admin panel için: CAMARA notify body'yi SSE ile yayınla
  sendSSE(taskId, {
    type: 'update',
    status: 'WORKING',
    step: '2.5',
    message: 'CAMARA notify callback alındı',
    apiCall: 'CAMARA_NOTIFY',
    camaraRequest: {
      method: 'POST',
      url: `http://localhost:${PORT}/notify/${taskId}`,
      headers: { 'Content-Type': req.headers['content-type'] }
    },
    notifyBody: cloudEvent
  });

  // Bandwidth ve fiyat bilgilerini al
  // Önce customData'dan, yoksa local mapping'den
  const qosProfile = customData.qosProfile || task?.qosProfile;
  const bandwidth = customData.bandwidth || QOS_PROFILE_INFO[qosProfile]?.bandwidth || 'bilinmiyor';

  // Fiyat bilgileri - CAMARA'dan gelen custom veya hesaplanan
  const oldPrice = customData.oldPrice !== undefined ? customData.oldPrice : currentUserPrice;
  const newPrice = customData.newPrice !== undefined ? customData.newPrice : QOS_PROFILE_INFO[qosProfile]?.price;
  const priceDifference = customData.priceDifference !== undefined ? customData.priceDifference : (newPrice - oldPrice);

  console.log(`[MCP]   Calculated: bandwidth=${bandwidth}, oldPrice=${oldPrice}, newPrice=${newPrice}, diff=${priceDifference}`);

  // Kullanıcı state'ini güncelle
  if (qosProfile) {
    currentUserPlan = qosProfile;
    currentUserPrice = newPrice;
  }

  // Kullanıcıya gösterilecek mesajı oluştur
  let userMessage;
  if (priceDifference > 0) {
    userMessage = `Hızınız ${bandwidth}'e yükseltildi! (${priceDifference} TL ek ücret) ✓`;
  } else if (priceDifference < 0) {
    userMessage = `Hızınız ${bandwidth}'e düşürüldü. (${Math.abs(priceDifference)} TL tasarruf) ✓`;
  } else {
    userMessage = `Hızınız ${bandwidth}'e ayarlandı! ✓`;
  }

  // SSE ile kullanıcıya bildir
  sendSSE(taskId, {
    type: 'complete',
    status: 'SUCCESS',
    step: 3,
    message: `Ağ optimizasyonu tamamlandı! Bandwidth: ${bandwidth}`,
    userMessage,
    finalBandwidth: bandwidth,
    priceInfo: {
      oldPrice,
      newPrice,
      priceDifference
    },
    // CAMARA CloudEvents - TÜM alanları gönder
    cloudEvent: {
      id: cloudEvent.id,
      source: cloudEvent.source,
      type: cloudEvent.type,
      specversion: cloudEvent.specversion,
      time: cloudEvent.time,
      datacontenttype: cloudEvent.datacontenttype,
      data: {
        sessionId: eventData.sessionId,
        qosStatus: eventData.qosStatus,
        statusInfo: eventData.statusInfo
      }
    }
  });

  // CAMARA spesine göre 204 No Content dönmeli
  res.status(204).send();
});

// SSE ENDPOINT
app.get('/sse/:taskId', (req, res) => {
  const { taskId } = req.params;

  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders();

  console.log(`[MCP] SSE connection opened for task ${taskId}`);

  sseConnections.set(taskId, res);

  res.write(`data: ${JSON.stringify({ type: 'connected', taskId })}\n\n`);

  const heartbeat = setInterval(() => {
    if (sseConnections.get(taskId)) {
      res.write(`data: ${JSON.stringify({ type: 'ping', taskId })}\n\n`);
    }
  }, 15000);

  req.on('close', () => {
    console.log(`[MCP] SSE connection closed for task ${taskId}`);
    clearInterval(heartbeat);
    sseConnections.delete(taskId);
  });
});

// HEALTH CHECK
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'mcp-server', protocol: 'json-rpc-2.0' });
});

// SERVER START
app.listen(PORT, () => {
  console.log(`[MCP] Server running on http://localhost:${PORT}`);
  console.log(`[MCP] Protocol: JSON-RPC 2.0`);
  console.log(`[MCP] Endpoint: POST /rpc`);
  console.log(`[MCP] Tools: ${TOOLS.map(t => t.name).join(', ')}`);
});
