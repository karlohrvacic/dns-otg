package com.controldmanager.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.controldmanager.app.MainActivity
import com.controldmanager.app.R
import com.controldmanager.app.api.ControlDApi
import com.controldmanager.app.api.TokenStore
import com.controldmanager.app.ui.CdButton
import com.controldmanager.app.ui.CdChip
import com.controldmanager.app.ui.CdTheme
import com.controldmanager.app.ui.GroupRow
import com.controldmanager.app.ui.Palette
import com.controldmanager.app.ui.PickerDialog
import com.controldmanager.app.ui.SectionHeader
import com.controldmanager.app.ui.Solar
import com.controldmanager.app.ui.compact
import kotlinx.coroutines.launch

/**
 * A widget's settings, with a live preview over the home screen wallpaper (the window shows the wallpaper
 * through, so no permission is needed). Opens when a widget is added (Android 11 and older) and from the
 * widget's pencil (long-press → edit) on Android 12 and newer.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out without saving leaves a newly added widget unplaced, as Android expects.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        enableEdgeToEdge()
        val manager = AppWidgetManager.getInstance(this)
        // The preview takes the widget's shape: wide when it is the activity widget or has been stretched wide.
        val wide = manager.getAppWidgetOptions(widgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) >= 250 ||
            manager.getAppWidgetInfo(widgetId)?.provider?.className?.endsWith("WideReceiver") == true
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(widgetId)
        lifecycleScope.launch {
            val state = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            val saved = state.widgetConfig()
            val shown = parseStats(state[WidgetKeys.data])
            setContent {
                CdTheme {
                    ConfigScreen(saved, shown, wide, signedIn = TokenStore.load(this@WidgetConfigActivity) != null) { cfg ->
                        lifecycleScope.launch {
                            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { p ->
                                p[WidgetKeys.metric] = cfg.metric
                                p[WidgetKeys.range] = cfg.range
                                p[WidgetKeys.profileId] = cfg.profileId.orEmpty()
                                p[WidgetKeys.profileName] = cfg.profileName.orEmpty()
                                p[WidgetKeys.endpointId] = cfg.endpointId.orEmpty()
                                p[WidgetKeys.endpointName] = cfg.endpointName.orEmpty()
                                p[WidgetKeys.colours] = cfg.colours
                                p[WidgetKeys.opacity] = cfg.opacity
                                p[WidgetKeys.configVersion] = System.currentTimeMillis()
                                p.remove(WidgetKeys.data); p.remove(WidgetKeys.error); p.remove(WidgetKeys.updated)
                            }
                            StatsWidget().update(this@WidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                            finish()
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ConfigScreen(initial: WidgetConfig, shown: WidgetStats?, wide: Boolean, signedIn: Boolean, onSave: (WidgetConfig) -> Unit) {
        var cfg by remember { mutableStateOf(initial) }
        var pick by remember { mutableStateOf<String?>(null) }
        val api = remember { TokenStore.load(this)?.let { (t, org) -> ControlDApi(t, org) } }
        var profiles by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
        var endpoints by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
        LaunchedEffect(api) {
            if (api == null) return@LaunchedEffect
            profiles = runCatching { api.profiles().map { it.pk to it.name } }.getOrDefault(emptyList())
            endpoints = runCatching { api.devices().map { it.pk to it.name } }.getOrDefault(emptyList())
        }
        // The preview shows the real numbers for the chosen period and traffic (sample ones until they arrive).
        var stats by remember { mutableStateOf(shown) }
        LaunchedEffect(cfg.range, cfg.profileId, cfg.endpointId) {
            if (signedIn) runCatching { loadStats(this@WidgetConfigActivity, cfg) }.onSuccess { stats = it }
        }
        val dark = widgetDark(this, cfg.colours) ?: isSystemInDarkTheme()

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // Top: the wallpaper shows through, with the widget on it.
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("Widget settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Palette.Teal)
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                        WidgetPreview(cfg, stats ?: SampleStats, wide, WidgetTheme(dark))
                    }
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Palette.Bg)
                        .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                ) {
                    if (!signedIn) {
                        Spacer(Modifier.height(16.dp))
                        Text("Sign in to DNS OTG first, then come back to set up this widget.", color = Palette.Muted)
                        Spacer(Modifier.height(16.dp))
                        CdButton("Open DNS OTG", { startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java)); finish() }, Modifier.fillMaxWidth())
                        return@Column
                    }
                    SectionHeader("Show")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetMetrics.forEach { (key, label) ->
                            CdChip(selected = cfg.metric == key, onClick = { cfg = cfg.copy(metric = key) }, label = { Text(label) })
                        }
                    }
                    SectionHeader("Time range")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetRanges.forEach { (key, _) ->
                            CdChip(selected = cfg.range == key, onClick = { cfg = cfg.copy(range = key) }, label = { Text(rangeName(key)) })
                        }
                    }
                    SectionHeader("Traffic from")
                    GroupRow(
                        "Profile", first = true, last = false, subtitle = cfg.profileName ?: "All profiles", fill = Palette.Card,
                        icon = { Icon(Solar.Tuning, null, tint = Palette.Teal) },
                        trailing = { Icon(Solar.ChevronRight, null, tint = Palette.Muted) },
                    ) { pick = "profile" }
                    GroupRow(
                        "Endpoint", first = false, last = true, subtitle = cfg.endpointName ?: "All endpoints", fill = Palette.Card,
                        icon = { Icon(Solar.Devices, null, tint = Palette.Teal) },
                        trailing = { Icon(Solar.ChevronRight, null, tint = Palette.Muted) },
                    ) { pick = "endpoint" }
                    SectionHeader("Look")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Card).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Background opacity", color = Palette.Text, modifier = Modifier.weight(1f))
                            Text("${cfg.opacity}%", color = Palette.Muted)
                        }
                        Slider(
                            value = cfg.opacity.toFloat(), onValueChange = { cfg = cfg.copy(opacity = (it / 5).toInt() * 5) }, valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = Palette.Teal, activeTrackColor = Palette.Teal, inactiveTrackColor = Palette.Outline),
                        )
                        Text("Colours", color = Palette.Text)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("app" to "Match app", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                                CdChip(selected = cfg.colours == key, onClick = { cfg = cfg.copy(colours = key) }, label = { Text(label) })
                            }
                        }
                    }
                    // Room for the floating Cancel / Save bar.
                    Spacer(Modifier.height(110.dp))
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(50)).background(Palette.CardHigh).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { finish() }) { Text("Cancel", color = Palette.Text, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
                Box(Modifier.width(1.dp).height(22.dp).background(Palette.Outline))
                TextButton(onClick = { onSave(cfg) }, enabled = signedIn) {
                    Text("Save", color = Palette.Teal, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
        }
        when (pick) {
            "profile" -> PickerDialog(
                "Profile", listOf<Pair<String, String>?>(null) + profiles.orEmpty(), label = { it?.second ?: "All profiles" },
                selected = { it?.first == cfg.profileId }, onDismiss = { pick = null },
            ) { cfg = cfg.copy(profileId = it?.first, profileName = it?.second) }
            "endpoint" -> PickerDialog(
                "Endpoint", listOf<Pair<String, String>?>(null) + endpoints.orEmpty(), label = { it?.second ?: "All endpoints" },
                selected = { it?.first == cfg.endpointId }, onDismiss = { pick = null },
            ) { cfg = cfg.copy(endpointId = it?.first, endpointName = it?.second) }
        }
    }
}

private fun rangeName(key: String) = when (key) {
    "1h" -> "Hour"; "24h" -> "Day"; "7d" -> "Week"; "30d" -> "Month"; "1y" -> "Year"; else -> key
}

/** Numbers for the preview until the real ones load. */
private val SampleStats = WidgetStats(
    206, 838, 6, 1050,
    listOf(8, 5, 9, 3, 1, 7, 2, 10, 4, 6, 3, 9, 5, 2, 8).map { it.toLong() },
    listOf(20, 12, 30, 6, 10, 26, 4, 34, 12, 18, 9, 40, 8, 30, 2).map { it.toLong() },
    listOf(0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0).map { it.toLong() },
)

/** The widget drawn as it will look (same layout, colours and numbers as on the home screen). */
@Composable
private fun WidgetPreview(cfg: WidgetConfig, s: WidgetStats, wide: Boolean, t: WidgetTheme) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        (if (wide) Modifier.fillMaxWidth().widthIn(max = 380.dp).height(170.dp) else Modifier.size(170.dp))
            .clip(shape).background(t.bg.copy(alpha = cfg.opacity / 100f)).padding(12.dp),
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(if (wide) Modifier.width(118.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.app_logo), null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(cfg.endpointName ?: cfg.profileName ?: "DNS OTG", color = t.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(t.pill).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text(cfg.range, color = t.muted, fontSize = 11.sp)
                    }
                    if (!wide) { Spacer(Modifier.width(6.dp)); Icon(painterResource(R.drawable.ic_widget_refresh), null, Modifier.size(15.dp), tint = t.muted) }
                }
                Spacer(Modifier.weight(1f))
                val (big, label) = headline(cfg, s)
                Text(big, color = t.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1, lineHeight = 38.sp)
                Text(label, color = t.muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cfg.metric == "total") {
                        Text(compact(s.blocked), color = t.red, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(" blocked", color = t.muted, fontSize = 11.sp)
                    } else {
                        val color = when (cfg.metric) { "bypassed" -> t.green; "redirected" -> t.orange; else -> t.red }
                        Text(compact(metricCount(cfg, s)), color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        Text(compact(s.total), color = t.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(" total", color = t.muted, fontSize = 11.sp)
                    }
                }
            }
            if (wide) {
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Activity", color = t.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(painterResource(R.drawable.ic_widget_refresh), null, Modifier.size(15.dp), tint = t.muted)
                    }
                    PreviewChart(s, Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf(s.blocked to t.red, s.bypassed to t.green, s.redirected to t.orange).forEachIndexed { i, (n, c) ->
                            if (i > 0) Spacer(Modifier.width(8.dp))
                            Text("●", color = c, fontSize = 9.sp)
                            Text(" ${compact(n)}", color = t.text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewChart(s: WidgetStats, modifier: Modifier) {
    Canvas(modifier) {
        val top = maxOf(1L, (s.seriesBlocked + s.seriesBypassed + s.seriesRedirected).maxOrNull() ?: 1L).toFloat()
        val stroke = 2.dp.toPx()
        fun line(values: List<Long>, color: Color) {
            if (values.size < 2) return
            val path = Path()
            values.forEachIndexed { i, v ->
                val p = Offset(stroke + i * (size.width - 2 * stroke) / (values.size - 1), size.height - stroke - v / top * (size.height - 2 * stroke))
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
        }
        line(s.seriesRedirected, RedirectedColor)
        line(s.seriesBlocked, BlockedColor)
        line(s.seriesBypassed, BypassedColor)
    }
}
