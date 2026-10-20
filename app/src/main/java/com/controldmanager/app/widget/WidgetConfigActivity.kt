package com.controldmanager.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.controldmanager.app.MainActivity
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
import kotlinx.coroutines.launch

/**
 * A widget's settings: what it shows, for which period, and for which profile or endpoint. Opens when a widget
 * is added (Android 11 and older) and from the widget's pencil (long-press → edit) on Android 12 and newer.
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
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(widgetId)
        lifecycleScope.launch {
            val saved = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId).widgetConfig()
            setContent {
                CdTheme {
                    ConfigScreen(saved, signedIn = TokenStore.load(this@WidgetConfigActivity) != null) { cfg ->
                        lifecycleScope.launch {
                            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { p ->
                                p[WidgetKeys.metric] = cfg.metric
                                p[WidgetKeys.range] = cfg.range
                                p[WidgetKeys.profileId] = cfg.profileId.orEmpty()
                                p[WidgetKeys.profileName] = cfg.profileName.orEmpty()
                                p[WidgetKeys.endpointId] = cfg.endpointId.orEmpty()
                                p[WidgetKeys.endpointName] = cfg.endpointName.orEmpty()
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
    private fun ConfigScreen(initial: WidgetConfig, signedIn: Boolean, onSave: (WidgetConfig) -> Unit) {
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
        Column(
            Modifier.fillMaxSize().background(Palette.Bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text("Widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            if (!signedIn) {
                Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(24.dp))
            CdButton("Save", { onSave(cfg) }, Modifier.fillMaxWidth(), icon = Solar.Check)
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
