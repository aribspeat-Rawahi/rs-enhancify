package com.aribs.rsenhancify.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.aribs.rsenhancify.ui.components.GlassColors

enum class InputKind { IMAGE, PDF }

/** Every tool in the app. Order = order on the Home screen and in the share chooser. */
enum class Tool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val input: InputKind,
    /** Max files the tool accepts from a share (Int.MAX_VALUE = many). */
    val maxShared: Int,
    /** Min files needed from a share (e.g. merging needs 2). */
    val minShared: Int = 1
) {
    ENHANCE("AI Enhance", "Upscale 2×/4× and sharpen", Icons.Rounded.AutoAwesome,
        listOf(GlassColors.Violet, GlassColors.Pink), InputKind.IMAGE, maxShared = 1),
    COMPRESS("Compress", "Reduce KB, resize, to JPG", Icons.Rounded.Compress,
        listOf(GlassColors.Mint, GlassColors.Cyan), InputKind.IMAGE, maxShared = Int.MAX_VALUE),
    IMAGE_TO_TEXT("Image to Text", "Copy text from photos", Icons.Rounded.TextFields,
        listOf(GlassColors.Amber, GlassColors.Pink), InputKind.IMAGE, maxShared = Int.MAX_VALUE),
    IMAGE_TO_PDF("JPG to PDF", "Photos into one PDF", Icons.Rounded.PictureAsPdf,
        listOf(GlassColors.Pink, GlassColors.Amber), InputKind.IMAGE, maxShared = Int.MAX_VALUE),
    PDF_TO_IMAGE("PDF to JPG", "Pages as images", Icons.Rounded.Image,
        listOf(GlassColors.Cyan, GlassColors.Mint), InputKind.PDF, maxShared = 1),
    PDF_TO_WORD("PDF to Word", "Editable .docx + OCR", Icons.Rounded.Description,
        listOf(Color(0xFF3B82F6), GlassColors.Cyan), InputKind.PDF, maxShared = 1),
    PDF_TOOLS("PDF Tools", "Merge, split, organize", Icons.Rounded.Tune,
        listOf(GlassColors.Violet, Color(0xFF3B82F6)), InputKind.PDF, maxShared = Int.MAX_VALUE);

    fun accepts(kind: InputKind, count: Int) = input == kind && count in minShared..maxShared
}
