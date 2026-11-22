package com.aribs.rsenhancify.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aribs.rsenhancify.BuildConfig
import com.aribs.rsenhancify.R
import com.aribs.rsenhancify.ui.components.GlassCard
import com.aribs.rsenhancify.ui.components.GlassColors
import com.aribs.rsenhancify.ui.components.SectionLabel
import com.aribs.rsenhancify.ui.components.glass

@Composable
fun HomeScreen(onOpen: (Tool) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(Modifier.size(60.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Rs Enhancify", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("AI photo & PDF studio", color = GlassColors.TextSecondary, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(18.dp))

        SectionLabel("Photo tools")
        ToolGrid(Tool.entries.filter { it.input == InputKind.IMAGE }, onOpen)
        Spacer(Modifier.height(10.dp))
        SectionLabel("PDF tools")
        ToolGrid(Tool.entries.filter { it.input == InputKind.PDF }, onOpen)

        Spacer(Modifier.height(14.dp))
        Text(
            "Tip: share photos or PDFs from any app to Rs Enhancify.",
            color = GlassColors.TextMuted, fontSize = 12.sp
        )
        Text(
            "Saved to Pictures/Rs Enhancify and Download/Rs Enhancify · v${BuildConfig.VERSION_NAME}",
            color = GlassColors.TextMuted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun ToolGrid(tools: List<Tool>, onOpen: (Tool) -> Unit) {
    tools.chunked(2).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            row.forEach { tool -> ToolCard(tool, Modifier.weight(1f)) { onOpen(tool) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToolCard(tool: Tool, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.heightIn(min = 132.dp), onClick = onClick, padding = 14.dp) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(tool.colors)),
            contentAlignment = Alignment.Center
        ) {
            Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(tool.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(
            tool.subtitle, color = GlassColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

/** The launcher icon, drawn in-app (gradient tile + vector foreground). */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
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
