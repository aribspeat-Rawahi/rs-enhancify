package com.aribs.rsenhancify.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.aribs.rsenhancify.ui.components.GlassCard
import com.aribs.rsenhancify.ui.components.GlassColors
import com.aribs.rsenhancify.ui.components.GlassTopBar

/** Standard scrollable tool screen with glass top bar. */
@Composable
fun ToolScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title, subtitle, onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun GlassSwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 15.sp)
                if (subtitle != null) Text(subtitle, color = GlassColors.TextMuted, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GlassColors.Violet,
                    uncheckedThumbColor = Color(0xCCFFFFFF),
                    uncheckedTrackColor = Color(0x33FFFFFF),
                    uncheckedBorderColor = Color(0x55FFFFFF)
                )
            )
        }
    }
}

fun formatEta(seconds: Int?): String = when {
    seconds == null -> ""
    seconds < 60 -> "· ~${seconds}s left"
    else -> "· ~${seconds / 60}m ${seconds % 60}s left"
}
