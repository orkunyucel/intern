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
app.use(express.json());

const PORT = 5001;
const CAMARA_API = 'http://localhost:5002';

// SSE CONNECTION STORAGE
const sseConnections = new Map();

// TASK STORAGE
const tasks = new Map();

// -----------------------------------------------------------------------------
// JSON-RPC 2.0 ERROR CODES
// -----------------------------------------------------------------------------
const JSON_RPC_ERRORS = {
  PARSE_ERROR: { code: -32700, message: 'Parse error' },
  INVALID_REQUEST: { code: -32600, message: 'Invalid Request' },
  METHOD_NOT_FOUND: { code: -32601, message: 'Method not found' },
  INVALID_PARAMS: { code: -32602, message: 'Invalid params' },
  INTERNAL_ERROR: { code: -32603, message: 'Internal error' }
};

// -----------------------------------------------------------------------------
// MCP TOOL DEFINITIONS
// -----------------------------------------------------------------------------
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
        status: 'WORKING',
        createdAt: new Date().toISOString()
      });

      // HEMEN taskId ile response döner
      res.json({
        jsonrpc: '2.0',
        result: {
          taskId,
          status: 'WORKING'
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

      await sleep(1500);

      // ⑨ CAMARA API'ye istek gönderiliyor
      sendSSE(taskId, {
        type: 'update',
        status: 'WORKING',
        step: '1.5',
        message: `CAMARA API çağrısı yapılıyor: POST /sessions (QoS: ${args.qosProfile})`,
        // userMessage YOK - bu sadece Admin Panel için
        apiCall: 'CAMARA_CREATE_SESSION'
      });

      await sleep(500);

      const response = await fetch(`${CAMARA_API}/sessions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          qosProfile: args.qosProfile,
          duration: args.duration || 3600,
          notificationUrl: `http://localhost:${PORT}/notify/${taskId}`
        })
      });

      const data = await response.json();

      // ⑩ CAMARA API'den 201 Created alındı
      sendSSE(taskId, {
        type: 'update',
        status: 'WORKING',
        step: '1.7',
        message: `CAMARA API yanıtı: 201 Created (sessionId: ${data.sessionId})`,
        // userMessage YOK - bu sadece Admin Panel için
        apiResponse: 'CAMARA_201_CREATED',
        sessionId: data.sessionId
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
      const response = await fetch(`${CAMARA_API}/sessions/${args.sessionId}`);

      if (response.status === 404) {
        sendSSE(taskId, {
          type: 'complete',
          status: 'SUCCESS',
          message: 'Session bulunamadı.',
          userMessage: 'Hız artırımı bulunamadı.',
          result: { error: 'Session not found' }
        });
      } else {
        const data = await response.json();
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
      const response = await fetch(`${CAMARA_API}/sessions/${args.sessionId}`, {
        method: 'DELETE'
      });

      if (response.status === 404) {
        sendSSE(taskId, {
          type: 'complete',
          status: 'SUCCESS',
          message: 'Session bulunamadı veya zaten silinmiş.',
          userMessage: 'Hız artırımı bulunamadı veya zaten sonlandırılmış.',
          result: { error: 'Session not found' }
        });
      } else {
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
      const response = await fetch(`${CAMARA_API}/network-context`);
      const data = await response.json();

      const activeText = data.activeSession
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
    connection.write(`data: ${JSON.stringify(data)}\n\n`);

    if (data.type === 'complete' || data.type === 'error') {
      setTimeout(() => {
        connection.end();
        sseConnections.delete(taskId);
      }, 100);
    }
  }
}

// CAMARA CALLBACK HANDLER
app.post('/notify/:taskId', (req, res) => {
  const { taskId } = req.params;
  const data = req.body;

  console.log(`[MCP] Notify received for task ${taskId}:`, data);

  const task = tasks.get(taskId);
  if (task) {
    task.status = 'SUCCESS';
    task.result = data;
    tasks.set(taskId, task);
  }

  sendSSE(taskId, {
    type: 'complete',
    status: 'SUCCESS',
    step: 3,
    message: `Ağ optimizasyonu tamamlandı! Yeni bandwidth: ${data.bandwidth}. ${data.priceText || ''}`,
    userMessage: data.priceDifference > 0
      ? `Hızınız ${data.bandwidth}'e yükseltildi! (${data.priceDifference} TL ek ücret) ✓`
      : data.priceDifference < 0
        ? `Hızınız ${data.bandwidth}'e düşürüldü. (${Math.abs(data.priceDifference)} TL tasarruf) ✓`
        : `Hızınız ${data.bandwidth}'e ayarlandı! ✓`,
    finalBandwidth: data.bandwidth,
    priceInfo: {
      oldPrice: data.oldPrice,
      newPrice: data.newPrice,
      priceDifference: data.priceDifference,
      priceText: data.priceText
    },
    result: data
  });

  res.json({ received: true });
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

  req.on('close', () => {
    console.log(`[MCP] SSE connection closed for task ${taskId}`);
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
