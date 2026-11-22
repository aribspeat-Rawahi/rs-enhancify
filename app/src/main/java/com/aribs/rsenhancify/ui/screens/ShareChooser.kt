package com.aribs.rsenhancify.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aribs.rsenhancify.ui.components.GlassColors
import com.aribs.rsenhancify.ui.components.GlassOutlineButton
import com.aribs.rsenhancify.ui.components.glass

/** Files that another app shared with (or opened in) Rs Enhancify. */
data class SharedFiles(val uris: List<Uri>, val kind: InputKind)

/** "What do you want to do with these files?" — shown when files are shared to the app. */
@Composable
fun ShareChooser(shared: SharedFiles, onPick: (Tool) -> Unit, onDismiss: () -> Unit) {
    val n = shared.uris.size
    val tools = Tool.entries.filter { it.accepts(shared.kind, n) }
    val what = when (shared.kind) {
        InputKind.IMAGE -> if (n == 1) "1 photo" else "$n photos"
        InputKind.PDF -> if (n == 1) "1 PDF" else "$n PDFs"
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF21E1650), Color(0xF20E1A3A))))
                .glass(RoundedCornerShape(28.dp), strong = true)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Open with Rs Enhancify", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("What do you want to do with $what?", color = GlassColors.TextSecondary, fontSize = 14.sp)
            tools.forEach { tool ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(16.dp))
                        .clickable { onPick(tool) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(tool.colors)),
                        contentAlignment = Alignment.Center
                    ) { Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val title = if (tool == Tool.PDF_TOOLS && n >= 2) "Merge PDFs" else tool.title
                        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(tool.subtitle, color = GlassColors.TextMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = GlassColors.TextMuted)
                }
            }
            GlassOutlineButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}
