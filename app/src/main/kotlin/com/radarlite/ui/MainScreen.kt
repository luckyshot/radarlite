package com.radarlite.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radarlite.Countries
import com.radarlite.SpeedAnnouncements
import com.radarlite.db.AlertLogEntry
import com.radarlite.ui.theme.AlertColorAverageSpeed
import com.radarlite.ui.theme.AlertColorCalming
import com.radarlite.ui.theme.AlertColorCrossing
import com.radarlite.ui.theme.AlertColorCurve
import com.radarlite.ui.theme.AlertColorJunction
import com.radarlite.ui.theme.AlertColorOverspeed
import com.radarlite.ui.theme.AlertColorRedLight
import com.radarlite.ui.theme.AlertColorSpeed
import com.radarlite.ui.theme.Amber
import com.radarlite.ui.theme.Coral
import com.radarlite.ui.theme.Sky
import com.radarlite.ui.theme.StatusActive
import com.radarlite.ui.theme.StatusIdle
import com.radarlite.ui.theme.StatusStopped
import com.radarlite.ui.theme.Surface
import com.radarlite.ui.theme.SurfaceHigh
import com.radarlite.ui.theme.SurfaceRaised
import com.radarlite.ui.theme.TextMuted
import com.radarlite.ui.theme.TextPrimary
import com.radarlite.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainScreenActions(
    val onToggleService: (Boolean) -> Unit,
    val onCountry1Selected: (String) -> Unit,
    val onCountry2Selected: (String) -> Unit,
    val onSpeedToggle: (Int, Boolean) -> Unit,
    val onAlertToggle: (String, Boolean) -> Unit,
    val onOverspeedToggle: (Boolean) -> Unit,
    val onCheckUpdate: () -> Unit,
    val onTestSound: (String, Int?, Boolean) -> Unit,
    val onTestUrgent: () -> Unit,
    val onLastFixClick: () -> Unit,
    val onAlertEntryClick: (AlertLogEntry) -> Unit,
    val onPrivacyClick: () -> Unit,
    val onSourceClick: () -> Unit,
    val onOsmClick: () -> Unit,
    val onActivateGps: (Int) -> Unit,
    val onDeactivateGps: () -> Unit,
)

@Composable
fun MainScreen(state: MainUiState, actions: MainScreenActions) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBarTop + 20.dp,
                bottom = navBarBottom + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TopBar() }
            item { DriveHeroCard(state, actions.onToggleService) }
            item {
                ActiveGpsSection(
                    remainingMs = state.activeGpsRemainingMs,
                    onActivate = actions.onActivateGps,
                    onDeactivate = actions.onDeactivateGps,
                )
            }
            item { GlanceRow(state) }
            item { SectionBanner("SET UP WHEN PARKED") }
            item {
                SpeedAnnouncementsCard(
                    selected = state.selectedSpeeds,
                    onToggle = actions.onSpeedToggle,
                )
            }
            item {
                AlertTogglesCard(
                    toggles = state.alertToggles,
                    overspeedEnabled = state.overspeedEnabled,
                    onAlertToggle = actions.onAlertToggle,
                    onOverspeedToggle = actions.onOverspeedToggle,
                )
            }
            item { LocationCard(state, actions.onLastFixClick) }
            item {
                DatabaseCard(
                    state = state,
                    onCountry1Selected = actions.onCountry1Selected,
                    onCountry2Selected = actions.onCountry2Selected,
                    onCheckUpdate = actions.onCheckUpdate,
                )
            }
            item {
                RecentAlertsCard(
                    entries = state.recentAlerts,
                    onClick = actions.onAlertEntryClick,
                )
            }
            item {
                SoundTestCard(
                    onTestSound = actions.onTestSound,
                    onTestUrgent = actions.onTestUrgent,
                )
            }
            item {
                GuideCard(
                    onPrivacyClick = actions.onPrivacyClick,
                    onSourceClick = actions.onSourceClick,
                    onOsmClick = actions.onOsmClick,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

@Composable
private fun TopBar() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Coral, Amber))),
            contentAlignment = Alignment.Center,
        ) {
            Text("📡", fontSize = 20.sp)
        }
        Text(
            text = "RadarLite",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        Pill(text = "ROAD ALERTS", background = SurfaceRaised, textColor = TextSecondary)
    }
}

@Composable
private fun Pill(text: String, background: Color, textColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

@Composable
private fun SectionBanner(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Coral),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Coral,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Drive hero card — the one thing that matters before you set off
// ---------------------------------------------------------------------------------------------

@Composable
private fun DriveHeroCard(state: MainUiState, onToggleService: (Boolean) -> Unit) {
    val running = state.isRunning
    val receiving = state.isReceivingLocation
    val statusColor = when {
        !running -> StatusStopped
        receiving -> StatusActive
        else -> StatusIdle
    }
    val statusLabel = when {
        !running -> "Stopped"
        receiving -> "Engaged"
        else -> "Idle · battery saving"
    }
    val gradient = when {
        !running -> listOf(SurfaceRaised, SurfaceHigh)
        receiving -> listOf(Color(0xFF12352A), Color(0xFF184A38))
        else -> listOf(Color(0xFF2A2410), Color(0xFF3A3216))
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(gradient), MaterialTheme.shapes.extraLarge),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MONITORING", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = if (receiving) pulse else 1f)),
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                DriveSwitch(checked = state.serviceEnabled, onCheckedChange = onToggleService)
            }
            Text(
                text = "Start before you set off. Alerts run in the background while you navigate.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 14.dp),
            )
            Row(modifier = Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DriveMetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    iconColor = Sky,
                    label = "SPEED",
                    value = if (state.speedKmh > 0) "${state.speedKmh.toInt()} km/h" else "—",
                )
                DriveMetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.LocationOn,
                    iconColor = Coral,
                    label = "NEXT ALERT",
                    value = formatDistance(state.closestCameraDistanceM),
                )
            }
        }
    }
}

@Composable
private fun DriveSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // An oversized, glove-friendly power button reads better at a glance than a small switch.
    val bg = if (checked) Brush.linearGradient(listOf(Coral, Amber)) else Brush.linearGradient(listOf(SurfaceHigh, SurfaceHigh))
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = "Turn monitoring on or off",
            tint = if (checked) Color(0xFF1A1520) else TextSecondary,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun DriveMetricTile(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Self-powered GPS — lets RadarLite work without another app driving GPS
// ---------------------------------------------------------------------------------------------

private data class ActiveGpsOption(val minutes: Int, val label: String)

private val ACTIVE_GPS_OPTIONS = listOf(
    ActiveGpsOption(30, "Activate 30 min"),
    ActiveGpsOption(60, "Activate 1h"),
    ActiveGpsOption(120, "Activate 2h"),
)

@Composable
private fun ActiveGpsSection(remainingMs: Long?, onActivate: (Int) -> Unit, onDeactivate: () -> Unit) {
    if (remainingMs != null) {
        val minutesLeft = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
        Button(
            onClick = onDeactivate,
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color(0xFF1A1520)),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                "Active GPS ($minutesLeft min left). Click to stop.",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ACTIVE_GPS_OPTIONS.forEach { option ->
                Button(
                    onClick = { onActivate(option.minutes) },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceRaised, contentColor = TextPrimary),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(option.label, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Glance row
// ---------------------------------------------------------------------------------------------

@Composable
private fun GlanceRow(state: MainUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Surface)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        GlanceItem(emoji = "🎯", label = "AHEAD", value = state.camerasNearby.toString(), hint = "alerts nearby")
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(SurfaceHigh),
        )
        GlanceItem(emoji = "🧭", label = "HEADING", value = state.heading, hint = "your direction")
    }
}

@Composable
private fun GlanceItem(emoji: String, label: String, value: String, hint: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = TextMuted, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// Generic screen card shell
// ---------------------------------------------------------------------------------------------

@Composable
private fun ScreenCard(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
            content()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Speed announcements
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedAnnouncementsCard(selected: Set<Int>, onToggle: (Int, Boolean) -> Unit) {
    ScreenCard(title = "SPEED ANNOUNCEMENTS", hint = "Choose speeds to hear as you accelerate.") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SpeedAnnouncements.speeds.toList()) { speed ->
                val checked = speed in selected
                FilterChip(
                    selected = checked,
                    onClick = { onToggle(speed, !checked) },
                    label = { Text("$speed") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SurfaceRaised,
                        labelColor = TextSecondary,
                        selectedContainerColor = Amber,
                        selectedLabelColor = Color(0xFF1A1520),
                    ),
                    border = null,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Alert toggles
// ---------------------------------------------------------------------------------------------

private fun colorFor(key: String): Color = when (key) {
    "speed" -> AlertColorSpeed
    "red_light" -> AlertColorRedLight
    "average_speed" -> AlertColorAverageSpeed
    "sharp_curve" -> AlertColorCurve
    "dangerous_junction" -> AlertColorJunction
    "level_crossing" -> AlertColorCrossing
    "traffic_calming" -> AlertColorCalming
    else -> AlertColorOverspeed
}

@Composable
private fun AlertTogglesCard(
    toggles: Map<String, Boolean>,
    overspeedEnabled: Boolean,
    onAlertToggle: (String, Boolean) -> Unit,
    onOverspeedToggle: (Boolean) -> Unit,
) {
    ScreenCard(title = "ALERTS TO HEAR", hint = "Leave on only the warnings useful for your route.") {
        ALERT_TYPES.forEach { info ->
            AlertToggleRow(
                emoji = info.emoji,
                color = colorFor(info.key),
                label = info.label,
                checked = toggles[info.key] ?: true,
                onCheckedChange = { onAlertToggle(info.key, it) },
            )
        }
        AlertToggleRow(
            emoji = "🚀",
            color = AlertColorOverspeed,
            label = "Over a camera limit",
            checked = overspeedEnabled,
            onCheckedChange = onOverspeedToggle,
        )
    }
}

@Composable
private fun AlertToggleRow(emoji: String, color: Color, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 16.sp)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = Color(0xFF1A1520)),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Location
// ---------------------------------------------------------------------------------------------

@Composable
private fun LocationCard(state: MainUiState, onLastFixClick: () -> Unit) {
    ScreenCard(title = "LOCATION") {
        InfoRow("GPS", state.gpsMode)
        InfoRow("Accuracy", state.accuracyM?.let { "${it.toInt()} m" } ?: "—")
        InfoRow(
            label = "Last fix",
            value = formatCoordinates(state.lastLat, state.lastLon),
            valueColor = if (state.lastLat != null) Coral else TextPrimary,
            onClick = if (state.lastLat != null) onLastFixClick else null,
        )
        InfoRow("Fix time", formatFixTime(state.lastFixMs))
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = TextPrimary, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

// ---------------------------------------------------------------------------------------------
// Alert database
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatabaseCard(
    state: MainUiState,
    onCountry1Selected: (String) -> Unit,
    onCountry2Selected: (String) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    ScreenCard(title = "ALERT DATABASE", hint = "Up to two countries can be active at once.") {
        CountryPickerRow(label = "Country 1", selectedCode = state.country1Code, allowNone = false, onSelected = onCountry1Selected)
        CountryPickerRow(label = "Country 2", selectedCode = state.country2Code, allowNone = true, onSelected = onCountry2Selected)
        InfoRow("Version", state.dbVersion)
        InfoRow("Points", if (state.dbCameraCount > 0) "%,d".format(state.dbCameraCount) else "No database")
        InfoRow("Last check", formatLastCheck(state.lastDbCheckMs))
        Button(
            onClick = onCheckUpdate,
            enabled = !state.updatingDb,
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color(0xFF1A1520)),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(52.dp),
        ) {
            if (state.updatingDb) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF1A1520), strokeWidth = 2.dp)
                Text("Checking…", modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
            } else {
                Text("Check for update", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerRow(label: String, selectedCode: String, allowNone: Boolean, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = if (selectedCode.isEmpty()) Countries.NONE_LABEL else Countries.nameFor(selectedCode)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(SurfaceRaised)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (allowNone) {
                    DropdownMenuItem(text = { Text(Countries.NONE_LABEL) }, onClick = { expanded = false; onSelected("") })
                }
                Countries.ALL.forEach { (name, code) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelected(code) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Recent alerts
// ---------------------------------------------------------------------------------------------

@Composable
private fun RecentAlertsCard(entries: List<AlertLogEntry>, onClick: (AlertLogEntry) -> Unit) {
    ScreenCard(title = "RECENT ALERTS") {
        if (entries.isEmpty()) {
            Text(
                "No alerts yet",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
            )
        } else {
            entries.take(8).forEach { entry ->
                val clickable = entry.cameraLat != null && entry.cameraLon != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (clickable) it.clickable { onClick(entry) } else it }
                        .padding(vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(colorFor(entry.cameraType).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emojiFor(entry.cameraType), fontSize = 14.sp)
                    }
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)) {
                        Text(alertLabel(entry.cameraType), style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Text(
                            listOfNotNull(
                                entry.speedLimit?.let { "Limit $it" },
                                entry.speedKmh?.let { "Speed ${it.toInt()}" },
                            ).joinToString(" · ").ifEmpty { "—" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                    Text(formatAlertTime(entry.timestamp), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}

private fun emojiFor(type: String): String = ALERT_TYPES.firstOrNull { it.key == type }?.emoji ?: "📷"

private fun alertLabel(type: String): String = when (type) {
    "red_light" -> "Red light"
    "average_speed" -> "Average speed zone"
    "sharp_curve" -> "Sharp curve"
    "dangerous_junction" -> "Dangerous junction"
    "level_crossing" -> "Level crossing"
    "traffic_calming" -> "Traffic calming"
    else -> "Speed limit"
}

// ---------------------------------------------------------------------------------------------
// Sound test
// ---------------------------------------------------------------------------------------------

@Composable
private fun SoundTestCard(onTestSound: (String, Int?, Boolean) -> Unit, onTestUrgent: () -> Unit) {
    ScreenCard(title = "SOUND TEST") {
        val row1 = listOf(
            Triple("📷 Camera", AlertColorSpeed) { onTestSound("speed", 50, false) },
            Triple("🚀 Overspeed", AlertColorOverspeed) { onTestSound("speed", 50, true) },
        )
        val row2 = listOf(
            Triple("➡️ Curve", AlertColorCurve) { onTestSound("sharp_curve", null, false) },
            Triple("✖️ Junction", AlertColorJunction) { onTestSound("dangerous_junction", null, false) },
        )
        val row3 = listOf(
            Triple("🚂 Crossing", AlertColorCrossing) { onTestSound("level_crossing", null, false) },
            Triple("⚠️ Urgent", AlertColorRedLight) { onTestUrgent() },
        )
        listOf(row1, row2, row3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                row.forEach { (label, color, onClick) ->
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceRaised, contentColor = TextPrimary),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(label, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Guide + about
// ---------------------------------------------------------------------------------------------

@Composable
private fun GuideCard(onPrivacyClick: () -> Unit, onSourceClick: () -> Unit, onOsmClick: () -> Unit) {
    ScreenCard(title = "QUICK GUIDE") {
        Text(
            "RadarLite normally activates only when your GPS is already being used by a navigation app like Waze or Google Maps, which keeps it very battery efficient. If you're not running one, use Activate above to have RadarLite request GPS on its own for a set time.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
        Text(
            "A first warning is one short tone followed by a short phrase, such as \"Speed limit 50\", \"Sharp curve ahead\", or \"Level crossing ahead\". A closer urgent warning is a short higher-pitched tone.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("ABOUT", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Text("Privacy policy", style = MaterialTheme.typography.bodyLarge, color = Coral, modifier = Modifier
            .clickable(onClick = onPrivacyClick)
            .padding(vertical = 6.dp))
        Text("Source code", style = MaterialTheme.typography.bodyLarge, color = Coral, modifier = Modifier
            .clickable(onClick = onSourceClick)
            .padding(vertical = 6.dp))
        Text("Alert data: OpenStreetMap", style = MaterialTheme.typography.bodyLarge, color = Coral, modifier = Modifier
            .clickable(onClick = onOsmClick)
            .padding(vertical = 6.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------------------------

private fun formatDistance(distanceM: Float?): String {
    if (distanceM == null) return "—"
    return if (distanceM < 1000f) "${distanceM.toInt()} m" else "%.1f km".format(distanceM / 1000f)
}

private fun formatCoordinates(lat: Double?, lon: Double?): String =
    if (lat == null || lon == null) "—" else "%.5f, %.5f".format(lat, lon)

private fun formatFixTime(ms: Long?): String =
    ms?.let { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "—"

private fun formatLastCheck(ms: Long): String =
    if (ms > 0) SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(ms)) else "Never"

private fun formatAlertTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ts))
    }
}
