import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import '../controller/verification_controller.dart';
import '../theme/app_colors.dart';
import '../theme/app_text_styles.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final VerificationController _verificationController =
  VerificationController();
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _baseUrlController = TextEditingController(
    text: '',
  );
  final TextEditingController _verifyBaseUrlController = TextEditingController(
    text: '',
  );

  String _authScheme = 'https://';
  String _verifyScheme = 'https://';

  // Buton durumları: chain aktivasyon
  bool _isStep12Loading = false;
  bool _isStep34Active = false;   // Step 1-2 başarılıysa true
  bool _isStep34Loading = false;
  bool _isStep56Active = false;   // Step 3-4 başarılıysa true
  bool _isStep56Loading = false;
  String _resultText = '';

  @override
  void dispose() {
    _phoneController.dispose();
    _baseUrlController.dispose();
    _verifyBaseUrlController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final EdgeInsets viewPadding = MediaQuery.paddingOf(context);
    return Scaffold(
      backgroundColor: AppColors.scaffold,
      resizeToAvoidBottomInset: true,
      body: SizedBox.expand(
        child: _buildPhoneCard(
          isFullScreen: true,
          viewPadding: viewPadding,
        ),
      ),
    );
  }

  Widget _buildPhoneCard({
    required bool isFullScreen,
    required EdgeInsets viewPadding,
  }) {
    final double radius = isFullScreen ? 0 : 44;
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        border: isFullScreen
            ? null
            : Border.all(color: AppColors.phoneBorder, width: 1.5),
        boxShadow: isFullScreen
            ? null
            : [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.70),
            blurRadius: 80,
            offset: const Offset(0, 40),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: Stack(
          children: [
            const _PhoneBackground(),
            Positioned(
              top: viewPadding.top + 13,
              left: 0,
              right: 0,
              child: Center(
                child: Container(
                  width: 64,
                  height: 4,
                  decoration: BoxDecoration(
                    color: AppColors.notch.withValues(alpha: 0.4),
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
              ),
            ),
            Padding(
              padding: EdgeInsets.fromLTRB(
                20,
                viewPadding.top + 20,
                20,
                viewPadding.bottom + 24,
              ),
              child: Column(
                children: [
                  const SizedBox(height: 20),
                  _buildHeader(),
                  const Spacer(),
                  const RepaintBoundary(child: _OrbitMergeWidget()),
                  const Spacer(),
                  _buildBottomGroup(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }



  Widget _buildHeader() {
    return Column(
      children: [
        Text(
          'Number\nVerification',
          textAlign: TextAlign.center,
          style: AppTextStyles.pageTitle,
        ),
        const SizedBox(height: 8),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 24,
              height: 2,
              decoration: const BoxDecoration(
                borderRadius: BorderRadius.all(Radius.circular(4)),
                gradient: LinearGradient(
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                  colors: [Colors.transparent, AppColors.accentBlue],
                ),
              ),
            ),
            const SizedBox(width: 6),
            Container(
              width: 5,
              height: 5,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [AppColors.accentBlue, AppColors.accentOrange],
                ),
              ),
            ),
            const SizedBox(width: 6),
            Container(
              width: 24,
              height: 2,
              decoration: const BoxDecoration(
                borderRadius: BorderRadius.all(Radius.circular(4)),
                gradient: LinearGradient(
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                  colors: [AppColors.accentOrange, Colors.transparent],
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          'Numaranızı doğrulayın',
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.bold,
            color: AppColors.textSubtitle.withValues(alpha: 0.8),
            letterSpacing: 0.3,
          ),
        ),
      ],
    );
  }

  Widget _buildBottomGroup() {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Base URL text field
        _buildBaseUrlField(),
        const SizedBox(height: 6),
        // Verify Base URL (ör: https://gateway.com/nv/number-verification)
        _buildVerifyBaseUrlField(),
        const SizedBox(height: 8),
        // Telefon numarası text field (Step 5-6 aktifse aktif)
        _buildPhoneField(),
        const SizedBox(height: 10),
        // 3 buton satırı
        _buildButtonRow(),
        const SizedBox(height: 10),
        _buildResultBox(),
      ],
    );
  }

  Widget _buildSchemeDropdown({
    required String value,
    required ValueChanged<String?> onChanged,
  }) {
    return Padding(
      padding: const EdgeInsets.only(left: 14.0, right: 8.0),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          DropdownButtonHideUnderline(
            child: DropdownButton<String>(
              value: value,
              isDense: true,
              dropdownColor: AppColors.inputFill,
              icon: const Icon(Icons.arrow_drop_down, color: AppColors.accentBlue),
              style: AppTextStyles.inputText.copyWith(fontSize: 16, fontWeight: FontWeight.bold),
              onChanged: onChanged,
              items: [
                DropdownMenuItem(
                  value: 'https://',
                  child: Text(
                    'https://',
                    style: AppTextStyles.inputText.copyWith(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                ),
                DropdownMenuItem(
                  value: 'http://',
                  child: Text(
                    'http://',
                    style: AppTextStyles.inputText.copyWith(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Container(width: 1.5, height: 24, color: AppColors.border),
        ],
      ),
    );
  }

  Widget _buildBaseUrlField() {
    return TextField(
      controller: _baseUrlController,
      style: AppTextStyles.inputText.copyWith(fontSize: 16, fontWeight: FontWeight.bold),
      maxLines: 1,
      keyboardType: TextInputType.url,
      decoration: InputDecoration(
        hintText: 'gateway.com',
        hintStyle: AppTextStyles.inputHint.copyWith(fontSize: 16, fontWeight: FontWeight.bold),
        filled: true,
        fillColor: AppColors.inputFill,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        prefixIcon: _buildSchemeDropdown(
          value: _authScheme,
          onChanged: (val) {
            if (val != null) setState(() => _authScheme = val);
          },
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppColors.border, width: 1.5),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppColors.borderFocus, width: 1.5),
        ),
      ),
    );
  }

  Widget _buildVerifyBaseUrlField() {
    final bool isEnabled = (_isStep34Active && !_isStep34Loading) || (_isStep56Active && !_isStep56Loading);
    return Opacity(
      opacity: isEnabled ? 1.0 : 0.4,
      child: IgnorePointer(
        ignoring: !isEnabled,
        child: TextField(
          controller: _verifyBaseUrlController,
          style: AppTextStyles.inputText.copyWith(fontSize: 16, fontWeight: FontWeight.bold),
          maxLines: 1,
          keyboardType: TextInputType.url,
          decoration: InputDecoration(
            hintText: 'gateway.com/verify',
            hintStyle: AppTextStyles.inputHint.copyWith(fontSize: 16, fontWeight: FontWeight.bold),
            filled: true,
            fillColor: AppColors.inputFill,
            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
            prefixIcon: _buildSchemeDropdown(
              value: _verifyScheme,
              onChanged: (val) {
                if (val != null) setState(() => _verifyScheme = val);
              },
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: AppColors.border, width: 1.5),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: AppColors.borderFocus, width: 1.5),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPhoneField() {
    final bool isEnabled = _isStep56Active && !_isStep56Loading;
    return Opacity(
      opacity: isEnabled ? 1.0 : 0.4,
      child: IgnorePointer(
        ignoring: !isEnabled,
        child: TextField(
          controller: _phoneController,
          style: AppTextStyles.inputText.copyWith(fontSize: 18, fontWeight: FontWeight.bold),
          maxLines: 1,
          textAlign: TextAlign.center,
          keyboardType: TextInputType.phone,
          inputFormatters: <TextInputFormatter>[_PhoneInputFormatter()],
          decoration: InputDecoration(
            hintText: '+90 5XX XXX XX XX',
            hintStyle: AppTextStyles.inputHint.copyWith(fontSize: 16, fontWeight: FontWeight.bold),
            filled: true,
            fillColor: AppColors.inputFill,
            contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: AppColors.border, width: 1.5),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide:
              const BorderSide(color: AppColors.borderFocus, width: 1.5),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildButtonRow() {
    return Row(
      children: [
        // Buton 1: Get Auth Code (Step 1-2) — her zaman aktif
        Expanded(
          child: _PrimaryButton(
            label: _isStep12Loading ? '...' : 'Auth Code',
            onPressed: _onStep12Pressed,
          ),
        ),
        const SizedBox(width: 6),
        // Buton 2: Get Token (Step 3-4) — Step 1-2 başarılıysa aktif
        Expanded(
          child: _SecondaryButton(
            label: _isStep34Loading ? '...' : 'Token',
            isActive: _isStep34Active || _isStep34Loading,
            activeColor: AppColors.btnTokenActive,
            activeTextColor: Colors.white,
            onPressed: _onStep34Pressed,
          ),
        ),
        const SizedBox(width: 6),
        // Buton 3: Verify (Step 5-6) — Step 3-4 başarılıysa aktif
        Expanded(
          child: _SecondaryButton(
            label: _isStep56Loading ? '...' : 'Verify',
            isActive: _isStep56Active || _isStep56Loading,
            activeColor: AppColors.btnVerifyActive,
            activeTextColor: Colors.white,
            onPressed: _onStep56Pressed,
          ),
        ),
      ],
    );
  }

  Widget _buildResultBox() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      constraints: const BoxConstraints(maxHeight: 160),
      decoration: BoxDecoration(
        color: AppColors.resultArea,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: AppColors.border.withValues(alpha: 0.8),
          width: 1.5,
        ),
      ),
      child: Scrollbar(
        child: SingleChildScrollView(
          child: Text(
            _resultText.isEmpty ? 'EMPTY RESULT' : _resultText,
            style: _resultText.isEmpty
                ? AppTextStyles.resultPlaceholder.copyWith(fontSize: 13, fontWeight: FontWeight.bold)
                : AppTextStyles.resultBody.copyWith(
              fontSize: 12,
              fontWeight: FontWeight.bold,
              fontFamily: 'monospace',
            ),
            textAlign: TextAlign.left,
          ),
        ),
      ),
    );
  }

  // ── BUTON 1: Get Auth Code (Step 1-2)
  void _onStep12Pressed() async {
    if (_isStep12Loading) return;

    // Base URL'i controller'a set et
    String cleanUrl = _baseUrlController.text.trim().replaceAll(RegExp(r'^https?://'), '');
    _verificationController.baseUrl = '$_authScheme$cleanUrl';
    _verificationController.clearLogs();

    setState(() {
      _isStep12Loading = true;
      _isStep34Active = false;
      _isStep56Active = false;
      _resultText = '';
    });
    try {
      final Step2AuthCodeResponse step12 =
      await _verificationController.runStep12();
      if (!mounted) return;
      setState(() {
        _isStep12Loading = false;
        _isStep34Active = step12.isSuccess;
        _resultText = _verificationController.logs;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isStep12Loading = false;
        _isStep34Active = false;
        _resultText = '${_verificationController.logs}\nHata: $e';
      });
    }
  }

  // ── BUTON 2: Get Token (Step 3-4)
  void _onStep34Pressed() async {
    if (!_isStep34Active || _isStep34Loading) return;

    // istekten önce kutudaki adresi Controller'a haber vermeliyiz.
    String cleanVerifyUrl = _verifyBaseUrlController.text.trim().replaceAll(RegExp(r'^https?://'), '');
    _verificationController.verifyBaseUrl = '$_verifyScheme$cleanVerifyUrl';
    _verificationController.clearLogs();

    setState(() {
      _isStep34Loading = true;
      _isStep56Active = false;
      _resultText = '';
    });
    try {
      await _verificationController.step3RequestToken();
      final Step4TokenResponse token =
      await _verificationController.step4ReadAccessTokenResponse();
      if (!mounted) return;
      setState(() {
        _isStep34Loading = false;
        _isStep56Active = token.isSuccess;
        _resultText = _verificationController.logs;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isStep34Loading = false;
        _isStep56Active = false;
        _resultText = '${_verificationController.logs}\nHata: $e';
      });
    }
  }

  // ── BUTON 3: Verify (Step 5-6)
  void _onStep56Pressed() async {
    if (!_isStep56Active || _isStep56Loading) return;
    // Verify Base URL'i controller'a set et
    String cleanVerifyUrl = _verifyBaseUrlController.text.trim().replaceAll(RegExp(r'^https?://'), '');
    _verificationController.verifyBaseUrl = '$_verifyScheme$cleanVerifyUrl';
    String localNumber = _phoneController.text.replaceAll(RegExp(r'\D'), '');
    if (localNumber.startsWith('90')) localNumber = localNumber.substring(2);
    if (localNumber.length != 10 || !localNumber.startsWith('5')) {
      setState(() => _resultText = 'Telefon formatı: +905XXXXXXXXX');
      return;
    }
    _verificationController.clearLogs();
    setState(() {
      _isStep56Loading = true;
      _resultText = '';
    });
    try {
      await _verificationController.step5VerifyWithNumToken(
          phoneNumber: '+90$localNumber');
      final bool login =
      await _verificationController.step6ReadVerificationResult();
      if (!mounted) return;
      setState(() {
        _isStep56Loading = false;
        _resultText = _verificationController.logs;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isStep56Loading = false;
        _resultText = '${_verificationController.logs}\nHata: $e';
      });
    }
  }
}

// ══════════════════════════════════════════════
// ORBIT MERGE WIDGET
// Fazlar: ORBIT → MERGE → SHOW → SPLIT → ORBIT
// ══════════════════════════════════════════════

enum _Phase { orbit, merge, show, split }

class _OrbitMergeWidget extends StatefulWidget {
  const _OrbitMergeWidget();

  @override
  State<_OrbitMergeWidget> createState() => _OrbitMergeWidgetState();
}

class _OrbitMergeWidgetState extends State<_OrbitMergeWidget>
    with SingleTickerProviderStateMixin {
  static const double kStage    = 400;
  static const double kOrbitR   = 108;
  static const double kLogoSz   = 160;
  static const double kCenterSz = 160;

  static const double kOrbitMs  = 2200;
  static const double kMergeMs  = 600;
  static const double kShowMs   = 900;
  static const double kSplitMs  = 600;

  late final Ticker _ticker;
  Duration _lastElapsed = Duration.zero;

  // _absAngle HİÇ durmaz, tüm fazlarda sürekli artar
  double _absAngle = 0.0;

  _Phase _phase     = _Phase.orbit;
  bool   _showA     = true;
  double _phaseMs   = kOrbitMs;
  double _phaseElapsed = 0.0; // mevcut fazda geçen ms

  // merge/split'te lerp için 0→1 progress
  double _mergeProgress = 0.0;
  double _showProgress  = 0.0;
  double _pulseProgress = 0.0;

  @override
  void initState() {
    super.initState();
    _ticker = createTicker(_onTick)..start();
  }

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  void _onTick(Duration elapsed) {
    final double dt = _lastElapsed == Duration.zero
        ? 0
        : (elapsed - _lastElapsed).inMicroseconds / 1000.0;
    _lastElapsed = elapsed;

    setState(() {
      // _absAngle HER ZAMAN artar — faz bağımsız
      _absAngle += (2 * pi / kOrbitMs) * dt;

      _phaseElapsed += dt;
      // 0→1 arası normalize progress (clamp ile taşmaz)
      final double t = (_phaseElapsed / _phaseMs).clamp(0.0, 1.0);

      switch (_phase) {
        case _Phase.orbit:
          _mergeProgress = 0.0;
          _showProgress  = 0.0;
          if (t >= 1.0) _nextPhase();
          break;

        case _Phase.merge:
          _mergeProgress = _easeInOut(t);
          if (t >= 1.0) _nextPhase();
          break;

        case _Phase.show:
          _mergeProgress = 1.0;
          _showProgress  = t < 0.4 ? _easeOut(t / 0.4) : 1.0;
          _pulseProgress = t;
          if (t >= 1.0) _nextPhase();
          break;

        case _Phase.split:
          _mergeProgress = 1.0 - _easeInOut(t);
          _showProgress  = t < 0.3 ? 1.0 - _easeOut(t / 0.3) : 0.0;
          if (t >= 1.0) _nextPhase();
          break;
      }
    });
  }

  void _nextPhase() {
    _phaseElapsed = 0.0;
    switch (_phase) {
      case _Phase.orbit:
        _phase   = _Phase.merge;
        _phaseMs = kMergeMs;
        _showA   = !_showA; // merge BAŞINDA toggle → Stack sırası hemen düzelir
        break;
      case _Phase.merge:
        _phase   = _Phase.show;
        _phaseMs = kShowMs;
        break;
      case _Phase.show:
        _phase         = _Phase.split;
        _phaseMs       = kSplitMs;
        _pulseProgress = 0.0;
        break;
      case _Phase.split:
        _phase   = _Phase.orbit;
        _phaseMs = kOrbitMs;
        break;
    }
  }

  double _easeInOut(double t) => t < .5 ? 2*t*t : -1+(4-2*t)*t;
  double _easeOut(double t)   => 1 - pow(1 - t, 3).toDouble();

  Offset _orbitPos(double angle) => Offset(
    kStage / 2 + kOrbitR * cos(angle - pi / 2),
    kStage / 2 + kOrbitR * sin(angle - pi / 2),
  );

  // _absAngle'dan türetilmiş pozisyon — snapshot YOK
  Offset _posA() {
    final Offset orbit  = _orbitPos(_absAngle);
    final Offset center = Offset(kStage / 2, kStage / 2);
    return Offset.lerp(orbit, center, _mergeProgress)!;
  }

  Offset _posB() {
    final Offset orbit  = _orbitPos(_absAngle + pi);
    final Offset center = Offset(kStage / 2, kStage / 2);
    return Offset.lerp(orbit, center, _mergeProgress)!;
  }

  // Hangi logo üstte olacak — _showA merge başında toggle olduğu için doğru sıra
  List<Widget> _buildLogos(Offset posA, Offset posB, double opA, double opB) {
    final Widget logoA = Positioned(
      left: posA.dx - kLogoSz / 2,
      top:  posA.dy - kLogoSz / 2,
      child: Opacity(
        opacity: opA,
        child: _LogoCard(
          imagePath: 'assets/images/header_logo.png',
          glowColor: AppColors.accentBlue,
          size: kLogoSz,
        ),
      ),
    );
    final Widget logoB = Positioned(
      left: posB.dx - kLogoSz / 2,
      top:  posB.dy - kLogoSz / 2,
      child: Opacity(
        opacity: opB,
        child: _LogoCard(
          imagePath: 'assets/images/new_logo.png',
          glowColor: AppColors.accentOrange,
          size: kLogoSz,
          transparent: true,
        ),
      ),
    );
    // _showA=false → turuncu (B) gösterilecek → B üstte
    // _showA=true  → mavi (A) gösterilecek    → A üstte
    return _showA ? [logoB, logoA] : [logoA, logoB];
  }

  @override
  Widget build(BuildContext context) {

    final Offset posA = _posA();
    final Offset posB = _posB();

    final double opA;
    final double opB;

    if (_phase == _Phase.show) {
      // show sırasında iki orbit logosu da gizli
      opA = 0.0;
      opB = 0.0;
    } else if (_phase == _Phase.merge) {
      // merge sırasında: gösterilecek logo tam opak, diğeri fade out
      if (_showA) {
        opA = 1.0;
        opB = (1.0 - _mergeProgress).clamp(0.0, 1.0);
      } else {
        opA = (1.0 - _mergeProgress).clamp(0.0, 1.0);
        opB = 1.0;
      }
    } else if (_phase == _Phase.split) {
      // split sırasında: gösterilen logo tam opak, diğeri fade in
      if (_showA) {
        opA = 1.0;
        opB = (1.0 - _mergeProgress).clamp(0.0, 1.0);
      } else {
        opA = (1.0 - _mergeProgress).clamp(0.0, 1.0);
        opB = 1.0;
      }
    } else {
      // orbit: ikisi de görünür
      opA = 1.0;
      opB = 1.0;
    }

    final double showVal  = _showProgress.clamp(0.0, 1.0);
    final double pulseVal = _pulseProgress.clamp(0.0, 1.0);

    return SizedBox(
      width: kStage,
      height: kStage,
      child: Stack(
        children: [
          Positioned.fill(
            child: CustomPaint(painter: _OrbitPathPainter(kOrbitR)),
          ),

          // pulse ring — sadece show fazında
          if (_phase == _Phase.show)
            Positioned(
              left: kStage / 2 - kCenterSz / 2,
              top:  kStage / 2 - kCenterSz / 2,
              child: Opacity(
                opacity: (1 - pulseVal).clamp(0.0, 1.0),
                child: Transform.scale(
                  scale: 1 + pulseVal * 1.4,
                  child: Container(
                    width: kCenterSz,
                    height: kCenterSz,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(kCenterSz * 0.28),
                      border: Border.all(
                        color: _showA
                            ? AppColors.accentBlue
                            : AppColors.accentOrange,
                        width: 2.5,
                      ),
                    ),
                  ),
                ),
              ),
            ),

          // Logolar — sırası _showA'ya göre helper'dan geliyor
          ..._buildLogos(posA, posB, opA, opB),

          // Merkez display
          Positioned(
            left: kStage / 2 - kCenterSz / 2,
            top:  kStage / 2 - kCenterSz / 2,
            child: Opacity(
              opacity: showVal,
              child: Transform.scale(
                scale: 0.7 + showVal * 0.3,
                child: _LogoCard(
                  imagePath: _showA
                      ? 'assets/images/header_logo.png'
                      : 'assets/images/new_logo.png',
                  glowColor: _showA
                      ? AppColors.accentBlue
                      : AppColors.accentOrange,
                  size: kCenterSz,
                  transparent: !_showA,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Orbit yolu çizer
class _OrbitPathPainter extends CustomPainter {
  final double radius;
  _OrbitPathPainter(this.radius);

  @override
  void paint(Canvas canvas, Size size) {
    final Paint paint = Paint()
      ..color = Colors.white.withValues(alpha: 0.08)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1
      ..isAntiAlias = true;
    canvas.drawCircle(
      Offset(size.width / 2, size.height / 2),
      radius,
      paint,
    );
  }

  @override
  bool shouldRepaint(_OrbitPathPainter old) => false;
}

// ── Logo card widget
class _LogoCard extends StatelessWidget {
  final String imagePath;
  final Color glowColor;
  final double size;
  final bool transparent;

  // Shadow cache — her frame yeniden oluşturulmaz
  static final List<BoxShadow> _blueShadows = [
    BoxShadow(
      color: AppColors.accentBlue.withValues(alpha: 0.45),
      blurRadius: 20,
      offset: const Offset(0, 4),
    ),
    BoxShadow(
      color: AppColors.accentBlue.withValues(alpha: 0.20),
      blurRadius: 40,
      spreadRadius: 4,
    ),
  ];
  static final List<BoxShadow> _orangeShadows = [
    BoxShadow(
      color: AppColors.accentOrange.withValues(alpha: 0.45),
      blurRadius: 20,
      offset: const Offset(0, 4),
    ),
    BoxShadow(
      color: AppColors.accentOrange.withValues(alpha: 0.20),
      blurRadius: 40,
      spreadRadius: 4,
    ),
  ];

  const _LogoCard({
    required this.imagePath,
    required this.glowColor,
    required this.size,
    this.transparent = false,
  });

  @override
  Widget build(BuildContext context) {
    final double r = size * 0.28;
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(r),
        color: transparent
            ? Colors.transparent
            : Colors.white.withValues(alpha: 0.55),
        border: transparent
            ? null
            : Border.all(
          color: Colors.white.withValues(alpha: 0.70),
          width: 1.5,
        ),
        boxShadow: glowColor == AppColors.accentBlue
            ? _blueShadows
            : _orangeShadows,
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(transparent ? r : r - 1),
        child: Image.asset(
          imagePath,
          width: size,
          height: size,
          fit: transparent ? BoxFit.contain : BoxFit.cover,
        ),
      ),
    );
  }
}

// ── Phone Background (const widget — rebuild edilmez)
class _PhoneBackground extends StatelessWidget {
  const _PhoneBackground();

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Container(color: AppColors.phoneBase),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: const Alignment(-1.2, -0.6),
                radius: 0.82,
                colors: [
                  AppColors.accentBlue.withValues(alpha: 0.45),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: const Alignment(-1.1, 0.4),
                radius: 0.85,
                colors: [
                  AppColors.gradientBlueMid.withValues(alpha: 0.35),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: const Alignment(1.2, -0.8),
                radius: 0.82,
                colors: [
                  AppColors.accentOrange.withValues(alpha: 0.30),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: const Alignment(1.2, 0.3),
                radius: 0.88,
                colors: [
                  AppColors.gradientOrangeDark.withValues(alpha: 0.25),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: const Alignment(0.0, 1.1),
                radius: 0.80,
                colors: [
                  AppColors.gradientOrangeLight.withValues(alpha: 0.18),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.centerLeft,
                end: Alignment.centerRight,
                colors: [
                  AppColors.accentBlue.withValues(alpha: 0.08),
                  AppColors.gradientMid.withValues(alpha: 0.20),
                  AppColors.accentOrange.withValues(alpha: 0.07),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

// ── Primary Button
class _PrimaryButton extends StatefulWidget {
  final String label;
  final VoidCallback? onPressed;
  const _PrimaryButton({required this.label, this.onPressed});

  @override
  State<_PrimaryButton> createState() => _PrimaryButtonState();
}

class _PrimaryButtonState extends State<_PrimaryButton> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapUp: (_) {
        setState(() => _pressed = false);
        widget.onPressed?.call();
      },
      onTapCancel: () => setState(() => _pressed = false),
      child: AnimatedScale(
        scale: _pressed ? 0.96 : 1.0,
        duration: const Duration(milliseconds: 120),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          height: 46,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [AppColors.btnAuthBg, AppColors.btnAuthBgDark],
            ),
            boxShadow: _pressed
                ? []
                : [
              BoxShadow(
                color: AppColors.btnAuthBg.withValues(alpha: 0.45),
                blurRadius: 18,
                offset: const Offset(0, 4),
              ),
              BoxShadow(
                color: AppColors.btnAuthBgDark.withValues(alpha: 0.25),
                blurRadius: 6,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          alignment: Alignment.center,
          child: Text(
            widget.label,
            style: AppTextStyles.buttonLabel.copyWith(
              color: AppColors.btnAuthText,
              fontSize: 16,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      ),
    );
  }
}

// ── Secondary Button
class _SecondaryButton extends StatefulWidget {
  final String label;
  final bool isActive;
  final Color? activeColor;
  final Color? activeTextColor;
  final VoidCallback? onPressed;

  const _SecondaryButton({
    required this.label,
    required this.isActive,
    this.activeColor,
    this.activeTextColor,
    this.onPressed,
  });

  @override
  State<_SecondaryButton> createState() => _SecondaryButtonState();
}

class _SecondaryButtonState extends State<_SecondaryButton> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown:
      widget.isActive ? (_) => setState(() => _pressed = true) : null,
      onTapUp: widget.isActive
          ? (_) {
        setState(() => _pressed = false);
        widget.onPressed?.call();
      }
          : null,
      onTapCancel: () => setState(() => _pressed = false),
      child: AnimatedScale(
        scale: _pressed ? 0.96 : 1.0,
        duration: const Duration(milliseconds: 120),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 250),
          height: 46,
          decoration: BoxDecoration(
            color: widget.isActive && widget.activeColor != null
                ? widget.activeColor
                : AppColors.btnInactiveBg,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: widget.isActive
                  ? (widget.activeColor ?? AppColors.btnInactiveBorderFallback)
                  : AppColors.btnInactiveBorder,
              width: 1.5,
            ),
            boxShadow: widget.isActive && !_pressed
                ? [
              BoxShadow(
                color: (widget.activeColor ?? AppColors.accentBlue).withValues(alpha: 0.35),
                blurRadius: 14,
                offset: const Offset(0, 4),
              ),
            ]
                : [],
          ),
          alignment: Alignment.center,
          child: Text(
            widget.label,
            style: AppTextStyles.buttonLabel.copyWith(
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: widget.isActive
                  ? (widget.activeTextColor ?? AppColors.btnInactiveTextFallback)
                  : AppColors.btnInactiveText,
            ),
          ),
        ),
      ),
    );
  }
}

class _PhoneInputFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
      TextEditingValue oldValue,
      TextEditingValue newValue,
      ) {
    String raw = newValue.text;
    if (raw.startsWith('+90')) raw = raw.substring(3);
    final String digits = raw.replaceAll(RegExp(r'\D'), '');
    final String limited =
    digits.length > 10 ? digits.substring(0, 10) : digits;
    if (limited.isEmpty) {
      return const TextEditingValue(
        text: '',
        selection: TextSelection.collapsed(offset: 0),
      );
    }
    final StringBuffer buffer = StringBuffer('+90 ');
    for (int i = 0; i < limited.length; i++) {
      if (i == 3 || i == 6 || i == 8) buffer.write(' ');
      buffer.write(limited[i]);
    }
    final String formatted = buffer.toString();
    return TextEditingValue(
      text: formatted,
      selection: TextSelection.collapsed(offset: formatted.length),
    );
  }
}