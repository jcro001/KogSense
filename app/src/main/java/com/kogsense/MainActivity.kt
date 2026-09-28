package com.kogsense

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import com.kogsense.ui.theme.KogSenseTheme
import androidx.compose.ui.text.style.TextAlign // For TextAlign.Center
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.rounded.ExpandMore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KogSenseTheme {
                KogSenseScreen()
            }
        }
    }
}

@Composable
fun <T> DropdownSettingCard(
    title: String,
    icon: ImageVector,
    selectedValue: String,
    options: List<Pair<T, String>>,
    onOptionSelected: (T) -> Unit,
    subtitle: String? = null,
    isStacked: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (isStacked) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedValue,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onOptionSelected(value)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Icon + Title + Subtitle
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Right side: Dropdown value + icon
                Box(contentAlignment = Alignment.CenterEnd) {
                    Surface(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedValue,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(start = 2.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onOptionSelected(value)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KogSenseScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("kogsense_prefs", Context.MODE_PRIVATE) }

    // Load global settings for fallback
    var autoDetectedBrand by remember {
        mutableStateOf(sharedPreferences.getString("detected_brand", "None") ?: "None")
    }
    var detectedSourceName by remember {
        mutableStateOf(sharedPreferences.getString("detected_source_name", "") ?: "")
    }

    // Load all saved bike sensors
    val allBikeSensors = remember(autoDetectedBrand, detectedSourceName) {
        val prefs = sharedPreferences
        val sensors = mutableListOf<Pair<String, String>>()
        val allKeys = prefs.all.keys

        // Look for sensors with stored names (from KogSenseExtension)
        allKeys.forEach { key ->
            if (key.startsWith("sensor_name_")) {
                val sensorId = key.removePrefix("sensor_name_")
                val sensorName = prefs.getString(key, sensorId) ?: sensorId
                sensors.add(sensorId to sensorName)
            }
        }

        val lastSourceId = prefs.getString("last_source_id", null)
        val currentId = lastSourceId ?: "default"
        if (sensors.none { it.first == currentId }) {
            val currentName = if (detectedSourceName.isNotEmpty()) detectedSourceName else if (lastSourceId != null) lastSourceId else "Default Bike"
            sensors.add(currentId to currentName)
        }

        // Sort by name for consistency
        sensors.sortBy { it.second }
        sensors
    }

    // State for selected bike (default to last connected or first available)
    var selectedBike by remember(allBikeSensors) {
        val lastSourceId = sharedPreferences.getString("last_source_id", null)
        mutableStateOf(
            if (lastSourceId != null && allBikeSensors.any { it.first == lastSourceId }) {
                lastSourceId
            } else {
                allBikeSensors.firstOrNull()?.first ?: "default"
            }
        )
    }

    // Load settings for the selected bike (fallback to global if not set)
    var lowGearAlertEnabled by remember {
        mutableStateOf(
            sharedPreferences.getBoolean(
                "low_gear_alert_enabled_$selectedBike",
                sharedPreferences.getBoolean("low_gear_alert_enabled", true)
            )
        )
    }
    var highGearAlertEnabled by remember {
        mutableStateOf(
            sharedPreferences.getBoolean(
                "high_gear_alert_enabled_$selectedBike",
                sharedPreferences.getBoolean("high_gear_alert_enabled", true)
            )
        )
    }
    var synchroAlertEnabled by remember {
        mutableStateOf(
            sharedPreferences.getBoolean(
                "synchro_alert_enabled_$selectedBike",
                sharedPreferences.getBoolean("synchro_alert_enabled", true)
            )
        )
    }
    var synchroUpMode by remember {
        mutableStateOf(
            sharedPreferences.getString(
                "synchro_up_mode_$selectedBike",
                "AUTO"
            ) ?: "AUTO"
        )
    }
    var synchroDownMode by remember {
        mutableStateOf(
            sharedPreferences.getString(
                "synchro_down_mode_$selectedBike",
                "AUTO"
            ) ?: "AUTO"
        )
    }
    var synchroUpCog by remember {
        mutableIntStateOf(
            sharedPreferences.getInt(
                "synchro_up_cog_$selectedBike",
                0
            )
        )
    }
    var synchroDownCog by remember {
        mutableIntStateOf(
            sharedPreferences.getInt(
                "synchro_down_cog_$selectedBike",
                0
            )
        )
    }
    var cassetteSize by remember {
        mutableIntStateOf(
            sharedPreferences.getInt(
                "pref_cassette_size_$selectedBike",
                sharedPreferences.getInt("pref_cassette_size", 0)
            )
        )
    }
    var drivetrainBrand by remember {
        mutableStateOf(
            sharedPreferences.getString(
                "pref_drivetrain_brand_$selectedBike",
                sharedPreferences.getString("pref_drivetrain_brand", "Auto") ?: "Auto"
            ) ?: "Auto"
        )
    }

    // Debug log
    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) {
            Log.d("KogSense", "MainActivity UI started. Selected Bike: $selectedBike")
        }
    }

    // Listener for SharedPreferences changes
    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (BuildConfig.DEBUG) Log.d("KogSense", "Pref change detected: $key")
            when {
                key == "detected_brand" -> {
                    autoDetectedBrand = prefs.getString(key, "None") ?: "None"
                }
                key == "detected_source_name" -> {
                    detectedSourceName = prefs.getString(key, "") ?: ""
                }
                key == "last_source_id" -> {
                    val id = prefs.getString(key, null)
                    if (id != null && allBikeSensors.any { it.first == id }) {
                        selectedBike = id
                    }
                }
                key?.startsWith("sensor_name_") == true -> {
                    // Reload the bike list if a new sensor is added
                    // (This is a simplification; in practice, you might want to trigger a recomposition)
                }
                key?.startsWith("low_gear_alert_enabled_") == true -> {
                    if (key.removePrefix("low_gear_alert_enabled_") == selectedBike) {
                        lowGearAlertEnabled = prefs.getBoolean(key, true)
                    }
                }
                key?.startsWith("high_gear_alert_enabled_") == true -> {
                    if (key.removePrefix("high_gear_alert_enabled_") == selectedBike) {
                        highGearAlertEnabled = prefs.getBoolean(key, true)
                    }
                }
                key?.startsWith("synchro_alert_enabled_") == true -> {
                    if (key.removePrefix("synchro_alert_enabled_") == selectedBike) {
                        synchroAlertEnabled = prefs.getBoolean(key, true)
                    }
                }
                key?.startsWith("synchro_up_mode_") == true -> {
                    if (key.removePrefix("synchro_up_mode_") == selectedBike) {
                        synchroUpMode = prefs.getString(key, "AUTO") ?: "AUTO"
                    }
                }
                key?.startsWith("synchro_down_mode_") == true -> {
                    if (key.removePrefix("synchro_down_mode_") == selectedBike) {
                        synchroDownMode = prefs.getString(key, "AUTO") ?: "AUTO"
                    }
                }
                key?.startsWith("synchro_up_cog_") == true -> {
                    if (key.removePrefix("synchro_up_cog_") == selectedBike) {
                        synchroUpCog = prefs.getInt(key, 0)
                    }
                }
                key?.startsWith("synchro_down_cog_") == true -> {
                    if (key.removePrefix("synchro_down_cog_") == selectedBike) {
                        synchroDownCog = prefs.getInt(key, 0)
                    }
                }
                key?.startsWith("pref_cassette_size_") == true -> {
                    if (key.removePrefix("pref_cassette_size_") == selectedBike) {
                        cassetteSize = prefs.getInt(key, 0)
                    }
                }
                key?.startsWith("pref_drivetrain_brand_") == true -> {
                    if (key.removePrefix("pref_drivetrain_brand_") == selectedBike) {
                        drivetrainBrand = prefs.getString(key, "Auto") ?: "Auto"
                    }
                }
            }
        }
    }

    // Register the listener
    DisposableEffect(sharedPreferences) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // UI state
    val isConnected = autoDetectedBrand != "None" && autoDetectedBrand != "Unknown"
    val effectiveBrand = if (drivetrainBrand == "Auto") autoDetectedBrand else drivetrainBrand
    val synchroToggleTitle = if (effectiveBrand == "SRAM") "Sequential Shift Alert" else "Synchro Shift Alert"
    val displayStatus = if (!isConnected) {
        "Not Connected"
    } else {
        "Connected ($effectiveBrand)"
    }

    // Main UI
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "KogSense Config",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Card
            StatusCard(
                title = "Drivetrain Status",
                status = displayStatus,
                icon = if (isConnected) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.DirectionsBike,
                isActive = isConnected
            )

            // Bike Selector Dropdown
            DropdownSettingCard(
                title = "Select Bike",
                icon = Icons.AutoMirrored.Rounded.DirectionsBike,
                selectedValue = allBikeSensors.find { it.first == selectedBike }?.second ?: selectedBike,
                options = allBikeSensors,
                onOptionSelected = { bikeId ->
                    selectedBike = bikeId
                },
                isStacked = true
            )

            // Settings Header
            Text(
                text = "Bike Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Toggle Cards
            CompactToggleCard(
                title = "Low Gear Alert",
                icon = Icons.Rounded.GraphicEq,
                checked = lowGearAlertEnabled,
                onCheckedChange = { isChecked ->
                    lowGearAlertEnabled = isChecked
                    sharedPreferences.edit()
                        .putBoolean("low_gear_alert_enabled_$selectedBike", isChecked)
                        .apply()
                }
            )

            CompactToggleCard(
                title = "High Gear Alert",
                icon = Icons.Rounded.AudioFile,
                checked = highGearAlertEnabled,
                onCheckedChange = { isChecked ->
                    highGearAlertEnabled = isChecked
                    sharedPreferences.edit()
                        .putBoolean("high_gear_alert_enabled_$selectedBike", isChecked)
                        .apply()
                }
            )

            CompactToggleCard(
                title = synchroToggleTitle,
                icon = Icons.Rounded.Sync,
                checked = synchroAlertEnabled,
                onCheckedChange = { isChecked ->
                    synchroAlertEnabled = isChecked
                    sharedPreferences.edit()
                        .putBoolean("synchro_alert_enabled_$selectedBike", isChecked)
                        .putBoolean("synchro_alert_enabled", isChecked) // Keep global fallback
                        .apply()
                }
            )

            // Synchro Settings (only show if synchro alerts are enabled)
            if (synchroAlertEnabled) {
                val cogOptions = remember {
                    mutableListOf<Pair<Int, String>>(
                        0 to "Auto (Auto-Detect)",
                        -1 to "Off"
                    ).apply {
                        for (c in 1..13) {
                            add(c to "Cog $c (Manual)")
                        }
                    }
                }

                // Up-Shift Cog
                val upSelectedText = when {
                    synchroUpCog == -1 -> "Off"
                    synchroUpMode == "AUTO" && synchroUpCog > 0 -> "Auto ($synchroUpCog)"
                    synchroUpMode == "AUTO" -> "Auto"
                    else -> "Cog $synchroUpCog"
                }

                DropdownSettingCard(
                    title = "Shift Up Cog",
                    icon = Icons.Rounded.ArrowUpward,
                    selectedValue = upSelectedText,
                    options = cogOptions,
                    onOptionSelected = { cog ->
                        val sensorKey = selectedBike
                        val modeKey = "synchro_up_mode_$sensorKey"
                        val cogKey = "synchro_up_cog_$sensorKey"
                        val candKey = "synchro_up_cand_$sensorKey"
                        val countKey = "synchro_up_count_$sensorKey"

                        if (cog == 0) {
                            synchroUpMode = "AUTO"
                            synchroUpCog = 0
                            sharedPreferences.edit()
                                .putString(modeKey, "AUTO")
                                .putInt(cogKey, 0)
                                .putInt(candKey, 0)
                                .putInt(countKey, 0)
                                .apply()
                        } else {
                            synchroUpMode = "MANUAL"
                            synchroUpCog = cog
                            sharedPreferences.edit()
                                .putString(modeKey, "MANUAL")
                                .putInt(cogKey, cog)
                                .apply()
                        }
                    }
                )

                // Down-Shift Cog
                val downSelectedText = when {
                    synchroDownCog == -1 -> "Off"
                    synchroDownMode == "AUTO" && synchroDownCog > 0 -> "Auto ($synchroDownCog)"
                    synchroDownMode == "AUTO" -> "Auto"
                    else -> "Cog $synchroDownCog"
                }

                DropdownSettingCard(
                    title = "Shift Down Cog",
                    icon = Icons.Rounded.ArrowDownward,
                    selectedValue = downSelectedText,
                    options = cogOptions,
                    onOptionSelected = { cog ->
                        val sensorKey = selectedBike
                        val modeKey = "synchro_down_mode_$sensorKey"
                        val cogKey = "synchro_down_cog_$sensorKey"
                        val candKey = "synchro_down_cand_$sensorKey"
                        val countKey = "synchro_down_count_$sensorKey"

                        if (cog == 0) {
                            synchroDownMode = "AUTO"
                            synchroDownCog = 0
                            sharedPreferences.edit()
                                .putString(modeKey, "AUTO")
                                .putInt(cogKey, 0)
                                .putInt(candKey, 0)
                                .putInt(countKey, 0)
                                .apply()
                        } else {
                            synchroDownMode = "MANUAL"
                            synchroDownCog = cog
                            sharedPreferences.edit()
                                .putString(modeKey, "MANUAL")
                                .putInt(cogKey, cog)
                                .apply()
                        }
                    }
                )
            }

            Button(
                onClick = {
                    val sensorKey = selectedBike
                    val globalLowGear = sharedPreferences.getBoolean("low_gear_alert_enabled", true)
                    val globalHighGear = sharedPreferences.getBoolean("high_gear_alert_enabled", true)
                    val globalCassette = sharedPreferences.getInt("pref_cassette_size", 0)
                    val globalBrand = sharedPreferences.getString("pref_drivetrain_brand", "Auto") ?: "Auto"

                    sharedPreferences.edit()
                        .putBoolean("low_gear_alert_enabled_$sensorKey", globalLowGear)
                        .putBoolean("high_gear_alert_enabled_$sensorKey", globalHighGear)
                        .putInt("pref_cassette_size_$sensorKey", globalCassette)
                        .putString("pref_drivetrain_brand_$sensorKey", globalBrand)
                        .putString("synchro_up_mode_$sensorKey", "AUTO")
                        .putInt("synchro_up_cog_$sensorKey", 0)
                        .putString("synchro_down_mode_$sensorKey", "AUTO")
                        .putInt("synchro_down_cog_$sensorKey", 0)
                        .apply()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Reset to Default Settings")
            }

            // Cassette Size
            DropdownSettingCard(
                title = "Cassette Size",
                icon = Icons.Rounded.FormatListNumbered,
                selectedValue = when(cassetteSize) {
                    0 -> "Auto"
                    else -> "${cassetteSize}S"
                },
                options = listOf(0 to "Auto", 10 to "10S", 11 to "11S", 12 to "12S", 13 to "13S"),
                onOptionSelected = { size ->
                    cassetteSize = size
                    sharedPreferences.edit()
                        .putInt("pref_cassette_size_$selectedBike", size)
                        .apply()
                }
            )

            // Drivetrain Brand
            DropdownSettingCard(
                title = "Drivetrain",
                icon = Icons.Rounded.Settings,
                selectedValue = drivetrainBrand,
                options = listOf("Auto" to "Auto", "SRAM" to "SRAM", "Shimano" to "Shimano"),
                onOptionSelected = { brand ->
                    drivetrainBrand = brand
                    sharedPreferences.edit()
                        .putString("pref_drivetrain_brand_$selectedBike", brand)
                        .apply()
                }
            )

            // Extension Info
            Text(
                text = "Extension Info",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "• Drivetrain Mode: Filters out front shift compensations and adjusts automated cross-chain limits based on paired hardware.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Synchro Warning Alerts: Emits a double beep when entering a boundary gear before an automated synchronized front shift.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Compensation Shift Muting: Automatically suppresses transient audio alerts during front ring operations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CompactToggleCard(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Icon + Title + Subtitle (takes up available space)
            Row(
                modifier = Modifier.weight(1f), // Takes up remaining space
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f) // Allows text to wrap
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        overflow = TextOverflow.Clip // Allows wrapping
                    )
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            overflow = TextOverflow.Clip // Allows wrapping
                        )
                    }
                }
            }

            // Right side: Switch
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}

@Composable
fun MiniInfoCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatusCard(title: String, status: String, icon: ImageVector, isActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun InfoCard(title: String, description: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun DefaultPreview() {
    KogSenseTheme {
        KogSenseScreen()
    }
}
