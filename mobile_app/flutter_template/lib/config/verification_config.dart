/// Verification akışının sabit konfigürasyon değerleri.
/// Credential'lar, endpoint path'leri ve content-type ayarları burada toplanır.
class VerificationConfig {
  VerificationConfig._();

  // ── ENDPOINT PATH'LERİ (baseUrl'e eklenir)
  static const String authorizePath = '/authorize';
  static const String tokenPath = '/oauth2/token';
  static const String verifyPath = '/vwip/verify';

  // ── AUTHORIZE İSTEĞİ QUERY PARAMETRELERİ
  // Authorize isteğinde URL'ye eklenen query parametreleri.
  static const String responseType = 'code';
  static const String clientId     = '';
  static const String redirectUri  = '';

  // ── TOKEN İSTEĞİ (Step 3)
  // Token isteğinin content-type'ı.
  static const String step3RequestContentType = 'application/json';

  // Basic auth için client secret.
  static const String clientSecret = '';

  // ── VERIFY İSTEĞİ (Step 5)
  // Verify isteğinin content-type'ı.
  static const String step5RequestContentType = 'application/json';
}
