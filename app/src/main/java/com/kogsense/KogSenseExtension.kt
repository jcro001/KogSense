package com.kogsense

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.StreamState

enum class DrivetrainBrand(val nameStr: String) {
    AUTO("Auto"),
    SRAM("SRAM"),
    SHIMANO("Shimano"),
    NONE("None"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String?): DrivetrainBrand {
            return values().find { it.nameStr == value } ?: UNKNOWN
        }
    }
}

class KogSenseExtension : KarooExtension("kogsense", "1.0.0") {
    private lateinit var karooSystem: KarooSystemService
    private val stateLock = Any()

    private var gearConsumerId: String? = null
    private var deviceConsumerId: String? = null

    @Volatile private var lastFrontGearIndex: Int = -1
    @Volatile private var lastRearGearIndex: Int = -1
    @Volatile private var lastFrontMax: Int = -1
    @Volatile private var lastRearMax: Int = -1
    @Volatile private var lastSavedDevices: List<SavedDevices.SavedDevice> = emptyList()
    @Volatile private var lastSourceId: String? = null
    @Volatile private var lastFrontShiftTimestamp: Long = 0

    // Cached SharedPreferences values
    @Volatile private var lowGearAlertEnabled = true
    @Volatile private var highGearAlertEnabled = true
    @Volatile private var manualCassetteSize = 0
    @Volatile private var drivetrainBrandPref = DrivetrainBrand.AUTO
    
    @Volatile
    private var autoDetectedBrand = DrivetrainBrand.NONE
    @Volatile private var detectedSourceName = ""

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            KEY_LOW_GEAR_ALERT -> lowGearAlertEnabled = prefs.getBoolean(KEY_LOW_GEAR_ALERT, true)
            KEY_HIGH_GEAR_ALERT -> highGearAlertEnabled = prefs.getBoolean(KEY_HIGH_GEAR_ALERT, true)
            KEY_CASSETTE_SIZE -> manualCassetteSize = prefs.getInt(KEY_CASSETTE_SIZE, 0)
            KEY_DRIVETRAIN_BRAND_PREF -> drivetrainBrandPref = DrivetrainBrand.fromString(prefs.getString(KEY_DRIVETRAIN_BRAND_PREF, DrivetrainBrand.AUTO.nameStr))
            KEY_DETECTED_BRAND -> autoDetectedBrand = DrivetrainBrand.fromString(prefs.getString(KEY_DETECTED_BRAND, DrivetrainBrand.NONE.nameStr))
            KEY_DETECTED_SOURCE_NAME -> detectedSourceName = prefs.getString(KEY_DETECTED_SOURCE_NAME, "") ?: ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        lowGearAlertEnabled = sharedPreferences.getBoolean(KEY_LOW_GEAR_ALERT, true)
        highGearAlertEnabled = sharedPreferences.getBoolean(KEY_HIGH_GEAR_ALERT, true)
        manualCassetteSize = sharedPreferences.getInt(KEY_CASSETTE_SIZE, 0)
        drivetrainBrandPref = DrivetrainBrand.fromString(sharedPreferences.getString(KEY_DRIVETRAIN_BRAND_PREF, DrivetrainBrand.AUTO.nameStr))
        autoDetectedBrand = DrivetrainBrand.fromString(sharedPreferences.getString(KEY_DETECTED_BRAND, DrivetrainBrand.NONE.nameStr))
        detectedSourceName = sharedPreferences.getString(KEY_DETECTED_SOURCE_NAME, "") ?: ""
        
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)

        karooSystem = KarooSystemService(this)
        karooSystem.connect { connected ->
            if (connected) {
                subscribeToGears()
                subscribeToDevices()
            }
        }
    }

    private fun subscribeToDevices() {
        deviceConsumerId?.let { karooSystem.removeConsumer(it) }
        deviceConsumerId = karooSystem.addConsumer<SavedDevices>(
            onEvent = { event ->
                synchronized(stateLock) {
                    lastSavedDevices = event.devices
                    detectDrivetrainBrandLocked()
                }
            }
        )
    }

    private fun detectDrivetrainBrandLocked() {
        val shiftingDevices = lastSavedDevices.filter { it.enabled && (
            it.supportedDataTypes.contains(DataType.Type.SHIFTING_GEARS) ||
            it.supportedDataTypes.contains(DataType.Type.SHIFTING_FRONT_GEAR) ||
            it.supportedDataTypes.contains(DataType.Type.SHIFTING_REAR_GEAR)
        )}
        
        val activeDevice = lastSourceId?.let { id ->
            lastSavedDevices.find { it.id.equals(id, ignoreCase = true) || id.contains(it.id, ignoreCase = true) }
        }
        
        val primaryDevice = if (lastSourceId != null) activeDevice else {
            shiftingDevices.find { it.connectionType != "EXTENSION" } ?: shiftingDevices.firstOrNull()
        }

        var detected = DrivetrainBrand.UNKNOWN
        val deviceName = primaryDevice?.name ?: "Unknown Source"

        if (primaryDevice != null) {
            val name = primaryDevice.name.lowercase()
            val manufacturer = primaryDevice.details.manufacturer?.lowercase() ?: ""
            
            if (name.contains("sram") || name.contains("axs") || manufacturer.contains("sram")) {
                detected = DrivetrainBrand.SRAM
            } else if (name.contains("shimano") || name.contains("di2") || name.contains("ki2") || manufacturer.contains("shimano")) {
                detected = DrivetrainBrand.SHIMANO
            }

            primaryDevice.gearInfo?.let { info ->
                if (info.maxFrontGears > 0) lastFrontMax = info.maxFrontGears
                if (info.maxRearGears > 0) lastRearMax = info.maxRearGears
            }
        }

        val reportBrand = if (lastSourceId != null) detected else DrivetrainBrand.NONE
        val reportName = if (lastSourceId != null) deviceName else ""

        if (reportBrand != autoDetectedBrand || reportName != detectedSourceName) {
            autoDetectedBrand = reportBrand
            detectedSourceName = reportName
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_DETECTED_BRAND, reportBrand.nameStr)
                .putString(KEY_DETECTED_SOURCE_NAME, reportName)
                .apply()
        }
    }

    private fun subscribeToGears() {
        gearConsumerId?.let { karooSystem.removeConsumer(it) }
        
        gearConsumerId = karooSystem.addConsumer<OnStreamState>(
            params = OnStreamState.StartStreaming(DataType.Type.SHIFTING_GEARS),
            onEvent = { event ->
                val state = event.state
                if (state is StreamState.Streaming) {
                    handleGearUpdate(state.dataPoint.dataTypeId, state.dataPoint.values, state.dataPoint.sourceId)
                }
            }
        )
    }

    private fun handleGearUpdate(dataTypeId: String, values: Map<String, Double>, sourceId: String?) {
        var beepFreq: Int? = null
        
        synchronized(stateLock) {
            if (sourceId != null && sourceId != lastSourceId) {
                lastSourceId = sourceId
                detectDrivetrainBrandLocked()
            }

            val drivetrainBrand = if (drivetrainBrandPref == DrivetrainBrand.AUTO) autoDetectedBrand else drivetrainBrandPref

            // DATA EXTRACTION
            val frontGear = values[DataType.Field.SHIFTING_FRONT_GEAR]?.toInt() ?: lastFrontGearIndex
            val frontMax = values[DataType.Field.SHIFTING_FRONT_GEAR_MAX]?.toInt()?.also { lastFrontMax = it } ?: lastFrontMax
            val rearGear = values[DataType.Field.SHIFTING_REAR_GEAR]?.toInt() ?: lastRearGearIndex
            val sdkRearMax = values[DataType.Field.SHIFTING_REAR_GEAR_MAX]?.toInt()?.also { lastRearMax = it } ?: lastRearMax
            
            val now = System.currentTimeMillis()
            val frontChanged = frontGear != lastFrontGearIndex && lastFrontGearIndex != -1
            if (frontChanged) lastFrontShiftTimestamp = now
            
            val baseMax = if (manualCassetteSize > 0) manualCassetteSize else (if (lastRearMax > 0) lastRearMax else sdkRearMax)
            val rearChanged = rearGear != lastRearGearIndex && lastRearGearIndex != -1
            
            // PROACTIVE-ONLY LOGIC
            if (rearChanged) {
                val isCompensationShift = frontChanged || (now - lastFrontShiftTimestamp < COMPENSATION_SHIFT_WINDOW_MS)
                if (!isCompensationShift) {
                    val isLowLimit = rearGear == 1
                    val isSram = (drivetrainBrand == DrivetrainBrand.SRAM)
                    val isSramHighLimit = isSram && lastFrontMax > 1 && lastFrontGearIndex == 1 && rearGear > 0 && (baseMax > 0 && rearGear == baseMax - 1)
                    val isHighLimit = (baseMax > 0 && rearGear == baseMax) || isSramHighLimit

                    if (isLowLimit && lowGearAlertEnabled) {
                        Log.d("KogSense", "Low Limit BEEP (Proactive)")
                        beepFreq = LOW_LIMIT_BEEP_FREQUENCY_HZ
                    } else if (isHighLimit && highGearAlertEnabled) {
                        Log.d("KogSense", "High Limit BEEP (Proactive)")
                        beepFreq = HIGH_LIMIT_BEEP_FREQUENCY_HZ
                    }
                }
            }

            // Sync state
            lastRearGearIndex = rearGear
            lastFrontGearIndex = frontGear
        }
        beepFreq?.let { playBeep(it) }
    }

    private fun playBeep(frequency: Int) {
        val tones = listOf(PlayBeepPattern.Tone(frequency, BEEP_DURATION_MS))
        karooSystem.dispatch(PlayBeepPattern(tones))
    }

    override fun onDestroy() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        gearConsumerId?.let { karooSystem.removeConsumer(it) }
        deviceConsumerId?.let { karooSystem.removeConsumer(it) }
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val PREFS_NAME = "kogsense_prefs"
        private const val KEY_DETECTED_BRAND = "detected_brand"
        private const val KEY_DETECTED_SOURCE_NAME = "detected_source_name"
        private const val KEY_DRIVETRAIN_BRAND_PREF = "pref_drivetrain_brand"
        private const val KEY_LOW_GEAR_ALERT = "low_gear_alert_enabled"
        private const val KEY_HIGH_GEAR_ALERT = "high_gear_alert_enabled"
        private const val KEY_CASSETTE_SIZE = "pref_cassette_size"

        private const val COMPENSATION_SHIFT_WINDOW_MS = 1000L
        private const val LOW_LIMIT_BEEP_FREQUENCY_HZ = 3000
        private const val HIGH_LIMIT_BEEP_FREQUENCY_HZ = 3800
        private const val BEEP_DURATION_MS = 200
    }
}
