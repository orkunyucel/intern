import 'package:flutter/material.dart';

class AppColors {
  AppColors._();

  // App background
  static const Color scaffold = Color(0xFF0A0A0A);

  // Phone card
  static const Color phoneBase = Color(0xFFF0F0FA);
  static const Color phoneBorder = Color(0xFFD0D0E8);
  static const Color notch = Color(0xFFB0B4D4);
  static const Color accentBlue = Color(0xFF227DB6);     // #227DB6
  static const Color accentOrange = Color(0xFF922786);    // #922786 (magenta)

  // Inputs & result
  static const Color inputFill = Color(0xD9FFFFFF);
  static const Color resultArea = Color(0xE6FFFFFF);
  static const Color border = Color(0xE6B0B8D8);
  static const Color borderFocus = Color(0xFF922786);

  // Text
  static const Color textTitle = Color(0xFF111111);
  static const Color textBody = Color(0xFF1A1A1A);
  static const Color textPlaceholder = Color(0xFF8890B0);

  // Auth Code butonu (her zaman aktif, magenta gradient)
  static const Color btnAuthBg = Color(0xFF922786);       // #922786
  static const Color btnAuthBgDark = Color(0xFF5C2D7C);   // #5C2D7C
  static const Color btnAuthText = Color(0xFFFFFFFF);

  // Token & Verify ortak pasif stili
  static const Color btnInactiveBg = Color(0xCCFFFFFF);
  static const Color btnInactiveBorderFallback = accentBlue;
  static const Color btnInactiveBorder = Color(0xE6B0B8D8);
  static const Color btnInactiveTextFallback = accentBlue;
  static const Color btnInactiveText = Color(0xFF8890B0);

  // Token & Verify buton renkleri
  static const Color btnTokenActive = Color(0xFF342E78);  // #342E78
  static const Color btnVerifyActive = Color(0xFF227DB6);  // #227DB6

  // Subtitle text
  static const Color textSubtitle = Color(0xFF342E78);    // #342E78

  // Gradient accent varyantları (background'da kullanılır)
  static const Color gradientBlueMid = Color(0xFF3A6B9E);
  static const Color gradientOrangeDark = Color(0xFF7A2580);
  static const Color gradientOrangeLight = Color(0xFFB030A0);
  static const Color gradientMid = Color(0xFFD0D0E6);
}
