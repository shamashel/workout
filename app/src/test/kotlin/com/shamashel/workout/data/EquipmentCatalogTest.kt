package com.shamashel.workout.data

import com.shamashel.workout.engine.EngineSettings
import com.shamashel.workout.engine.defaultBarbellWeights
import com.shamashel.workout.engine.defaultDbWeights
import com.shamashel.workout.engine.defaultLatWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EquipmentCatalogTest {
    @Test
    fun defaultDumbbells_matchTheEngineList() {
        assertTrue(sameWeights(defaultDumbbells(false).weights(), defaultDbWeights(false)))
        assertTrue(sameWeights(defaultDumbbells(true).weights(), defaultDbWeights(true)))
        assertEquals("5 to 150 lb in 5 lb steps", defaultDumbbells(false).preview())
    }

    @Test
    fun defaultLatStack_matchesTheEngineList() {
        assertTrue(sameWeights(defaultLatStack().weights(), defaultLatWeights()))
        assertEquals("10 to 200 lb in 5 lb steps", defaultLatStack().preview())
    }

    @Test
    fun defaultBar_matchesTheFivePoundGrid() {
        assertTrue(sameWeights(defaultBar().weights(), defaultBarbellWeights()))
        assertEquals("45 lb bar, 5 lb jumps, up to 545 lb", defaultBar().preview())
    }

    @Test
    fun storedDefaultStrings_roundTripToTheSameLoads() {
        val stored = EngineSettings().toEntity(writeHealthConnect = false)
        val again = stored.toDraft().toSettings()
        assertTrue(sameWeights(parseWeights(stored.availableDb), parseWeights(again.availableDb)))
        assertTrue(sameWeights(parseWeights(stored.availableBarbell), parseWeights(again.availableBarbell)))
        assertTrue(sameWeights(parseWeights(stored.availableLat), parseWeights(again.availableLat)))
        assertFalse(again.availableDb.contains(".0"))
        assertEquals("5", plainLb(5.0))
        assertEquals("2.5", plainLb(2.5))
    }

    @Test
    fun dumbbellExceptions_addAndRemoveSingleBells() {
        val edited = defaultDumbbells().removeWeight(40.0).addWeight(17.5)
        val loads = edited.weights()
        assertFalse(loads.any { abs(it - 40.0) <= 0.05 })
        assertTrue(loads.any { abs(it - 17.5) <= 0.05 })
        assertTrue(sameWeights(inferRange(loads, defaultDumbbells()).weights(), loads))
        assertEquals("5 to 150 lb in 5 lb steps, 1 added, 1 removed", edited.preview())
    }

    @Test
    fun barWithoutTwoAndAHalf_skipsFifty() {
        val plates = setOf(45.0, 25.0, 10.0, 5.0)
        val loads = loadableBar(45.0, plates, 545.0)
        assertFalse(loads.any { abs(it - 50.0) <= 0.05 })
        assertTrue(loads.any { abs(it - 55.0) <= 0.05 })
        val inferred = inferBar(loads)
        assertFalse(inferred.plates.any { abs(it - 2.5) <= 0.05 })
        assertTrue(inferred.plates.any { abs(it - 5.0) <= 0.05 })
        assertTrue(sameWeights(inferred.weights(), loads))
    }

    @Test
    fun customBarList_isKeptUntilPlatesChange() {
        val odd = listOf(45.0, 95.0, 135.0, 185.0, 225.0)
        val inferred = inferBar(odd)
        assertTrue(sameWeights(inferred.weights(), odd))
        val replaced = inferred.usePlates(DEFAULT_PLATES)
        assertTrue(replaced.weights().any { abs(it - 50.0) <= 0.05 })
        assertFalse(sameWeights(replaced.weights(), odd))
    }

    @Test
    fun microPlates_changeJumpsAndDefaultBellsOnly() {
        val custom = defaultDumbbells().withMax(100.0)
        val draft = SettingsDraft(
            dumbbells = custom,
            bar = defaultBar(),
            lat = defaultLatStack(),
            micro = false,
            dbIncrement = 5.0,
            barIncrement = 10.0,
            latIncrement = 5.0,
            allowSkipDeload = false,
            writeHealthConnect = false,
        )
        val enabled = draft.withMicro(true)
        assertEquals(2.5, enabled.dbIncrement, 0.001)
        assertEquals(5.0, enabled.barIncrement, 0.001)
        assertTrue(sameWeights(enabled.dumbbells.weights(), custom.weights()))
        val defaults = draft.copy(dumbbells = defaultDumbbells()).withMicro(true)
        assertTrue(sameWeights(defaults.dumbbells.weights(), defaultDbWeights(true)))
    }
}
