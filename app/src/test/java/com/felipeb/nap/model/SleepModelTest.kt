package com.felipeb.nap.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SleepModelTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val day = LocalDate.of(2026, 9, 20)

    private fun at(dayOffset: Long, hour: Int, minute: Int = 0) =
        LocalDateTime.of(day.plusDays(dayOffset), java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant()

    /** Uma semana dormindo 23h -> 7h. */
    private fun regular(nights: Int = 6) = (0 until nights).map { d ->
        SleepSession(at(d.toLong(), 23), at(d + 1L, 7))
    }

    @Test
    fun rested_person_has_good_energy_during_the_day() {
        val sessions = regular()
        val morning = SleepModel.energy(sessions, at(6, 10), zone)
        val night = SleepModel.energy(sessions, at(6, 23, 30), zone)
        println("descansado 10h=$morning  23h30=$night")
        assertTrue(morning >= 70)
        assertTrue(night < morning)
    }

    @Test
    fun all_nighter_is_much_worse_than_rested() {
        val rested = regular()
        // virou a noite: última sessão termina no dia 5 às 7h, sem dormir na noite do dia 5
        val allNighter = regular(5)
        val restedAt10 = SleepModel.energy(rested, at(6, 10), zone)
        val tiredAt10 = SleepModel.energy(allNighter, at(6, 10), zone)
        val tiredAt4 = SleepModel.energy(allNighter, at(6, 4), zone)
        println("virado 04h=$tiredAt4  10h=$tiredAt10  (descansado 10h=$restedAt10)")
        assertTrue(tiredAt10 < restedAt10 - 30)
        assertTrue(tiredAt4 < 25)
    }

    @Test
    fun short_sleep_is_worse_than_full_sleep() {
        val base = regular(5)
        val short = base + SleepSession(at(5, 23), at(6, 3, 30))
        val full = base + SleepSession(at(5, 23), at(6, 7))
        val e1 = SleepModel.energy(short, at(6, 12), zone)
        val e2 = SleepModel.energy(full, at(6, 12), zone)
        println("4h30 dormidas 12h=$e1  8h dormidas 12h=$e2")
        assertTrue(e1 < e2)
    }

    @Test
    fun naps_reduce_debt_without_counting_as_a_night() {
        val night = SleepSession(at(4, 20, 30), at(5, 8, 40))   // 12h10, sex -> sáb
        val nap = SleepSession(at(5, 15), at(5, 15, 30))        // cochilo de 30 min
        assertTrue(nap.isNap && !night.isNap)
        // só o cochilo: nenhum dia com sono principal, sem dívida inventada
        assertTrue(SleepModel.sleepDebtHours(listOf(nap), at(5, 20), zone) == 0.0)
        val shortNight = SleepSession(at(4, 2), at(4, 7))       // 5h
        val withoutNap = SleepModel.sleepDebtHours(listOf(shortNight), at(4, 20), zone)
        val withNap = SleepModel.sleepDebtHours(listOf(shortNight, SleepSession(at(4, 14), at(4, 15))), at(4, 20), zone)
        assertTrue(withoutNap in 2.9..3.1 && withNap in 1.9..2.1)
        assertTrue(SleepModel.lastMainSleep(listOf(night, nap), at(5, 20)) == night)
    }

    @Test
    fun debt_counts_only_logged_nights() {
        val sessions = listOf(
            SleepSession(at(4, 1), at(4, 7)),   // 6h
            SleepSession(at(5, 2), at(5, 7)),   // 5h
        )
        val debt = SleepModel.sleepDebtHours(sessions, at(5, 12), zone)
        assertTrue(debt in 4.9..5.1)
    }
}
