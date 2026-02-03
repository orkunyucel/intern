/**
 * CAMARA QoD (Quality on Demand) API - Mock Implementation
 * 
 * CAMARA OpenAPI Spec v1.1.0 uyumlu mock API.
 * Gerçek CAMARA formatlarını kullanarak MCP Server'ı test etmemizi sağlar.
 *
 * FLOW'daki Rolü:
 * - ⑨ MCP Server'dan API call alır
 * - ⑩ 201 Created döner (CAMARA SessionInfo formatında)
 * - ⑫ 3 saniye sonra CloudEvents formatında callback gönderir
 *
 * Port: 5002
 */

const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = 5002;

// =============================================================================
// POC MOCK DATA - Gerçek CAMARA formatında
// Tüm mock veriler tek yerde toplanır, yarın gerçek data geldiğinde burası değişir
// =============================================================================

// POC için sabit device bilgisi (gerçekte 3-legged token'dan gelir)
const MOCK_DEVICE = {
  phoneNumber: "+905551234567"
};

// POC için sabit application server (backend IP)
const MOCK_APPLICATION_SERVER = {
  ipv4Address: "192.168.1.100"
};

// QoS profilleri ve karşılık gelen bandwidth/fiyat değerleri
// NOT: Gerçek CAMARA bu bilgileri ayrı QoS Profiles API'den verir
// POC için burada tutuyoruz
const QOS_PROFILES = {
  QOS_S: { bandwidth: '50 Mbps', latency: '100ms', price: 100 },
  QOS_M: { bandwidth: '200 Mbps', latency: '50ms', price: 200 },
  QOS_L: { bandwidth: '700 Mbps', latency: '20ms', price: 300 },
  QOS_E: { bandwidth: '1500 Mbps', latency: '10ms', price: 400 }
};

// Kullanıcının mevcut durumu (merkezi state)
const DEFAULT_BANDWIDTH = '200 Mbps';
const DEFAULT_PLAN = 'QOS_M';

const deviceState = {
  currentBandwidth: DEFAULT_BANDWIDTH,
  currentPlan: DEFAULT_PLAN,
  currentPrice: QOS_PROFILES[DEFAULT_PLAN].price,
  city: 'İstanbul',
  country: 'Türkiye',
  networkType: '5G',
  roaming: false,
  signalStrength: 'Excellent'
};

// Session storage (memory)
const sessions = new Map();

// =============================================================================
// POST /sessions - QoS Session Oluştur (CAMARA Spec v1.1.0)
// =============================================================================
// Request: CreateSession schema (device, applicationServer, qosProfile, duration, sink)
// Response: SessionInfo schema (sessionId, qosStatus, duration, applicationServer, qosProfile)
// =============================================================================
app.post('/sessions', (req, res) => {
  // CAMARA formatında request body parse et
  const {
    device,                    // Opsiyonel (3-legged token'da zorunlu değil)
    applicationServer,         // Zorunlu
    qosProfile,                // Zorunlu
    duration = 3600,           // Varsayılan 1 saat
    sink                       // Callback URL (eski adı: notificationUrl)
  } = req.body;

  // Validasyonlar
  if (!qosProfile || !QOS_PROFILES[qosProfile]) {
    return res.status(400).json({
      status: 400,
      code: 'INVALID_ARGUMENT',
      message: `Invalid qosProfile. Valid profiles: ${Object.keys(QOS_PROFILES).join(', ')}`
    });
  }

  if (!applicationServer) {
    return res.status(400).json({
      status: 400,
      code: 'INVALID_ARGUMENT',
      message: 'applicationServer is required'
    });
  }

  // Session ID üret (UUID v4)
  const sessionId = uuidv4();
  const now = new Date();

  // Session objesi (internal storage için)
  const session = {
    sessionId,
    qosProfile,
    qosStatus: 'REQUESTED',
    duration,
    device: device || MOCK_DEVICE,  // Device yoksa mock kullan
    applicationServer,
    sink,
    createdAt: now.toISOString()
  };

  sessions.set(sessionId, session);

  console.log(`[CAMARA] ✓ Session created: ${sessionId}`);
  console.log(`[CAMARA]   Profile: ${qosProfile}, Duration: ${duration}s`);

  // ⑩ 201 Created - CAMARA SessionInfo formatında response
  // NOT: bandwidth/latency burada YOK - gerçek CAMARA da vermez
  res.status(201).json({
    sessionId,
    qosStatus: 'REQUESTED',
    qosProfile,
    duration,
    applicationServer
  });

  // ⑫ Async callback - 3 saniye sonra CloudEvents formatında
  if (sink) {
    setTimeout(async () => {
      // Session durumunu güncelle
      session.qosStatus = 'AVAILABLE';
      session.startedAt = new Date().toISOString();
      session.expiresAt = new Date(Date.now() + duration * 1000).toISOString();
      sessions.set(sessionId, session);

      // Fiyat hesaplaması (bu bizim custom logic, CAMARA'da yok)
      const oldPrice = deviceState.currentPrice;
      const newPrice = QOS_PROFILES[qosProfile].price;
      const priceDifference = newPrice - oldPrice;

      // Merkezi state güncelle
      deviceState.currentBandwidth = QOS_PROFILES[qosProfile].bandwidth;
      deviceState.currentPlan = qosProfile;
      deviceState.currentPrice = newPrice;

      console.log(`[CAMARA] ✓ Session AVAILABLE: ${sessionId}`);
      console.log(`[CAMARA]   Bandwidth: ${deviceState.currentBandwidth}`);
      console.log(`[CAMARA]   Price: ${oldPrice} TL → ${newPrice} TL (${priceDifference >= 0 ? '+' : ''}${priceDifference} TL)`);

      // CloudEvents formatında callback gönder (CAMARA Spec)
      try {
        await fetch(sink, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/cloudevents+json'  // CloudEvents content type
          },
          body: JSON.stringify({
            // CloudEvents required fields
            id: uuidv4(),
            source: `http://localhost:${PORT}/sessions/${sessionId}`,
            type: 'org.camaraproject.quality-on-demand.v1.qos-status-changed',
            specversion: '1.0',
            time: new Date().toISOString(),
            datacontenttype: 'application/json',

            // Event payload
            data: {
              sessionId,
              qosStatus: 'AVAILABLE',
              statusInfo: null
            },

            // CUSTOM: Bizim eklediğimiz fiyat bilgileri (CAMARA standartında yok)
            // MCP Server bu bilgileri kullanarak kullanıcı mesajı oluşturur
            _custom: {
              qosProfile,
              bandwidth: QOS_PROFILES[qosProfile].bandwidth,
              latency: QOS_PROFILES[qosProfile].latency,
              oldPrice,
              newPrice,
              priceDifference
            }
          })
        });
        console.log(`[CAMARA] ✓ CloudEvent notification sent to ${sink}`);
      } catch (err) {
        console.error(`[CAMARA] ✗ Notification failed: ${err.message}`);
      }
    }, 3000);  // 3 saniye delay (network provisioning simülasyonu)
  }
});

// =============================================================================
// GET /sessions/:sessionId - Session Durumu Sorgula
// =============================================================================
app.get('/sessions/:sessionId', (req, res) => {
  const { sessionId } = req.params;
  const session = sessions.get(sessionId);

  if (!session) {
    return res.status(404).json({
      status: 404,
      code: 'NOT_FOUND',
      message: 'Session not found'
    });
  }

  // CAMARA SessionInfo formatında döndür
  res.json({
    sessionId: session.sessionId,
    qosStatus: session.qosStatus,
    qosProfile: session.qosProfile,
    duration: session.duration,
    applicationServer: session.applicationServer,
    startedAt: session.startedAt,
    expiresAt: session.expiresAt
  });
});

// =============================================================================
// DELETE /sessions/:sessionId - Session Sonlandır
// =============================================================================
app.delete('/sessions/:sessionId', (req, res) => {
  const { sessionId } = req.params;
  const session = sessions.get(sessionId);

  if (!session) {
    return res.status(404).json({
      status: 404,
      code: 'NOT_FOUND',
      message: 'Session not found'
    });
  }

  // Session sil ve state'i sıfırla
  sessions.delete(sessionId);
  deviceState.currentBandwidth = DEFAULT_BANDWIDTH;
  deviceState.currentPlan = DEFAULT_PLAN;
  deviceState.currentPrice = QOS_PROFILES[DEFAULT_PLAN].price;

  console.log(`[CAMARA] ✓ Session deleted: ${sessionId}`);
  console.log(`[CAMARA]   Bandwidth reset to: ${DEFAULT_BANDWIDTH}`);

  // Callback gönder (CloudEvents formatında)
  if (session.sink) {
    fetch(session.sink, {
      method: 'POST',
      headers: { 'Content-Type': 'application/cloudevents+json' },
      body: JSON.stringify({
        id: uuidv4(),
        source: `http://localhost:${PORT}/sessions/${sessionId}`,
        type: 'org.camaraproject.quality-on-demand.v1.qos-status-changed',
        specversion: '1.0',
        time: new Date().toISOString(),
        datacontenttype: 'application/json',
        data: {
          sessionId,
          qosStatus: 'UNAVAILABLE',
          statusInfo: 'DELETE_REQUESTED'
        }
      })
    }).catch(err => console.error(`[CAMARA] Notification failed: ${err.message}`));
  }

  res.status(204).send();
});

// =============================================================================
// GET /network-context - Device Network Context (POC Helper)
// =============================================================================
// Bu endpoint CAMARA standardında yok, POC için eklendi
// =============================================================================
app.get('/network-context', (req, res) => {
  const activeSessions = Array.from(sessions.values()).filter(s => s.qosStatus === 'AVAILABLE');

  res.json({
    currentBandwidth: deviceState.currentBandwidth,
    currentPlan: deviceState.currentPlan,
    currentPrice: deviceState.currentPrice,
    city: deviceState.city,
    country: deviceState.country,
    networkType: deviceState.networkType,
    roaming: deviceState.roaming,
    signalStrength: deviceState.signalStrength,
    activeSessions: activeSessions.length
  });
});

// =============================================================================
// GET /health - Health Check
// =============================================================================
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'camara-api', version: '1.1.0' });
});

// Server başlat
app.listen(PORT, () => {
  console.log(`[CAMARA] ✓ API running on http://localhost:${PORT}`);
  console.log(`[CAMARA]   Spec: CAMARA QoD v1.1.0 (Mock)`);
  console.log(`[CAMARA]   Profiles: ${Object.keys(QOS_PROFILES).join(', ')}`);
});
