package com.aribs.rsenhancify.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aribs.rsenhancify.BuildConfig
import com.aribs.rsenhancify.R
import com.aribs.rsenhancify.Screen
import androidx.compose.ui.res.painterResource
import com.aribs.rsenhancify.ui.components.GlassCard
import com.aribs.rsenhancify.ui.components.GlassColors
import com.aribs.rsenhancify.ui.components.glass

private data class Tool(
    val screen: Screen,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val colors: List<Color>
)

@Composable
fun HomeScreen(onOpen: (Screen) -> Unit, onCheckUpdates: () -> Unit) {
    val tools = listOf(
        Tool(Screen.ENHANCE, "AI Enhance", "Upscale 2x / 4x and sharpen blurry photos", Icons.Rounded.AutoAwesome,
            listOf(GlassColors.Violet, GlassColors.Pink)),
        Tool(Screen.IMAGE_TO_PDF, "JPG to PDF", "Combine photos into one PDF", Icons.Rounded.PictureAsPdf,
            listOf(GlassColors.Pink, GlassColors.Amber)),
        Tool(Screen.PDF_TO_IMAGE, "PDF to JPG", "Export pages as high-quality images", Icons.Rounded.Image,
            listOf(GlassColors.Cyan, GlassColors.Mint)),
        Tool(Screen.PDF_TO_WORD, "PDF to Word", "Editable .docx, with offline OCR for scans", Icons.Rounded.Description,
            listOf(Color(0xFF3B82F6), GlassColors.Cyan)),
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Rs Enhancify", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("AI photo & PDF studio", color = GlassColors.TextSecondary, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .glass(RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.WifiOff, null, tint = GlassColors.Mint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Works offline · files never leave your phone", color = GlassColors.TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(26.dp))

        tools.forEach { tool ->
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                onClick = { onOpen(tool.screen) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(tool.colors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(tool.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(tool.subtitle, color = GlassColors.TextSecondary, fontSize = 13.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = GlassColors.TextMuted)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .glass(RoundedCornerShape(50))
                .clickable(onClick = onCheckUpdates)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = GlassColors.Cyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Check for updates · v${BuildConfig.VERSION_NAME}", color = GlassColors.TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Saved to Pictures/Rs Enhancify and Download/Rs Enhancify",
            color = GlassColors.TextMuted, fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

/** The launcher icon, drawn in-app (gradient tile + vector foreground). */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF7A3BEE), Color(0xFF4F4FEC), Color(0xFF2B5FEB)))
            )
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Rs Enhancify logo",
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.5f, scaleY = 1.5f) // show the 72dp visible area of the 108dp adaptive icon
        )
    }
}
