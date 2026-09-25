package cc.opencar.assistant.feature.web

import android.content.Context
import cc.opencar.assistant.api.DeviceClass
import cc.opencar.assistant.api.EntityContract
import cc.opencar.assistant.api.EntityDef
import cc.opencar.assistant.api.EntityRegistry
import cc.opencar.assistant.api.EntityType
import cc.opencar.assistant.api.PropertyValue
import cc.opencar.assistant.api.ReadOutcome
import cc.opencar.assistant.api.UnitOfMeasurement
import cc.opencar.assistant.api.VehicleSession
import cc.opencar.assistant.api.WellKnownProperties
import cc.opencar.assistant.feature.memory.SettingsMemoryController
import cc.opencar.assistant.support.I18nBundle
import cc.opencar.assistant.support.LastKnownStore
import kotlinx.coroutines.flow.first

/**
 * Product entity API facade over [EntityRegistry].
 * Builds `/api/entities` maps and handles set/read (including composite climate).
 */
object ControlCatalog {
    val ALL: List<EntityDef> get() = EntityRegistry.ALL

    fun i18n(context: Context, session: VehicleSession): I18nBundle =
        I18nBundle.load(context, session.integrationId)

    suspend fun snapshot(
        session: VehicleSession,
        context: Context? = null,
        memory: SettingsMemoryController? = null,
    ): List<Map<String, Any?>> {
        val store = context?.let { LastKnownStore(it) }
        val i18n = context?.let { i18n(it, session) }
        val persist = memory?.persistSnapshot().orEmpty()
        return ALL.mapNotNull { def ->
            val base = when {
                def.id == "climate" -> climateMap(session, def, store, i18n) ?: return@mapNotNull null
                def.id == "drive_mode" -> driveModeMap(session, def, store, i18n)
                else -> {
                    val prop = def.property() ?: return@mapNotNull null
                    defToMap(def, session.diagnose(prop), store, i18n, session)
                }
            }
            enrichPersist(base, def, persist[def.id], i18n)
        }
    }

    suspend fun entities(
        session: VehicleSession,
        context: Context? = null,
        memory: SettingsMemoryController? = null,
        android: AndroidSettingsController? = null,
        location: LocationTrackerController? = null,
    ): List<Map<String, Any?>> {
        val controls = snapshot(session, context, memory)
        val t = session.telemetry().first()
        val i18n = context?.let { i18n(it, session) }
        fun s(key: String, fallback: String) = i18n?.t(key, fallback) ?: fallback
        val persist = memory?.persistSnapshot().orEmpty()
        val vinValue = when (val out = session.diagnose(WellKnownProperties.INFO_VIN)) {
            is ReadOutcome.Ok -> out.value?.display()
            else -> null
        }
        val sensors = listOf(
            sensor("sensor_model", "vehicle", s("sensor.model", "Modelo"), t.extras["model"], icon = "sensor", i18n = i18n),
            sensor("sensor_vin", "vehicle", s("sensor.vin", "VIN"), vinValue, icon = "sensor", i18n = i18n),
            sensor(
                "sensor_gear", "home", s("sensor.gear", "Marcha"), t.gear?.toString(),
                icon = "drive", history = true, i18n = i18n, valueMapId = "gear",
            ),
            sensor(
                "sensor_speed", "home", s("sensor.speed", "Velocidade"),
                t.speedKmh?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.SPEED,
                unitOfMeasurement = UnitOfMeasurement.KM_PER_HOUR,
                icon = "sensor", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_soc", "home", s("sensor.soc", "Bateria"),
                (t.evBatteryPercent ?: t.hybridSocPercent)?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.BATTERY,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "battery", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_fuel", "home", s("sensor.fuel", "Combustível"),
                t.fuelPercent?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.FUEL,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_range", "home", s("sensor.range", "Autonomia"),
                t.rangeKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_range_ev", "home", s("sensor.range_ev", "Autonomia EV"),
                t.rangeEvKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "battery", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_range_fuel", "home", s("sensor.range_fuel", "Autonomia combustível"),
                t.rangeFuelKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_odometer", "vehicle", s("sensor.odometer", "Odômetro"),
                t.odometerKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "sensor", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_ignition", "home", s("sensor.ignition", "Ignição"),
                t.ignitionState?.toString(),
                icon = "drive", i18n = i18n, valueMapId = "ignition",
            ),
            sensor(
                "sensor_parking_brake", "home", s("sensor.parking_brake", "Freio"),
                t.extras["parkingBrake"],
                icon = "brake", i18n = i18n, binary = true, valueMapId = "parking_brake",
            ),
            sensor(
                "sensor_temp_ambient", "controls", s("sensor.temp_ambient", "Temp. externa"),
                t.tempAmbientC?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.TEMPERATURE,
                unitOfMeasurement = UnitOfMeasurement.CELSIUS,
                icon = "temp", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_battery_temp", "energy", s("sensor.battery_temp", "Temp. bateria"),
                t.batteryTempC?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.TEMPERATURE,
                unitOfMeasurement = UnitOfMeasurement.CELSIUS,
                icon = "battery", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_charge_plug", "energy", s("sensor.charge_plug", "Plug"),
                t.chargePlugConnected?.let { if (it) "1" else "0" },
                icon = "charge", history = true, i18n = i18n, binary = true, valueMapId = "charge_plug",
            ),
            sensor(
                "sensor_hybrid_soc", "energy", s("sensor.hybrid_soc", "SOC híbrido"),
                t.hybridSocPercent?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.BATTERY,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "battery", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_charge_eta", "energy", s("sensor.charge_eta", "Tempo de carga"),
                t.chargeEstimatedTimeMin?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DURATION,
                unitOfMeasurement = UnitOfMeasurement.MINUTE,
                icon = "charge", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_charge_energy", "energy", s("sensor.charge_energy", "Energia de carga"),
                t.chargeEnergyKwh?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.ENERGY,
                unitOfMeasurement = UnitOfMeasurement.KILOWATT_HOUR,
                icon = "charge", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_charge_work_a", "energy", s("sensor.charge_work_a", "Corrente de carga"),
                t.chargeWorkCurrentA?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.CURRENT,
                unitOfMeasurement = UnitOfMeasurement.AMPERE,
                icon = "charge", i18n = i18n,
            ),
            sensor(
                "sensor_charge_work_v", "energy", s("sensor.charge_work_v", "Tensão de carga"),
                t.chargeWorkVoltageV?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.VOLTAGE,
                unitOfMeasurement = UnitOfMeasurement.VOLT,
                icon = "charge", i18n = i18n,
            ),
            sensor(
                "sensor_avg_energy", "energy", s("sensor.avg_energy", "Consumo elétrico"),
                t.avgEnergyKwh100km?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.ENERGY,
                unitOfMeasurement = UnitOfMeasurement.KWH_PER_100KM,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_avg_fuel", "energy", s("sensor.avg_fuel", "Consumo combustível"),
                t.avgFuelL100km?.let { "%.1f".format(it) },
                deviceClass = DeviceClass.FUEL,
                unitOfMeasurement = UnitOfMeasurement.LITER_PER_100KM,
                icon = "energy", history = true, i18n = i18n,
            ),
            sensor(
                "sensor_energy_flow_driving", "energy", s("sensor.energy_flow_driving", "Fluxo tração"),
                t.energyFlowDriving?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.POWER,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "drive", i18n = i18n,
            ),
            sensor(
                "sensor_energy_flow_battery", "energy", s("sensor.energy_flow_battery", "Fluxo bateria"),
                t.energyFlowBattery?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.POWER,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "battery", i18n = i18n,
            ),
            sensor(
                "sensor_energy_flow_climate", "energy", s("sensor.energy_flow_climate", "Fluxo clima"),
                t.energyFlowClimate?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.POWER,
                unitOfMeasurement = UnitOfMeasurement.PERCENT,
                icon = "climate", i18n = i18n,
            ),
            sensor(
                "sensor_maintenance", "vehicle", s("sensor.maintenance", "Próx. revisão"),
                t.maintenanceMileageKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "sensor", i18n = i18n,
            ),
            sensor(
                "sensor_since_maintenance", "vehicle", s("sensor.since_maintenance", "Desde a revisão"),
                t.sinceMaintenanceKm?.let { "%.0f".format(it) },
                deviceClass = DeviceClass.DISTANCE,
                unitOfMeasurement = UnitOfMeasurement.KILOMETER,
                icon = "sensor", i18n = i18n,
            ),
        )
        val androidEntities = android?.entityMaps(i18n, persist).orEmpty()
        val locationEntities = location?.entityMaps(i18n).orEmpty()
        return sensors + controls + androidEntities + locationEntities
    }

    /** Current display value for shortcut conditions / entity_state watching. */
    suspend fun currentValue(session: VehicleSession, id: String): String? {
        val def = EntityRegistry.resolve(id) ?: return null
        if (def.id == "climate") {
            val map = climateMap(session, def, null, null) ?: return null
            val attr = EntityRegistry.aliasAttribute(id)
            return when (attr) {
                null, "power" -> map["value"] as? String
                "temperature" -> (map["attributes"] as? Map<*, *>)?.get("temperature")?.toString()
                "fan_mode" -> (map["attributes"] as? Map<*, *>)?.get("fan_mode")?.toString()
                else -> (map["attributes"] as? Map<*, *>)?.get(attr)?.toString()
                    ?: map["value"] as? String
            }
        }
        val prop = def.property() ?: return null
        return when (val out = session.diagnose(prop)) {
            is ReadOutcome.Ok -> out.value?.display()
            else -> null
        }
    }

    suspend fun set(session: VehicleSession, id: String, raw: String, context: Context? = null): Result<Unit> {
        val store = context?.let { LastKnownStore(it) }
        val resolved = EntityRegistry.resolve(id)
            ?: return Result.failure(IllegalArgumentException("unknown control"))

        if (resolved.id == "climate") {
            val attr = if (id == "climate") null else EntityRegistry.aliasAttribute(id)
            return setClimate(session, resolved, raw, attr, store)
        }

        if (resolved.id == "drive_mode" || id == "drive_mode") {
            val mode = raw.toIntOrNull() ?: return Result.failure(IllegalArgumentException("bad mode"))
            val result = session.set(WellKnownProperties.DRIVE_MODE, PropertyValue.IntVal(mode))
            if (result.isSuccess) store?.put("drive_mode", mode.toString())
            return result
        }

        val def = resolved
        if (!def.writable) {
            return Result.failure(IllegalArgumentException("control is read-only"))
        }
        val prop = def.property()
            ?: return Result.failure(IllegalArgumentException("control has no binding"))
        val pv: PropertyValue = when (def.input) {
            "bool" -> {
                val on = raw == "1" || raw.equals("true", true) || raw == "on"
                PropertyValue.IntVal(if (on) 1 else 0)
            }
            "float" -> PropertyValue.FloatVal(raw.toFloat())
            else -> PropertyValue.IntVal(raw.toInt())
        }
        val result = session.set(prop, pv)
        if (result.isSuccess && def.lastKnown) store?.put(def.id, raw)
        return result
    }

    private suspend fun setClimate(
        session: VehicleSession,
        def: EntityDef,
        raw: String,
        aliasAttr: String?,
        store: LastKnownStore?,
    ): Result<Unit> {
        val v = raw.trim()
        // Alias writes: treat as attribute set (hvac_power=1 → power)
        if (aliasAttr != null) {
            return writeClimateAttr(session, def, aliasAttr, v, store)
        }
        val lower = v.lowercase().replace('-', '_')
        when {
            lower == "off" || lower == "0" -> return writeClimateAttr(session, def, "power", "0", store)
            lower == "on" || lower == "1" -> return writeClimateAttr(session, def, "power", "1", store)
            lower.startsWith("hvac_mode:") || lower.startsWith("hvac_mode_") -> {
                val mode = lower.removePrefix("hvac_mode").trimStart(':', '_')
                return setClimateMode(session, def, mode, store)
            }
            lower.startsWith("temperature:") || lower.startsWith("temperature_") -> {
                val t = lower.removePrefix("temperature").trimStart(':', '_')
                return writeClimateAttr(session, def, "temperature", t, store)
            }
            lower.startsWith("fan_mode:") || lower.startsWith("fan_mode_") -> {
                val f = lower.removePrefix("fan_mode").trimStart(':', '_')
                return writeClimateAttr(session, def, "fan_mode", f, store)
            }
            lower.startsWith("recirc:") || lower.startsWith("recirc_") -> {
                val r = lower.removePrefix("recirc").trimStart(':', '_')
                return writeClimateAttr(session, def, "recirc", r, store)
            }
            lower.startsWith("ac:") || lower.startsWith("ac_") -> {
                val a = lower.removePrefix("ac").trimStart(':', '_')
                return writeClimateAttr(session, def, "ac", a, store)
            }
            lower.toFloatOrNull() != null ->
                return writeClimateAttr(session, def, "temperature", lower, store)
            lower in setOf("auto", "cool", "heat", "fan_only", "defrost") ->
                return setClimateMode(session, def, lower, store)
        }
        return Result.failure(IllegalArgumentException("unknown climate value: $raw"))
    }

    private suspend fun setClimateMode(
        session: VehicleSession,
        def: EntityDef,
        mode: String,
        store: LastKnownStore?,
    ): Result<Unit> {
        when (mode) {
            "off" -> return writeClimateAttr(session, def, "power", "0", store)
            "on" -> return writeClimateAttr(session, def, "power", "1", store)
            "auto" -> {
                writeClimateAttr(session, def, "power", "1", store)
                writeClimateAttr(session, def, "auto", "1", store)
                return writeClimateAttr(session, def, "ac", "0", store)
            }
            "cool" -> {
                writeClimateAttr(session, def, "power", "1", store)
                writeClimateAttr(session, def, "auto", "0", store)
                return writeClimateAttr(session, def, "ac", "1", store)
            }
            "heat", "defrost" -> {
                writeClimateAttr(session, def, "power", "1", store)
                writeClimateAttr(session, def, "auto", "0", store)
                writeClimateAttr(session, def, "ac", "0", store)
                return writeClimateAttr(session, def, "max_defrost", if (mode == "defrost") "1" else "0", store)
            }
            "fan_only" -> {
                writeClimateAttr(session, def, "power", "1", store)
                writeClimateAttr(session, def, "auto", "0", store)
                writeClimateAttr(session, def, "ac", "0", store)
                return writeClimateAttr(session, def, "max_defrost", "0", store)
            }
        }
        return Result.failure(IllegalArgumentException("unknown hvac_mode: $mode"))
    }

    private suspend fun writeClimateAttr(
        session: VehicleSession,
        def: EntityDef,
        attr: String,
        raw: String,
        store: LastKnownStore?,
    ): Result<Unit> {
        val prop = def.attributeProperty(attr)
            ?: return Result.failure(IllegalArgumentException("unknown climate attr: $attr"))
        val pv = when (attr) {
            "temperature" -> PropertyValue.FloatVal(raw.toFloat())
            "fan_mode", "fan_direction" -> PropertyValue.IntVal(raw.toInt())
            else -> {
                val on = raw == "1" || raw.equals("true", true) || raw.equals("on", true)
                PropertyValue.IntVal(if (on) 1 else 0)
            }
        }
        val result = session.set(prop, pv)
        if (result.isSuccess && def.lastKnown) {
            store?.put("climate:$attr", raw)
        }
        return result
    }

    private suspend fun climateMap(
        session: VehicleSession,
        def: EntityDef,
        store: LastKnownStore?,
        i18n: I18nBundle?,
    ): Map<String, Any?>? {
        val powerProp = def.attributeProperty("power")
        val tempProp = def.attributeProperty("temperature")
        val coreBound = listOfNotNull(powerProp, tempProp).any { session.hasBinding(it) }
        if (!coreBound) {
            // Still show if any climate attr is bound
            val any = def.attributes.values.any { session.hasBinding(EntityRegistry.property(it)) }
            if (!any) return null
        }

        suspend fun readInt(attr: String): Int? {
            val p = def.attributeProperty(attr) ?: return null
            if (!session.hasBinding(p)) return null
            return when (val out = session.diagnose(p)) {
                is ReadOutcome.Ok -> out.value?.asInt()
                else -> null
            }
        }
        suspend fun readFloat(attr: String): Float? {
            val p = def.attributeProperty(attr) ?: return null
            if (!session.hasBinding(p)) return null
            return when (val out = session.diagnose(p)) {
                is ReadOutcome.Ok -> out.value?.asFloat()
                else -> null
            }
        }

        val power = readInt("power")
        val auto = readInt("auto")
        val ac = readInt("ac")
        val maxDefrost = readInt("max_defrost")
        val maxAc = readInt("max_ac")
        val temperature = readFloat("temperature")
        val currentTemp = readFloat("current_temperature")
            ?: session.telemetry().first().tempIndoorC
        val fanMode = readInt("fan_mode")
        val fanDirection = readInt("fan_direction")
        val recirc = readInt("recirc")
        val eco = readInt("eco")

        fun on(v: Int?) = v != null && v != 0 && v != 2

        val hvacMode = when {
            power != null && !on(power) -> "off"
            on(auto) -> "auto"
            on(maxDefrost) -> "heat"
            on(ac) || on(maxAc) -> "cool"
            on(power) -> "fan_only"
            else -> "off"
        }

        if (def.lastKnown) {
            store?.put(def.id, hvacMode)
            temperature?.let { store?.put("climate:temperature", it.toString()) }
        }

        val label = i18n?.t(def.resolvedLabelKey(), "Climate") ?: "Climate"
        val hint = if (i18n != null && i18n.has(def.resolvedHintKey())) {
            i18n.t(def.resolvedHintKey()).takeIf { it.isNotBlank() }
        } else null

        val attrs = linkedMapOf<String, Any?>(
            "hvac_modes" to listOf("off", "auto", "cool", "heat", "fan_only"),
            "hvac_mode" to hvacMode,
            "fan_modes" to (0..8).toList(),
        )
        temperature?.let { attrs["temperature"] = it }
        currentTemp?.let { attrs["current_temperature"] = it }
        fanMode?.let { attrs["fan_mode"] = it }
        fanDirection?.let { attrs["fan_direction"] = it }
        attrs["recirc"] = if (on(recirc)) 1 else 0
        attrs["ac"] = if (on(ac)) 1 else 0
        attrs["auto"] = if (on(auto)) 1 else 0
        attrs["eco"] = if (on(eco)) 1 else 0
        attrs["power"] = if (on(power)) 1 else 0
        def.min?.let { attrs["min_temp"] = it }
        def.max?.let { attrs["max_temp"] = it }
        def.step?.let { attrs["target_temp_step"] = it }

        val status = when {
            power != null || temperature != null -> "ok"
            else -> "unavailable"
        }

        return EntityContract.enrich(
            mapOf(
                "id" to def.id,
                "group" to def.group,
                "entity" to def.domain.id,
                "label" to label,
                "labelKey" to def.resolvedLabelKey(),
                "hint" to hint,
                "description" to hint,
                "input" to "climate",
                "icon" to def.resolvedIcon(),
                "deviceClass" to def.deviceClass?.id,
                "unitOfMeasurement" to def.unitOfMeasurement?.id,
                "unitLabel" to resolveUnitLabel(def.unitOfMeasurement, i18n),
                "min" to def.min,
                "max" to def.max,
                "step" to def.step,
                "history" to def.history,
                "writable" to (status == "ok"),
                "value" to hvacMode,
                "valueLabel" to hvacMode,
                "state" to hvacMode,
                "status" to status,
                "permission" to null,
                "needsPrivilege" to false,
                "stale" to false,
                "attributes" to attrs,
                EntityContract.FIELD_COMPOSITE to true,
                EntityContract.FIELD_UPDATE to EntityContract.UPDATE_CATALOG,
            ),
        )
    }

    private fun enrichPersist(
        base: Map<String, Any?>,
        def: EntityDef,
        pin: Map<String, Any?>?,
        i18n: I18nBundle?,
    ): Map<String, Any?> {
        val enabled = pin?.get("enabled") == true
        val pVal = pin?.get("value") as? String
        return base + mapOf(
            "persistEnabled" to enabled,
            "persistValue" to pVal,
            "persistLabel" to pVal?.let { i18n?.valueLabel(def.resolvedValueMapId(), it) ?: it },
        )
    }

    private suspend fun driveModeMap(
        session: VehicleSession,
        def: EntityDef,
        store: LastKnownStore?,
        i18n: I18nBundle?,
    ): Map<String, Any?> {
        val enumOut = session.diagnose(WellKnownProperties.DRIVE_MODE)
        when (enumOut) {
            is ReadOutcome.Ok -> {
                val selected = enumOut.value?.asInt()?.toString()
                if (selected != null) {
                    store?.put(def.id, selected)
                    return baseMap(def, selected, "ok", null, stale = false, i18n = i18n)
                }
            }
            is ReadOutcome.Denied -> {
                val cached = store?.get(def.id)
                if (cached != null) {
                    return baseMap(def, cached, "cached", null, stale = true, i18n = i18n)
                }
                return baseMap(def, null, "denied", enumOut.permission, stale = false, i18n = i18n)
            }
            else -> Unit
        }
        val cached = store?.get(def.id)
        if (cached != null) {
            return baseMap(def, cached, "cached", null, stale = true, i18n = i18n)
        }
        return baseMap(def, null, "unavailable", null, stale = false, i18n = i18n)
    }

    private fun defToMap(
        def: EntityDef,
        outcome: ReadOutcome,
        store: LastKnownStore?,
        i18n: I18nBundle?,
        session: VehicleSession? = null,
    ): Map<String, Any?> {
        // Write-only commands never have a lasting current value — don't surface reads.
        if (def.input == "command") {
            val permission = (outcome as? ReadOutcome.Denied)?.permission
            val denied = outcome is ReadOutcome.Denied
            val bound = def.property()?.let { session?.hasBinding(it) } == true
            val ready = def.writable && bound && !denied
            return baseMap(
                def,
                value = null,
                status = when {
                    denied -> "denied"
                    ready -> "ok"
                    else -> "unavailable"
                },
                permission = permission,
                stale = false,
                i18n = i18n,
                forceWritable = ready,
            )
        }
        val (value, status, permission) = when (outcome) {
            is ReadOutcome.Ok -> {
                val disp = outcome.value?.display()
                // Cache every successful read, including Off/0/false — those are real states.
                if (def.lastKnown && !disp.isNullOrBlank()) {
                    store?.put(def.id, disp)
                }
                Triple(disp, "ok", null as String?)
            }
            is ReadOutcome.Denied -> Triple(null, "denied", outcome.permission)
            is ReadOutcome.Failed -> Triple(null, "failed", outcome.message)
            is ReadOutcome.Unavailable -> Triple(null, "unavailable", null)
        }
        // Only fall back when the live value is actually missing. Off (0) and false are
        // real states — treating them as empty made exterior_light/CST/night_mode show
        // "last known" after the user turned them off, and overwrote card descriptions.
        if (value.isNullOrBlank() && def.lastKnown) {
            val cached = store?.get(def.id)
            if (cached != null) {
                return baseMap(def, cached, "cached", null, stale = true, i18n = i18n)
            }
        }
        return baseMap(def, value, status, permission, stale = false, i18n = i18n)
    }

    private fun optionMaps(def: EntityDef, i18n: I18nBundle?): List<Map<String, Any?>> {
        // Always emit options sorted by ascending numeric value (Auto=3/4 last, etc.).
        val keys = def.optionKeys
        if (keys != null) {
            return keys.sortedBy { it.second }.map { (key, value) ->
                mapOf("label" to (i18n?.t(key, key) ?: key), "value" to value)
            }
        }
        val fromMaps = i18n?.valueMapsSnapshot()?.get(def.resolvedValueMapId())
        if (!fromMaps.isNullOrEmpty() && (def.input == "choice" || def.input == "command")) {
            val parsed = fromMaps.mapNotNull { (k, labelKey) ->
                k.toIntOrNull()?.let { value -> labelKey to value }
            }.sortedBy { it.second }
            if (parsed.isNotEmpty()) {
                return parsed.map { (labelKey, value) ->
                    mapOf("label" to (i18n.t(labelKey, labelKey)), "value" to value)
                }
            }
        }
        return emptyList()
    }

    private fun baseMap(
        def: EntityDef,
        value: String?,
        status: String,
        permission: String?,
        stale: Boolean,
        i18n: I18nBundle?,
        forceWritable: Boolean? = null,
    ): Map<String, Any?> {
        val label = i18n?.t(def.resolvedLabelKey(), def.id) ?: def.id
        // Keep the control's own hint/description even when showing a stale cached value;
        // the UI already surfaces staleness via status/stale (lock-note), not by overwriting copy.
        val hint = if (i18n != null && i18n.has(def.resolvedHintKey())) {
            i18n.t(def.resolvedHintKey()).takeIf { it.isNotBlank() }
        } else {
            null
        }
        val unitLabel = resolveUnitLabel(def.unitOfMeasurement, i18n)
        val writable = forceWritable
            ?: (def.writable && (status == "ok" || status == "cached"))
        return EntityContract.enrich(
            mapOf(
                "id" to def.id,
                "group" to def.group,
                "entity" to def.domain.id,
                "label" to label,
                "labelKey" to def.resolvedLabelKey(),
                "hint" to hint,
                "description" to hint,
                "acronym" to def.acronym,
                "input" to def.input,
                "icon" to def.resolvedIcon(),
                "deviceClass" to def.deviceClass?.id,
                "unitOfMeasurement" to def.unitOfMeasurement?.id,
                "unitLabel" to unitLabel,
                "min" to def.min,
                "max" to def.max,
                "step" to def.step,
                "history" to def.history,
                "writable" to writable,
                "options" to optionMaps(def, i18n).ifEmpty { null },
                "value" to value,
                "valueLabel" to valueLabel(def, value, i18n),
                "status" to status,
                "permission" to permission,
                "needsPrivilege" to (status == "denied"),
                "stale" to stale,
                "writeOnly" to (def.input == "command"),
                EntityContract.FIELD_COMPOSITE to def.isComposite,
                EntityContract.FIELD_UPDATE to EntityContract.updatePolicy(def.isComposite),
            ),
        )
    }

    private fun valueLabel(def: EntityDef, value: String?, i18n: I18nBundle?): String? {
        if (value == null) return null
        if (def.input == "bool") {
            val on = value == "1" || value.equals("true", true) || value == "on"
            return i18n?.t(if (on) "common.on" else "common.off", if (on) "On" else "Off")
                ?: if (on) "On" else "Off"
        }
        i18n?.valueLabel(def.resolvedValueMapId(), value)?.let { return it }
        val asInt = value.toIntOrNull()
        val optionKeys = def.optionKeys
        if (asInt != null && optionKeys != null) {
            optionKeys.firstOrNull { it.second == asInt }?.let { (key, _) ->
                return i18n?.t(key, key) ?: key
            }
            if (asInt > 0xff) return "0x" + asInt.toString(16)
        }
        return null
    }

    private fun resolveUnitLabel(unit: UnitOfMeasurement?, i18n: I18nBundle?): String? {
        if (unit == null) return null
        return i18n?.t(unit.i18nKey(), unit.symbol) ?: unit.symbol
    }

    private fun sensor(
        id: String,
        group: String,
        label: String,
        value: String?,
        deviceClass: DeviceClass? = null,
        unitOfMeasurement: UnitOfMeasurement? = null,
        icon: String = "sensor",
        history: Boolean = false,
        i18n: I18nBundle? = null,
        /** valueMaps id for enum-like raw values (gear, ignition, …). */
        valueMapId: String? = null,
        /** Treat 0/1/on/off/true/false as localized On/Off. */
        binary: Boolean = false,
    ): Map<String, Any?> {
        val ok = value != null && value.isNotBlank()
        val unitLabel = resolveUnitLabel(unitOfMeasurement, i18n)
        val stringKey = "sensor." + id.removePrefix("sensor_")
        val hintKey = "$stringKey.hint"
        val hint = if (i18n != null && i18n.has(hintKey)) {
            i18n.t(hintKey).takeIf { it.isNotBlank() }
        } else {
            null
        }
        val valueLabel = when {
            !ok -> null
            valueMapId != null ->
                i18n?.valueLabel(valueMapId, value)
                    ?: if (binary) sensorBinaryLabel(value, i18n) else null
            binary -> sensorBinaryLabel(value, i18n)
            else -> null
        }
        return EntityContract.enrich(
            mapOf(
                "id" to id,
                "group" to group,
                "entity" to EntityType.SENSOR.id,
                "label" to label,
                "labelKey" to stringKey,
                "hint" to hint,
                "description" to hint,
                "input" to "sensor",
                "icon" to icon,
                "deviceClass" to deviceClass?.id,
                "unitOfMeasurement" to unitOfMeasurement?.id,
                "unitLabel" to unitLabel,
                "writable" to false,
                "options" to null,
                "value" to if (ok) value else null,
                "valueLabel" to valueLabel,
                "status" to if (ok) "ok" else "unavailable",
                "permission" to null,
                "needsPrivilege" to false,
                "stale" to false,
                "history" to history,
                "persistEnabled" to false,
            ),
        )
    }

    private fun sensorBinaryLabel(value: String?, i18n: I18nBundle?): String? {
        if (value == null) return null
        val on = value == "1" || value.equals("true", true) || value.equals("on", true)
        val off = value == "0" || value.equals("false", true) || value.equals("off", true)
        if (!on && !off) return value
        return i18n?.t(if (on) "common.on" else "common.off", if (on) "On" else "Off")
            ?: if (on) "On" else "Off"
    }
}
