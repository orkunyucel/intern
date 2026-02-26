import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

class AppTextStyles {
  AppTextStyles._();

  static TextStyle get pageTitle => GoogleFonts.syne(
    fontSize: 22,
    fontWeight: FontWeight.w800,
    color: AppColors.textTitle,
    letterSpacing: -0.5,
    height: 1.2,
  );

  static TextStyle get resultBody => GoogleFonts.outfit(
    fontSize: 13,
    fontWeight: FontWeight.w700,
    color: AppColors.textBody,
    height: 1.2,
  );

  static TextStyle get resultPlaceholder => GoogleFonts.outfit(
    fontSize: 11,
    fontWeight: FontWeight.w700,
    color: AppColors.textPlaceholder,
    letterSpacing: 1.5,
    height: 1.2,
  );

  static TextStyle get inputText => GoogleFonts.outfit(
    fontSize: 13,
    fontWeight: FontWeight.w400,
    color: AppColors.textTitle,
  );

  static TextStyle get inputHint => GoogleFonts.outfit(
    fontSize: 13,
    fontWeight: FontWeight.w400,
    color: AppColors.textPlaceholder,
  );

  static TextStyle get buttonLabel => GoogleFonts.outfit(
    fontSize: 13,
    fontWeight: FontWeight.w700,
    letterSpacing: 0.2,
  );
}
