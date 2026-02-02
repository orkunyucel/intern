/**
 
 * CAMARA QoD (Quality on Demand) API - Mock Implementation

 * Bu dosya CAMARA standardına uygun bir QoD API simülasyonudur.
 * Bu mock versiyon, POC için async callback pattern'i simüle eder.
 *
 * FLOW'daki Rolü:
 * - ⑨ MCP Server'dan API call alır
 * - ⑩ 201 Created döner (hemen)
 * - ⑫ 3 saniye sonra /notify callback'i MCP Server'a gönderir
 *
 * Port: 5002
 * ============================================================================
 */

const express = require('express');  // Web framework
const cors = require('cors');        // Cross-Origin Resource Sharing (UI'dan erişim için)
const { v4: uuidv4 } = require('uuid'); // Unique session ID üretimi

const app = express();
app.use(cors());           // Tüm origin'lerden gelen isteklere izin ver
app.use(express.json());   // JSON body parsing

const PORT = 5002;

// -----------------------------------------------------------------------------
// IN-MEMORY SESSION STORAGE
// Gerçek sistemde bu bir veritabanı olurdu (PostgreSQL, Redis vb.)
// POC için basit bir Map kullanıyoruz
// -----------------------------------------------------------------------------
const sessions = new Map();

// -----------------------------------------------------------------------------
// QoS PROFILE DEFINITIONS
// CAMARA standardına göre QoS profilleri ve karşılık gelen bandwidth değerleri
// LLM, user'ın "700 Mbps istiyorum" demesini QOS_L'e map eder
// -----------------------------------------------------------------------------
const QOS_PROFILES = {
  QOS_S: { bandwidth: '50 Mbps', latency: '100ms', price: 100 },   // Small - Temel kullanım - 100 TL
  QOS_M: { bandwidth: '200 Mbps', latency: '50ms', price: 200 },   // Medium - HD streaming - 200 TL
  QOS_L: { bandwidth: '700 Mbps', latency: '20ms', price: 300 },   // Large - 4K streaming - 300 TL
  QOS_E: { bandwidth: '1500 Mbps', latency: '10ms', price: 400 }   // Extra Large - Gaming/Pro - 400 TL
};

// -----------------------------------------------------------------------------
// CENTRALIZED DEVICE STATE
// Tek bir state objesi - tüm bandwidth değişiklikleri buradan yönetilir
// Bu POC için fake cihaz durumu - gerçek sistemde network'ten gelir
// -----------------------------------------------------------------------------
const DEFAULT_BANDWIDTH = '200 Mbps';
const DEFAULT_PLAN = 'QOS_M';  // Kullanıcının mevcut planı

const deviceState = {
  currentBandwidth: DEFAULT_BANDWIDTH,
  currentPlan: DEFAULT_PLAN,        // Kullanıcının mevcut QoS planı
  currentPrice: QOS_PROFILES[DEFAULT_PLAN].price,  // Mevcut plan ücreti
  city: 'İstanbul',
  country: 'Türkiye',
  networkType: '5G',
  roaming: false,
  signalStrength: 'Excellent'
};

/**
 * ============================================================================
 * POST /sessions - QoS Session Oluştur
 * ============================================================================
 *
 * FLOW Adımları:
 * Request Body:
 * {
 *   "qosProfile": "QOS_L",           // Zorunlu: QoS profili
 *   "duration": 3600,                 // Opsiyonel: Session süresi (saniye)
 *   "notificationUrl": "http://..."   // Callback URL (MCP Server'ın /notify endpoint'i)
 * }
 *
 * Response (201 Created):
 * {
 *   "sessionId": "uuid",
 *   "qosStatus": "REQUESTED",
 *   "qosProfile": "QOS_L",
 *   "bandwidth": "700 Mbps",
 *   "latency": "20ms"
 * }
 */
app.post('/sessions', (req, res) => {
  // Request body'den parametreleri al
  const { qosProfile, duration = 3600, notificationUrl } = req.body;

  // QoS profili validasyonu
  // Geçersiz profil gönderilirse 400 Bad Request döner
  if (!qosProfile || !QOS_PROFILES[qosProfile]) {
    return res.status(400).json({
      error: 'Invalid qosProfile',
      validProfiles: Object.keys(QOS_PROFILES)
    });
  }

  // Unique session ID üret (UUID v4 formatında)
  const sessionId = uuidv4();

  // Session objesi oluştur
  const session = {
    sessionId,
    qosProfile,
    qosStatus: 'REQUESTED',  // Başlangıç durumu: İstek alındı
    duration,
    startedAt: new Date().toISOString(),
    notificationUrl          // Callback için sakla
  };

  // Session'ı memory'de sakla
  sessions.set(sessionId, session);

  // Console'a log bas (debug için)
  console.log(`[CAMARA] Session created: ${sessionId}, Profile: ${qosProfile}`);

  // ⑩ HEMEN 201 Created döner (async pattern)
  // Gerçek CAMARA API'si de böyle çalışır - işlem arka planda devam eder
  res.status(201).json({
    sessionId,
    qosStatus: 'REQUESTED',
    qosProfile,
    ...QOS_PROFILES[qosProfile]  // bandwidth ve latency bilgilerini ekle
  });

  // ⑫ ASYNC NETWORK OPERATION SİMÜLASYONU
  // 3 saniye sonra callback gönder (gerçek sistemde network provisioning süresi)
  setTimeout(async () => {
    // Session durumunu güncelle
    session.qosStatus = 'AVAILABLE';  // Artık kullanıma hazır
    sessions.set(sessionId, session);

    // ▼▼▼ FİYAT FARKI HESAPLAMASI ▼▼▼
    const oldPrice = deviceState.currentPrice;
    const newPrice = QOS_PROFILES[qosProfile].price;
    const priceDifference = newPrice - oldPrice;  // Pozitif = upgrade, negatif = downgrade
    // ▲▲▲ FİYAT FARKI HESAPLAMASI ▲▲▲

    // ▼▼▼ MERKEZI STATE DEĞİŞİKLİĞİ ▼▼▼
    deviceState.currentBandwidth = QOS_PROFILES[qosProfile].bandwidth;
    deviceState.currentPlan = qosProfile;
    deviceState.currentPrice = newPrice;
    console.log(`[CAMARA] Bandwidth changed: ${deviceState.currentBandwidth}`);
    console.log(`[CAMARA] Price difference: ${priceDifference} TL (${oldPrice} TL -> ${newPrice} TL)`);
    // ▲▲▲ MERKEZI STATE DEĞİŞİKLİĞİ ▲▲▲

    console.log(`[CAMARA] Session ${sessionId} is now AVAILABLE`);

    // MCP Server'a callback notification gönder
    // Bu, MCP Server'ın /notify/:taskId endpoint'ine POST yapar
    if (notificationUrl) {
      try {
        await fetch(notificationUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            sessionId,
            qosStatus: 'AVAILABLE',
            qosProfile,
            ...QOS_PROFILES[qosProfile],
            // Fiyat bilgileri
            oldPrice,
            newPrice,
            priceDifference,
            priceText: priceDifference > 0
              ? `${priceDifference} TL ek ücret`
              : priceDifference < 0
                ? `${Math.abs(priceDifference)} TL tasarruf`
                : 'Ücret değişikliği yok'
          })
        });
        console.log(`[CAMARA] Notification sent to ${notificationUrl}`);
      } catch (err) {
        // Callback gönderilemezse sadece log bas, işlem devam eder
        console.error(`[CAMARA] Failed to send notification: ${err.message}`);
      }
    }
  }, 3000);  // 3000ms = 3 saniye
});

/**
 * GET /sessions/:sessionId - Session Durumunu Sorgula
 *
 * Belirli bir session'ın mevcut durumunu döner.
 * get_qos_status tool'u bu endpoint'i çağırır.
 */
app.get('/sessions/:sessionId', (req, res) => {
  const { sessionId } = req.params;
  const session = sessions.get(sessionId);

  // Session bulunamazsa 404 döner
  if (!session) {
    return res.status(404).json({ error: 'Session not found' });
  }

  res.json(session);
});

/**

 * DELETE /sessions/:sessionId - Session'ı Sonlandır
 * Aktif bir QoS session'ı sonlandırır.
 * Kullanıcı "session'ı kapat" dediğinde LLM bu endpoint'i çağırır.
 */
app.delete('/sessions/:sessionId', (req, res) => {
  const { sessionId } = req.params;

  // Session yoksa 404 döner
  if (!sessions.has(sessionId)) {
    return res.status(404).json({ error: 'Session not found' });
  }

  // Session'ı sil
  sessions.delete(sessionId);

  // ▼▼▼ MERKEZI BANDWIDTH SIFIRLAMA ▼▼▼
  deviceState.currentBandwidth = DEFAULT_BANDWIDTH;
  console.log(`[CAMARA] Bandwidth reset to default: ${DEFAULT_BANDWIDTH}`);
  // ▲▲▲ MERKEZI BANDWIDTH SIFIRLAMA ▲▲▲

  console.log(`[CAMARA] Session deleted: ${sessionId}`);

  // 204 No Content - başarılı silme, body yok
  res.status(204).send();
});

/**
 * ============================================================================
 * GET /network-context - Device Network Context
 * ============================================================================
 *
 * Cihazın mevcut ağ durumunu ve konum bilgisini döner.
 * "Bandwidth değerim nedir?" gibi sorular için kullanılır.
 */
app.get('/network-context', (req, res) => {
  // Aktif session var mı kontrol et
  const activeSessions = Array.from(sessions.values()).filter(s => s.qosStatus === 'AVAILABLE');
  const hasActiveSession = activeSessions.length > 0;
  let activeSessionInfo = null;

  if (hasActiveSession) {
    const activeSession = activeSessions[0];
    activeSessionInfo = {
      sessionId: activeSession.sessionId,
      qosProfile: activeSession.qosProfile,
      createdAt: activeSession.createdAt
    };
  }

  // Merkezi deviceState'ten al 
  const networkContext = {
    currentBandwidth: deviceState.currentBandwidth,  // ← Merkezi state'ten
    currentPlan: deviceState.currentPlan,            // ← Mevcut QoS planı
    currentPrice: deviceState.currentPrice,          // ← Mevcut plan ücreti (TL)
    city: deviceState.city,
    country: deviceState.country,
    networkType: deviceState.networkType,
    roaming: deviceState.roaming,
    signalStrength: deviceState.signalStrength,
    activeSession: hasActiveSession,
    activeSessionInfo
  };

  console.log(`[CAMARA] Network context requested:`, networkContext);
  res.json(networkContext);
});

/**
 * ============================================================================
 * GET /health - Health Check Endpoint
 * ============================================================================
 *
 * Servisin ayakta olup olmadığını kontrol etmek için.
 * Load balancer'lar ve monitoring sistemleri bu endpoint'i kullanır.
 */
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'camara-api' });
});

// -----------------------------------------------------------------------------
// SERVER BAŞLAT
// -----------------------------------------------------------------------------
app.listen(PORT, () => {
  console.log(`[CAMARA] API running on http://localhost:${PORT}`);
  console.log(`[CAMARA] Available QoS Profiles: ${Object.keys(QOS_PROFILES).join(', ')}`);
});
