package com.kogsense

import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.SystemNotification
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.PlayBeepPattern
import io.hammerhead.karooext.models.SavedDevices
import io.hammerhead.karooext.models.StreamState
import android.os.Handler
import android.os.Looper

enum class DrivetrainBrand(val nameStr: String) {
    AUTO("Auto"),
    SRAM("SRAM"),
    SHIMANO("Shimano"),
    NONE("None"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String?): DrivetrainBrand {
            return entries.find { it.nameStr == value } ?: UNKNOWN
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
    @Volatile private var prevFrontGearIndex: Int = -1
    @Volatile private var prevRearGearIndex: Int = -1
    @Volatile private var synchroStartRearCog: Int = -1
    @Volatile private var isLearningCompensation: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())          // ← add
    @Volatile private var pendingSynchroSettle: Runnable? = null       // ← add
    @Volatile private var lastFrontMax: Int = -1
    @Volatile private var lastRearMax: Int = -1
    @Volatile private var lastSavedDevices: List<SavedDevices.SavedDevice> = emptyList()
    @Volatile private var lastSourceId: String? = null
    @Volatile private var lastFrontShiftTimestamp: Long = 0
    @Volatile private var lastRearShiftTimestamp: Long = 0

    // Cached SharedPreferences values
    @Volatile private var lowGearAlertEnabled = true
    @Volatile private var highGearAlertEnabled = true
    @Volatile private var synchroAlertEnabled = true
    @Volatile private var manualCassetteSize = 0
    @Volatile private var drivetrainBrandPref = DrivetrainBrand.AUTO
    
    @Volatile
    private var autoDetectedBrand = DrivetrainBrand.NONE
    @Volatile private var detectedSourceName = ""

    private val negativeUpCogs = mutableSetOf<Int>()
    private val negativeDownCogs = mutableSetOf<Int>()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        // Only handle per-bike settings (ignore global keys)
        if (key == null) return@OnSharedPreferenceChangeListener

        when {
            key.startsWith("low_gear_alert_enabled_") -> {
                val sensorId = key.removePrefix("low_gear_alert_enabled_")
                if (sensorId == lastSourceId) {
                    lowGearAlertEnabled = prefs.getBoolean(key, true)
                }
            }
            key.startsWith("high_gear_alert_enabled_") -> {
                val sensorId = key.removePrefix("high_gear_alert_enabled_")
                if (sensorId == lastSourceId) {
                    highGearAlertEnabled = prefs.getBoolean(key, true)
                }
            }
            key.startsWith("synchro_alert_enabled_") -> {
                val sensorId = key.removePrefix("synchro_alert_enabled_")
                if (sensorId == lastSourceId) {
                    synchroAlertEnabled = prefs.getBoolean(key, true)
                }
            }
            key.startsWith("pref_cassette_size_") -> {
                val sensorId = key.removePrefix("pref_cassette_size_")
                if (sensorId == lastSourceId) {
                    manualCassetteSize = prefs.getInt(key, 0)
                }
            }
            key.startsWith("pref_drivetrain_brand_") -> {
                val sensorId = key.removePrefix("pref_drivetrain_brand_")
                if (sensorId == lastSourceId) {
                    drivetrainBrandPref = DrivetrainBrand.fromString(prefs.getString(key, DrivetrainBrand.AUTO.nameStr))
                }
            }
            key == KEY_DETECTED_BRAND -> {
                autoDetectedBrand = DrivetrainBrand.fromString(prefs.getString(key, DrivetrainBrand.NONE.nameStr))
            }
            key == KEY_DETECTED_SOURCE_NAME -> {
                detectedSourceName = prefs.getString(key, "") ?: ""
            }
            key == KEY_LAST_SOURCE_ID -> {
                lastSourceId = prefs.getString(key, null)
                // Reload all per-bike settings for the new sensor
                reloadPerBikeSettings(prefs)
            }
        }
    }

    private fun reloadPerBikeSettings(prefs: SharedPreferences) {
        val sensorKey = lastSourceId ?: "default"
        lowGearAlertEnabled = prefs.getBoolean("low_gear_alert_enabled_$sensorKey", true)
        highGearAlertEnabled = prefs.getBoolean("high_gear_alert_enabled_$sensorKey", true)
        synchroAlertEnabled = prefs.getBoolean("synchro_alert_enabled_$sensorKey", true)
        manualCassetteSize = prefs.getInt("pref_cassette_size_$sensorKey", 0)
        drivetrainBrandPref = DrivetrainBrand.fromString(
            prefs.getString("pref_drivetrain_brand_$sensorKey", DrivetrainBrand.AUTO.nameStr)
        )
        negativeUpCogs.clear()
        negativeDownCogs.clear()
    }

    override fun onCreate() {
        super.onCreate()
        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Load global settings (fallback for legacy)
        lowGearAlertEnabled = sharedPreferences.getBoolean(KEY_LOW_GEAR_ALERT, true)
        highGearAlertEnabled = sharedPreferences.getBoolean(KEY_HIGH_GEAR_ALERT, true)
        synchroAlertEnabled = sharedPreferences.getBoolean(KEY_SYNCHRO_ALERT, true)
        manualCassetteSize = sharedPreferences.getInt(KEY_CASSETTE_SIZE, 0)
        drivetrainBrandPref = DrivetrainBrand.fromString(
            sharedPreferences.getString(KEY_DRIVETRAIN_BRAND_PREF, DrivetrainBrand.AUTO.nameStr)
        )
        autoDetectedBrand = DrivetrainBrand.fromString(
            sharedPreferences.getString(KEY_DETECTED_BRAND, DrivetrainBrand.NONE.nameStr)
        )
        detectedSourceName = sharedPreferences.getString(KEY_DETECTED_SOURCE_NAME, "") ?: ""

        // Load per-bike settings (override global if they exist)
        val sensorKey = lastSourceId ?: "default"
        lowGearAlertEnabled = sharedPreferences.getBoolean("low_gear_alert_enabled_$sensorKey", lowGearAlertEnabled)
        highGearAlertEnabled = sharedPreferences.getBoolean("high_gear_alert_enabled_$sensorKey", highGearAlertEnabled)
        synchroAlertEnabled = sharedPreferences.getBoolean("synchro_alert_enabled_$sensorKey", synchroAlertEnabled)
        manualCassetteSize = sharedPreferences.getInt("pref_cassette_size_$sensorKey", manualCassetteSize)
        drivetrainBrandPref = DrivetrainBrand.fromString(
            sharedPreferences.getString("pref_drivetrain_brand_$sensorKey", drivetrainBrandPref.nameStr)
        )

        sharedPreferences.registerOnSharedPreferenceChangeListener(prefsListener)

        // --- ADD THE MIGRATION CODE HERE ---
        // Migrate global settings to per-bike settings for existing users
        val globalLowGear = sharedPreferences.getBoolean(KEY_LOW_GEAR_ALERT, true)
        val globalHighGear = sharedPreferences.getBoolean(KEY_HIGH_GEAR_ALERT, true)
        val globalCassette = sharedPreferences.getInt(KEY_CASSETTE_SIZE, 0)
        val globalBrand = sharedPreferences.getString(KEY_DRIVETRAIN_BRAND_PREF, DrivetrainBrand.AUTO.nameStr)

        if (sharedPreferences.getBoolean("low_gear_alert_enabled_$sensorKey", false) == false) {
            // Only migrate if per-bike settings don't exist
            sharedPreferences.edit()
                .putBoolean("low_gear_alert_enabled_$sensorKey", globalLowGear)
                .putBoolean("high_gear_alert_enabled_$sensorKey", globalHighGear)
                .putInt("pref_cassette_size_$sensorKey", globalCassette)
                .putString("pref_drivetrain_brand_$sensorKey", globalBrand)
                .apply()
        }
        // --- END OF MIGRATION CODE ---

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
            shiftingDevices.find { device ->
                device.id.equals(id, ignoreCase = true) ||
                        id.contains(device.id, ignoreCase = true) ||
                        device.id.contains(id, ignoreCase = true)
            }
        }

        val primaryDevice = activeDevice
            ?: shiftingDevices.find { it.connectionType != "EXTENSION" }
            ?: shiftingDevices.firstOrNull()

        var detected = DrivetrainBrand.UNKNOWN
        val deviceName = primaryDevice?.name ?: "Unknown Source"

        if (primaryDevice != null) {
            val name = primaryDevice.name.lowercase()
            val manufacturer = primaryDevice.details.manufacturer?.lowercase() ?: ""

            if (name.contains("sram") || name.contains("axs") || manufacturer.contains("sram")) {
                detected = DrivetrainBrand.SRAM
            } else if (name.contains("shimano") || name.contains("di2") || name.contains("ki2") ||
                name.contains("dura") || name.contains("ultegra") || name.contains("105") ||
                name.contains("grx") || name.contains("steps") || name.contains("ew-") ||
                manufacturer.contains("shimano")) {
                detected = DrivetrainBrand.SHIMANO
            }

            primaryDevice.gearInfo?.let { info ->
                if (info.maxFrontGears > 0) lastFrontMax = info.maxFrontGears
                if (info.maxRearGears > 0) lastRearMax = info.maxRearGears
            }

            // Store the sensor name in SharedPreferences
            if (lastSourceId != null) {
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                prefs.edit()
                    .putString("sensor_name_$lastSourceId", deviceName)
                    .apply()
            }
        }

        val reportBrand = if (lastSourceId != null) detected else DrivetrainBrand.NONE
        val reportName = if (lastSourceId != null) deviceName else ""

        if (reportBrand != autoDetectedBrand || reportName != detectedSourceName) {
            autoDetectedBrand = reportBrand
            detectedSourceName = reportName
            val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_DETECTED_BRAND, reportBrand.nameStr)
                .putString(KEY_DETECTED_SOURCE_NAME, reportName)
            if (lastSourceId != null) {
                editor.putString(KEY_LAST_SOURCE_ID, lastSourceId)
            }
            editor.apply()
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
        var triggerUpDoubleBeep = false
        var triggerDownDoubleBeep = false
        
        synchronized(stateLock) {
            if (sourceId != null && sourceId != lastSourceId) {
                lastSourceId = sourceId
                detectDrivetrainBrandLocked()
            }

            val drivetrainBrand = if (drivetrainBrandPref == DrivetrainBrand.AUTO) autoDetectedBrand else drivetrainBrandPref

            // DATA EXTRACTION
            val frontGear = values[DataType.Field.SHIFTING_FRONT_GEAR]?.toInt() ?: lastFrontGearIndex
            values[DataType.Field.SHIFTING_FRONT_GEAR_MAX]?.toInt()?.also { lastFrontMax = it }
            val rearGear = values[DataType.Field.SHIFTING_REAR_GEAR]?.toInt() ?: lastRearGearIndex
            val sdkRearMax = values[DataType.Field.SHIFTING_REAR_GEAR_MAX]?.toInt()?.also { lastRearMax = it } ?: lastRearMax
            
            val now = System.currentTimeMillis()

            val frontChanged = lastFrontGearIndex != -1 && frontGear != lastFrontGearIndex
            val rearChanged = lastRearGearIndex != -1 && rearGear != lastRearGearIndex

            if (frontChanged) {
                prevFrontGearIndex = lastFrontGearIndex
                val isUpShift = frontGear > lastFrontGearIndex
                val recentRearShift = (now - lastRearShiftTimestamp < RECENT_REAR_SHIFT_WINDOW_MS) && prevRearGearIndex > 0
                synchroStartRearCog = if (recentRearShift) {
                    if (isUpShift) maxOf(prevRearGearIndex, lastRearGearIndex)
                    else minOf(prevRearGearIndex, lastRearGearIndex)
                } else {
                    lastRearGearIndex
                }
                isLearningCompensation = true
                lastFrontShiftTimestamp = now
                scheduleSynchroSettle()
            }

            if (rearChanged) {
                prevRearGearIndex = lastRearGearIndex
                lastRearShiftTimestamp = now
                Log.d(
                    "KogSense",
                    "Rear gear changed: $prevRearGearIndex -> $rearGear"
                )
                if (isLearningCompensation) {
                    scheduleSynchroSettle()
                } else {
                    // Record Negative Evidence when riding normally without a front shift:
                    if (frontGear == 1 && prevRearGearIndex > 0 && rearGear > prevRearGearIndex) {
                        // Small ring: shifted to a smaller cog (harder gear) without a synchro shift
                        if (negativeUpCogs.add(prevRearGearIndex)) {
                            Log.d("KogSense", "Negative Evidence (UP): Cog $prevRearGearIndex ruled out for sensor: $lastSourceId")
                        }
                        // Invalidate stored setting if it matches this ruled-out cog
                        if (getSynchroUpCog(lastSourceId) == prevRearGearIndex) {
                            val sensorKey = lastSourceId ?: "default"
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                .putInt("${KEY_SYNCHRO_UP_COG_PREFIX}$sensorKey", 0)
                                .putInt("${KEY_SYNCHRO_UP_CAND_PREFIX}$sensorKey", 0)
                                .putInt("${KEY_SYNCHRO_UP_COUNT_PREFIX}$sensorKey", 0)
                                .apply()
                            Log.d("KogSense", "Negative Evidence: Invalidated stored UP cog $prevRearGearIndex for sensor: $lastSourceId")
                        }
                    } else if (frontGear == 2 && prevRearGearIndex > 0 && rearGear < prevRearGearIndex) {
                        // Big ring: shifted to a larger cog (easier gear) without a synchro shift
                        if (negativeDownCogs.add(prevRearGearIndex)) {
                            Log.d("KogSense", "Negative Evidence (DOWN): Cog $prevRearGearIndex ruled out for sensor: $lastSourceId")
                        }
                        // Invalidate stored setting if it matches this ruled-out cog
                        if (getSynchroDownCog(lastSourceId) == prevRearGearIndex) {
                            val sensorKey = lastSourceId ?: "default"
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                .putInt("${KEY_SYNCHRO_DOWN_COG_PREFIX}$sensorKey", 0)
                                .putInt("${KEY_SYNCHRO_DOWN_CAND_PREFIX}$sensorKey", 0)
                                .putInt("${KEY_SYNCHRO_DOWN_COUNT_PREFIX}$sensorKey", 0)
                                .apply()
                            Log.d("KogSense", "Negative Evidence: Invalidated stored DOWN cog $prevRearGearIndex for sensor: $lastSourceId")
                        }
                    }
                }
            }

            // Sync state AFTER computing changes and capturing previous indices
            lastFrontGearIndex = frontGear
            lastRearGearIndex = rearGear

            val baseMax = if (manualCassetteSize > 0) manualCassetteSize else (if (lastRearMax > 0) lastRearMax else (if (sdkRearMax > 0) sdkRearMax else 12))

            val isCompensationShiftWindow = (now - lastFrontShiftTimestamp < COMPENSATION_SHIFT_WINDOW_MS)
            // PROACTIVE LIMIT & SYNCHRO SHIFT LOGIC
            if (rearChanged) {
                if (!isCompensationShiftWindow) {
                    val isLowLimit = rearGear == 1
                    val isSram = (drivetrainBrand == DrivetrainBrand.SRAM)
                    val isSramHighLimit = isSram && lastFrontMax > 1 && frontGear == 1 && rearGear > 0 && (baseMax > 0 && rearGear == baseMax - 1)
                    val isHighLimit = (baseMax > 0 && rearGear == baseMax) || isSramHighLimit

                    if (isLowLimit && lowGearAlertEnabled) {
                        Log.d("KogSense", "Low Limit BEEP (Proactive)")
                        beepFreq = LOW_LIMIT_BEEP_FREQUENCY_HZ
                    } else if (isHighLimit && highGearAlertEnabled) {
                        Log.d("KogSense", "High Limit BEEP (Proactive)")
                        beepFreq = HIGH_LIMIT_BEEP_FREQUENCY_HZ
                    } else if (isSynchroAlertEnabledForSensor(lastSourceId) && prevRearGearIndex > 0) {
                        // 2. PRE-SHIFT SYNCHRO WARNING ALERT
                        val synchroUpCog = getSynchroUpCog(lastSourceId)
                        val synchroDownCog = getSynchroDownCog(lastSourceId)

                        val shiftingUp = rearGear > prevRearGearIndex
                        val shiftingDown = rearGear < prevRearGearIndex

                        if (shiftingUp && frontGear == 1 && synchroUpCog > 0 && rearGear == synchroUpCog) {
                            Log.d("KogSense", "Synchro UP Warning BEEP for gear 1/$rearGear (sensor: $lastSourceId)")
                            triggerUpDoubleBeep = true
                        } else if (shiftingDown && frontGear == 2 && synchroDownCog > 0 && rearGear == synchroDownCog) {
                            Log.d("KogSense", "Synchro DOWN Warning BEEP for gear 2/$rearGear (sensor: $lastSourceId)")
                            triggerDownDoubleBeep = true
                        }
                    }
                }
            }
        }

        if (triggerUpDoubleBeep) {
            playAscendingBeep()
        } else if (triggerDownDoubleBeep) {
            playDescendingBeep()
        } else {
            beepFreq?.let { playBeep(it) }
        }
    }

    private fun scheduleSynchroSettle() {
        pendingSynchroSettle?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { finalizeSynchroLearning() }
        pendingSynchroSettle = runnable
        mainHandler.postDelayed(runnable, SYNCHRO_SETTLE_QUIET_MS)
    }

    private fun finalizeSynchroLearning() = synchronized(stateLock) {
        pendingSynchroSettle = null
        if (!isLearningCompensation) return@synchronized
        isLearningCompensation = false

        if (!isSynchroAlertEnabledForSensor(lastSourceId)) return@synchronized

        val startFront = prevFrontGearIndex
        val endFront = lastFrontGearIndex
        val startRear = synchroStartRearCog
        val endRear = lastRearGearIndex

        if (startFront <= 0 || startRear <= 0) return@synchronized

        Log.d(
            "KogSense",
            "Finalizing synchro: Front: $startFront -> $endFront, Start Rear: $startRear, Final Rear: $endRear"
        )

        // Up-shift: Front increased (Front 1 -> 2).
        // Synchro compensation moves rear to smaller cogs (endRear < startRear).
        if (endFront > startFront && endRear < startRear) {
            handleAutoLearnUp(lastSourceId, startRear)
        }
        // Down-shift: Front decreased (Front 2 -> 1).
        // Synchro compensation moves rear to larger cogs (endRear > startRear).
        else if (endFront < startFront && endRear > startRear) {
            handleAutoLearnDown(lastSourceId, startRear)
        }
    }

    private fun getSynchroUpMode(sensorId: String?): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString("${KEY_SYNCHRO_UP_MODE_PREFIX}${sensorId ?: "default"}", "AUTO") ?: "AUTO"
    }

    private fun getSynchroDownMode(sensorId: String?): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString("${KEY_SYNCHRO_DOWN_MODE_PREFIX}${sensorId ?: "default"}", "AUTO") ?: "AUTO"
    }

    private fun getSynchroUpCog(sensorId: String?): Int {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getInt("${KEY_SYNCHRO_UP_COG_PREFIX}${sensorId ?: "default"}", 0)
    }

    private fun getSynchroDownCog(sensorId: String?): Int {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getInt("${KEY_SYNCHRO_DOWN_COG_PREFIX}${sensorId ?: "default"}", 0)
    }

    private fun isSynchroAlertEnabledForSensor(sensorId: String?): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean("${KEY_SYNCHRO_ALERT_PREFIX}${sensorId ?: "default"}", synchroAlertEnabled)
    }

    private fun showLearningNotification(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
        val notification = SystemNotification(
            id = "kogsense_learning_${System.currentTimeMillis()}",
            message = message,
            header = "KogSense",
            style = SystemNotification.Style.EVENT
        )
        karooSystem.dispatch(notification)
    }

    private fun handleAutoLearnUp(sensorId: String?, cog: Int) {
        if (!isSynchroAlertEnabledForSensor(sensorId)) return
        if (getSynchroUpMode(sensorId) != "AUTO") return
        if (getSynchroUpCog(sensorId) == -1) return

        // 1. Negative Evidence Check
        if (negativeUpCogs.contains(cog)) {
            Log.d("KogSense", "Auto-learn UP REJECTED: Cog $cog is in negative evidence table for sensor: $sensorId")
            return
        }

        // 2. Plausibility Bound Check (UP boundary must be in upper half of cassette)
        val cassette = if (manualCassetteSize > 0) manualCassetteSize else (if (lastRearMax > 0) lastRearMax else 12)
        val minPlausibleUpCog = maxOf(2, cassette / 2)
        if (cog < minPlausibleUpCog) {
            Log.d("KogSense", "Auto-learn UP REJECTED: Cog $cog is below plausibility bound (>= $minPlausibleUpCog) for sensor: $sensorId")
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val key = "${KEY_SYNCHRO_UP_COG_PREFIX}${sensorId ?: "default"}"
        val candKey = "${KEY_SYNCHRO_UP_CAND_PREFIX}${sensorId ?: "default"}"
        val countKey = "${KEY_SYNCHRO_UP_COUNT_PREFIX}${sensorId ?: "default"}"

        val currentCog = prefs.getInt(key, 0)
        val lastCand = prefs.getInt(candKey, 0)
        val count = if (lastCand == cog) prefs.getInt(countKey, 0) + 1 else 1

        prefs.edit().putInt(candKey, cog).putInt(countKey, count).apply()

        // Require 2x confirmation even when unconfigured to prevent false positives
        if (count >= 2) {
            val isNew = currentCog != cog
            prefs.edit().putInt(key, cog).apply()
            Log.d("KogSense", "Auto-learned Synchro UP boundary cog: $cog for sensor: $sensorId")
            if (isNew) {
                showLearningNotification("Synchro UP: Cog $cog")
            }
        } else {
            Log.d("KogSense", "Candidate Synchro UP boundary cog: $cog (Count: $count/2) for sensor: $sensorId")
        }
    }

    private fun handleAutoLearnDown(sensorId: String?, cog: Int) {
        if (!isSynchroAlertEnabledForSensor(sensorId)) return
        if (getSynchroDownMode(sensorId) != "AUTO") return
        if (getSynchroDownCog(sensorId) == -1) return

        // 1. Negative Evidence Check
        if (negativeDownCogs.contains(cog)) {
            Log.d("KogSense", "Auto-learn DOWN REJECTED: Cog $cog is in negative evidence table for sensor: $sensorId")
            return
        }

        // 2. Plausibility Bound Check (DOWN boundary must be in lower half of cassette)
        val cassette = if (manualCassetteSize > 0) manualCassetteSize else (if (lastRearMax > 0) lastRearMax else 12)
        val maxPlausibleDownCog = (cassette / 2) + 1
        if (cog > maxPlausibleDownCog) {
            Log.d("KogSense", "Auto-learn DOWN REJECTED: Cog $cog is above plausibility bound (<= $maxPlausibleDownCog) for sensor: $sensorId")
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val key = "${KEY_SYNCHRO_DOWN_COG_PREFIX}${sensorId ?: "default"}"
        val candKey = "${KEY_SYNCHRO_DOWN_CAND_PREFIX}${sensorId ?: "default"}"
        val countKey = "${KEY_SYNCHRO_DOWN_COUNT_PREFIX}${sensorId ?: "default"}"

        val currentCog = prefs.getInt(key, 0)
        val lastCand = prefs.getInt(candKey, 0)
        val count = if (lastCand == cog) prefs.getInt(countKey, 0) + 1 else 1

        prefs.edit().putInt(candKey, cog).putInt(countKey, count).apply()

        // Require 2x confirmation even when unconfigured
        if (count >= 2) {
            val isNew = currentCog != cog
            prefs.edit().putInt(key, cog).apply()
            Log.d("KogSense", "Auto-learned Synchro DOWN boundary cog: $cog for sensor: $sensorId")
            if (isNew) {
                showLearningNotification("Synchro DOWN: Cog $cog")
            }
        } else {
            Log.d("KogSense", "Candidate Synchro DOWN boundary cog: $cog (Count: $count/2) for sensor: $sensorId")
        }
    }

    private fun playBeep(frequency: Int) {
        val tones = listOf(PlayBeepPattern.Tone(frequency, BEEP_DURATION_MS))
        karooSystem.dispatch(PlayBeepPattern(tones))
    }

    private fun playAscendingBeep() {
        val tones = listOf(
            PlayBeepPattern.Tone(SYNCHRO_LOW_BEEP_HZ, SYNCHRO_BEEP_TONE_DURATION_MS),
            PlayBeepPattern.Tone(null, SYNCHRO_BEEP_PAUSE_MS),
            PlayBeepPattern.Tone(SYNCHRO_HIGH_BEEP_HZ, SYNCHRO_BEEP_TONE_DURATION_MS)
        )
        karooSystem.dispatch(PlayBeepPattern(tones))
    }

    private fun playDescendingBeep() {
        val tones = listOf(
            PlayBeepPattern.Tone(SYNCHRO_HIGH_BEEP_HZ, SYNCHRO_BEEP_TONE_DURATION_MS),
            PlayBeepPattern.Tone(null, SYNCHRO_BEEP_PAUSE_MS),
            PlayBeepPattern.Tone(SYNCHRO_LOW_BEEP_HZ, SYNCHRO_BEEP_TONE_DURATION_MS)
        )
        karooSystem.dispatch(PlayBeepPattern(tones))
    }

    override fun onDestroy() {
        pendingSynchroSettle?.let { mainHandler.removeCallbacks(it) }   // ← add this line first
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
        private const val KEY_SYNCHRO_ALERT = "synchro_alert_enabled"
        private const val KEY_CASSETTE_SIZE = "pref_cassette_size"
        private const val KEY_LAST_SOURCE_ID = "last_source_id"

        private const val KEY_SYNCHRO_ALERT_PREFIX = "synchro_alert_enabled_"
        private const val KEY_SYNCHRO_UP_MODE_PREFIX = "synchro_up_mode_"
        private const val KEY_SYNCHRO_DOWN_MODE_PREFIX = "synchro_down_mode_"
        private const val KEY_SYNCHRO_UP_COG_PREFIX = "synchro_up_cog_"
        private const val KEY_SYNCHRO_DOWN_COG_PREFIX = "synchro_down_cog_"
        private const val KEY_SYNCHRO_UP_CAND_PREFIX = "synchro_up_cand_"
        private const val KEY_SYNCHRO_UP_COUNT_PREFIX = "synchro_up_count_"
        private const val KEY_SYNCHRO_DOWN_CAND_PREFIX = "synchro_down_cand_"
        private const val KEY_SYNCHRO_DOWN_COUNT_PREFIX = "synchro_down_count_"

        private const val RECENT_REAR_SHIFT_WINDOW_MS = 600L
        private const val COMPENSATION_SHIFT_WINDOW_MS = 1500L
        private const val SYNCHRO_SETTLE_QUIET_MS = 1200L
        private const val LOW_LIMIT_BEEP_FREQUENCY_HZ = 3000
        private const val HIGH_LIMIT_BEEP_FREQUENCY_HZ = 3800
        private const val SYNCHRO_LOW_BEEP_HZ = 3000
        private const val SYNCHRO_HIGH_BEEP_HZ = 3800
        private const val BEEP_DURATION_MS = 200
        private const val SYNCHRO_BEEP_TONE_DURATION_MS = 100
        private const val SYNCHRO_BEEP_PAUSE_MS = 40
    }
}
