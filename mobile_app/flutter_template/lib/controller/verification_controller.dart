import 'dart:convert';
import 'dart:io';
import '../config/verification_config.dart';

// ── Response model'leri ──

class Step1RawResponse {
  final int statusCode;
  final String? responseContentType;
  final String body;

  const Step1RawResponse({
    required this.statusCode,
    required this.responseContentType,
    required this.body,
  });
}

class Step2AuthCodeResponse {
  final int statusCode;
  final String? responseContentType;
  final String? redirectUri;
  final String? code;
  final String? state;
  final bool phoneNumberIncluded;

  const Step2AuthCodeResponse({
    required this.statusCode,
    required this.responseContentType,
    required this.redirectUri,
    required this.code,
    required this.state,
    required this.phoneNumberIncluded,
  });

  bool get isSuccess => statusCode == 302 && code?.isNotEmpty == true;
}

class Step3RawResponse {
  final int statusCode;
  final String? responseContentType;
  final String body;

  const Step3RawResponse({
    required this.statusCode,
    required this.responseContentType,
    required this.body,
  });
}

class Step4TokenResponse {
  final int statusCode;
  final String? accessToken;
  final String? tokenType;
  final int? expiresIn;
  final String? refreshToken;
  final String? scope;

  const Step4TokenResponse({
    required this.statusCode,
    required this.accessToken,
    required this.tokenType,
    required this.expiresIn,
    required this.refreshToken,
    required this.scope,
  });

  bool get isSuccess => statusCode == 200 && accessToken?.isNotEmpty == true;
}

class Step5RawResponse {
  final int statusCode;
  final String? responseContentType;
  final String body;

  const Step5RawResponse({
    required this.statusCode,
    required this.responseContentType,
    required this.body,
  });
}

/// OAuth2 tabanlı numara doğrulama akışını yöneten controller.
///
/// Akış 6 adımdan oluşur:
///   Step 1-2: Authorize → auth code al
///   Step 3-4: Token → access token al
///   Step 5-6: Verify → numara doğrula
///
/// Her step'in çıktısı bir sonrakine girdi olur.
class VerificationController {

  // Log buffer — UI'daki result box'a yansıtılır
  final StringBuffer _logBuffer = StringBuffer();
  String get logs => _logBuffer.toString();
  void clearLogs() => _logBuffer.clear();
  void _log(String message) => _logBuffer.writeln(message);

  // Auth base URL (UI'dan girilir)
  String _baseUrl = '';
  String get baseUrl => _baseUrl;
  set baseUrl(String url) {
    if (!url.startsWith('http')) url = 'https://$url';
    _baseUrl = url.endsWith('/') ? url.substring(0, url.length - 1) : url;
  }

  // Verify base URL (UI'dan girilir, token ve verify için kullanılır)
  String _verifyBaseUrl = '';
  set verifyBaseUrl(String url) {
    if (!url.startsWith('http')) url = 'https://$url';
    _verifyBaseUrl = url.endsWith('/') ? url.substring(0, url.length - 1) : url;
  }

  // Endpoint URL'leri (base + config path)
  String get authUrl => '$_baseUrl${VerificationConfig.authorizePath}';
  String get tokenUrl => '$_verifyBaseUrl${VerificationConfig.tokenPath}';
  String get verifyUrl => '$_verifyBaseUrl${VerificationConfig.verifyPath}';

  /// SSL doğrulamasını atlayan HttpClient 
  HttpClient _createHttpClient() {
    final client = HttpClient();
    client.connectionTimeout = const Duration(seconds: 15);
    client.badCertificateCallback = (_, __, ___) => true;
    return client;
  }

  // Adımlar arası aktarılan state
  String? _authCode;
  Step3RawResponse? _step3RawResponse;
  String? _accessToken;
  Step5RawResponse? _step5RawResponse;

  String? get authCode => _authCode;
  String? get accessToken => _accessToken;

  // ── STEP 1-2: Authorization Code ──

  /// Step 1 ve 2'yi sırayla çalıştırır, başarılıysa auth code'u saklar.
  Future<Step2AuthCodeResponse> runStep12() async {
    final step1 = await step1RequestAuthCode();
    final step2 = step2ParseAuthCodeResponse(step1);
    _authCode = step2.isSuccess ? step2.code : null;
    return step2;
  }

  /// Authorize endpoint'e GET isteği. Redirect'i elle yakalar (followRedirects: false).
  Future<Step1RawResponse> step1RequestAuthCode() async {
    if (_baseUrl.trim().isEmpty) {
      _log('[Step1] ERROR: Base URL boş');
      throw StateError('Step 1 URL boş. Base URL alanını doldurun.');
    }

    final HttpClient client = _createHttpClient();
    try {
      final Uri uri = Uri.parse(authUrl).replace(
        queryParameters: <String, String>{
          'response_type': VerificationConfig.responseType,
          'client_id': VerificationConfig.clientId,
          'redirect_uri': VerificationConfig.redirectUri,
        },
      );

      _log('[Step1] REQUEST');
      _log('  Method: GET');
      _log('  URL: $uri');
      _log('  followRedirects: false');

      final HttpClientRequest request = await client.openUrl('GET', uri);
      request.followRedirects = false;

      final HttpClientResponse response = await request.close().timeout(const Duration(seconds: 15));
      final String responseBody = await utf8.decoder.bind(response).join().timeout(const Duration(seconds: 15));

      _log('[Step1] RESPONSE');
      _log('  Status: ${response.statusCode}');
      _log('  Location: ${response.headers.value("location")}');
      _log('  ContentType: ${response.headers.contentType?.mimeType}');
      _log('  Body: $responseBody');

      return Step1RawResponse(
        statusCode: response.statusCode,
        responseContentType: response.headers.contentType?.mimeType,
        body: responseBody,
      );
    } finally {
      client.close(force: true);
    }
  }

  /// Response body'yi query string olarak parse eder, code/state/redirect_uri çıkarır.
  Step2AuthCodeResponse step2ParseAuthCodeResponse(Step1RawResponse raw) {
    Map<String, String> parsed = <String, String>{};
    try {
      parsed = Uri.splitQueryString(raw.body);
    } catch (_) {
      parsed = <String, String>{};
    }

    _log('[Step2] PARSE');
    _log('  code: ${parsed['code']}');
    _log('  state: ${parsed['state']}');
    _log('  redirect_uri: ${parsed['redirect_uri']}');
    _log('  phone_number_included: ${parsed['phone_number_included']}');

    final result = Step2AuthCodeResponse(
      statusCode: raw.statusCode,
      responseContentType: raw.responseContentType,
      redirectUri: parsed['redirect_uri'],
      code: parsed['code'],
      state: parsed['state'],
      phoneNumberIncluded: parsed['phone_number_included'] == 'true',
    );

    if (!result.isSuccess) {
      final errorMsg = _parseErrorBody(raw.body);
      _log('[Step2] FAILED: status:${raw.statusCode} | $errorMsg');
      throw StateError('Step 2 başarısız. status:${raw.statusCode} | Detay: $errorMsg');
    }

    _log('[Step2] SUCCESS');
    return result;
  }

  // ── STEP 3-4: Access Token ──

  /// Token endpoint'e POST. Basic Auth header + JSON body (grant_type, code, scope).
  Future<Step3RawResponse> step3RequestToken() async {
    if (tokenUrl.trim().isEmpty) {
      _log('[Step3] ERROR: URL boş');
      throw StateError('Step 3 URL boş. step3Url alanını doldurun.');
    }
    if (_authCode?.isNotEmpty != true) {
      _log('[Step3] ERROR: auth code yok');
      throw StateError('Step 3 için auth code yok. Önce Step 1-2 çalıştırın.');
    }

    final HttpClient client = _createHttpClient();
    try {
      final Uri uri = Uri.parse(tokenUrl);

      _log('[Step3] REQUEST');
      _log('  Method: POST');
      _log('  URL: $uri');
      _log('  Auth: Basic (clientId:clientSecret)');
      _log('  ContentType: ${VerificationConfig.step3RequestContentType.isEmpty ? "(not set)" : VerificationConfig.step3RequestContentType}');
      _log('  Body: {grant_type:authorization_code, code:$_authCode, scope:read-write}');

      final HttpClientRequest request = await client.openUrl('POST', uri);

      request.headers.set(
        HttpHeaders.authorizationHeader,
        _buildBasicAuthHeader(),
      );

      if (VerificationConfig.step3RequestContentType.isNotEmpty) {
        request.headers.set(
          HttpHeaders.contentTypeHeader,
          VerificationConfig.step3RequestContentType,
        );
      }

      request.write(
        jsonEncode(<String, String>{
          'grant_type': 'authorization_code',
          'code': _authCode!,
          'scope': 'read-write',
        }),
      );

      final HttpClientResponse response = await request.close().timeout(const Duration(seconds: 15));
      final String responseBody = await utf8.decoder.bind(response).join().timeout(const Duration(seconds: 15));

      final raw = Step3RawResponse(
        statusCode: response.statusCode,
        responseContentType: response.headers.contentType?.mimeType,
        body: responseBody,
      );
      _step3RawResponse = raw;

      _log('[Step3] RESPONSE');
      _log('  Status: ${raw.statusCode}');
      _log('  ContentType: ${raw.responseContentType}');
      _log('  Body: ${raw.body}');

      return raw;
    } finally {
      client.close(force: true);
    }
  }

  /// Step 3 response'unu parse eder, access_token'ı saklar.
  Future<Step4TokenResponse> step4ReadAccessTokenResponse() async {
    final raw = _step3RawResponse;
    if (raw == null) {
      _log('[Step4] ERROR: Step 3 response bulunamadı');
      throw StateError('Step 4 için Step 3 response bulunamadı.');
    }

    _log('[Step4] PARSE');
    Map<String, dynamic> parsed;
    try {
      parsed = _parseJsonMap(raw.body);
    } catch (e) {
      _log('[Step4] ERROR: Response JSON parse edilemedi: $e');
      rethrow;
    }

    final token = Step4TokenResponse(
      statusCode: raw.statusCode,
      accessToken: _asString(parsed['access_token']),
      tokenType: _asString(parsed['token_type']),
      expiresIn: _asInt(parsed['expires_in']),
      refreshToken: _asString(parsed['refresh_token']),
      scope: _asString(parsed['scope']),
    );

    _log('  Status: ${token.statusCode}');
    _log('  accessToken: ${token.accessToken != null ? "${token.accessToken!.substring(0, token.accessToken!.length > 20 ? 20 : token.accessToken!.length)}..." : "null"}');
    _log('  tokenType: ${token.tokenType}');
    _log('  expiresIn: ${token.expiresIn}');
    _log('  refreshToken: ${token.refreshToken != null ? "ok" : "null"}');
    _log('  scope: ${token.scope}');

    if (!token.isSuccess) {
      final errorMsg = _parseErrorBody(raw.body);
      _log('[Step4] FAILED: $errorMsg');
      throw StateError('Step 4 başarısız. status:${token.statusCode} | Detay: $errorMsg');
    }

    _accessToken = token.accessToken;
    _log('[Step4] SUCCESS');
    return token;
  }

  // ── STEP 5-6: Number Verification ──

  /// Verify endpoint'e POST. Bearer token + JSON body (phoneNumber: +905XXXXXXXXX).
  Future<Step5RawResponse> step5VerifyWithNumToken({
    required String phoneNumber,
  }) async {
    if (verifyUrl.trim().isEmpty) {
      _log('[Step5] ERROR: URL boş');
      throw StateError('Step 5 URL boş. step5Url alanını doldurun.');
    }
    if (_accessToken?.isNotEmpty != true) {
      _log('[Step5] ERROR: access token yok');
      throw StateError('Step 5 için access token yok. Önce Step 3-4 çalıştırın.');
    }
    if (phoneNumber.trim().isEmpty) {
      _log('[Step5] ERROR: phoneNumber boş');
      throw StateError('Step 5 için phoneNumber boş olamaz.');
    }
    if (!RegExp(r'^\+905\d{9}$').hasMatch(phoneNumber.trim())) {
      _log('[Step5] ERROR: phoneNumber format hatalı: $phoneNumber');
      throw StateError('Step 5 phoneNumber formatı +905XXXXXXXXX olmalı.');
    }

    final HttpClient client = _createHttpClient();
    try {
      final Uri uri = Uri.parse(verifyUrl);

      _log('[Step5] REQUEST');
      _log('  Method: POST');
      _log('  URL: $uri');
      _log('  Auth: Bearer token');
      _log('  ContentType: ${VerificationConfig.step5RequestContentType.isEmpty ? "(not set)" : VerificationConfig.step5RequestContentType}');
      _log('  Body: {phoneNumber: ${phoneNumber.trim()}}');

      final HttpClientRequest request = await client.openUrl('POST', uri);

      request.headers.set(
        HttpHeaders.authorizationHeader,
        'Bearer ${_accessToken!}',
      );

      if (VerificationConfig.step5RequestContentType.isNotEmpty) {
        request.headers.set(
          HttpHeaders.contentTypeHeader,
          VerificationConfig.step5RequestContentType,
        );
      }

      request.write(
        jsonEncode(<String, String>{
          'phoneNumber': phoneNumber.trim(),
        }),
      );

      final HttpClientResponse response = await request.close().timeout(const Duration(seconds: 15));
      final String responseBody = await utf8.decoder.bind(response).join().timeout(const Duration(seconds: 15));

      final raw = Step5RawResponse(
        statusCode: response.statusCode,
        responseContentType: response.headers.contentType?.mimeType,
        body: responseBody,
      );
      _step5RawResponse = raw;

      _log('[Step5] RESPONSE');
      _log('  Status: ${raw.statusCode}');
      _log('  ContentType: ${raw.responseContentType}');
      _log('  Body: ${raw.body}');

      return raw;
    } finally {
      client.close(force: true);
    }
  }

  /// Step 5 response'unu parse eder. { "devicePhoneNumberVerified": true } → doğrulandı.
  Future<bool> step6ReadVerificationResult() async {
    final raw = _step5RawResponse;
    if (raw == null) {
      _log('[Step6] ERROR: Step 5 response bulunamadı');
      throw StateError('Step 6 için Step 5 response bulunamadı.');
    }

    if (raw.statusCode != 200) {
      final errorMsg = _parseErrorBody(raw.body);
      _log('[Step6] FAILED: status:${raw.statusCode} | $errorMsg');
      throw StateError('Step 6 başarısız. status:${raw.statusCode} | Detay: $errorMsg');
    }

    _log('[Step6] PARSE');
    Map<String, dynamic> parsed;
    try {
      parsed = _parseJsonMap(raw.body);
    } catch (e) {
      _log('[Step6] ERROR: Response JSON parse edilemedi: $e');
      rethrow;
    }
    final bool verified = parsed['devicePhoneNumberVerified'] == true;

    _log('  Status: ${raw.statusCode}');
    _log('  devicePhoneNumberVerified: $verified');
    _log('[Step6] ${verified ? "SUCCESS" : "FAILED"}');

    return verified;
  }

  // ── Yardımcı fonksiyonlar ──

  /// "Basic Base64(clientId:clientSecret)" formatında auth header üretir.
  String _buildBasicAuthHeader() {
    final encoded = base64Encode(
      utf8.encode('${VerificationConfig.clientId}:${VerificationConfig.clientSecret}'),
    );
    return 'Basic $encoded';
  }

  /// JSON string → Map. Geçerli JSON object değilse FormatException fırlatır.
  Map<String, dynamic> _parseJsonMap(String rawBody) {
    final decoded = jsonDecode(rawBody);
    if (decoded is Map<String, dynamic>) return decoded;
    if (decoded is Map) {
      return decoded.map((key, value) => MapEntry(key.toString(), value));
    }
    throw const FormatException('Response body JSON object değil.');
  }

  /// Error response'tan okunabilir mesaj çıkarır (JSON veya form-urlencoded).
  /// Bulamazsa ham body'yi döndürür.
  String _parseErrorBody(String rawBody) {
    if (rawBody.trim().isEmpty) return 'Boş Response Body';
    try {
      final parsed = _parseJsonMap(rawBody);

      // OAuth formatı: { "error": "...", "error_description": "..." }
      final error = parsed['error'];
      final errorDesc = parsed['error_description'];
      if (error != null || errorDesc != null) {
        return [
          if (error != null) 'Hata Türü: $error',
          if (errorDesc != null) 'Açıklama: $errorDesc',
        ].join(' - ');
      }

      // API hata formatı: { "status": 401, "code": "UNAUTHENTICATED", "message": "..." }
      final code = parsed['code'];
      final message = parsed['message'];
      if (code != null || message != null) {
        return [
          if (code != null) 'Kod: $code',
          if (message != null) 'Mesaj: $message',
        ].join(' - ');
      }
    } catch (_) {
      // JSON değilse form-urlencoded dene
      try {
        final params = Uri.splitQueryString(rawBody);
        final error = params['error'];
        final errorDesc = params['error_description'];
        if (error != null || errorDesc != null) {
          return [
            if (error != null) 'Hata Türü: $error',
            if (errorDesc != null) 'Açıklama: $errorDesc',
          ].join(' - ');
        }
      } catch (_) {}
    }
    return rawBody.length > 200
        ? '${rawBody.substring(0, 200)}... (kısaltıldı)'
        : rawBody;
  }

  /// dynamic → String (null-safe).
  String? _asString(dynamic value) {
    if (value == null) return null;
    if (value is String) return value;
    return value.toString();
  }

  /// dynamic → int (null-safe).
  int? _asInt(dynamic value) {
    if (value == null) return null;
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.tryParse(value);
    return null;
  }
}