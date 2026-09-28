package com.felipeb.nap.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

data class SleepSession(val start: Instant, val end: Instant) {
    val duration: Duration get() = Duration.between(start, end)
    val hours: Double get() = duration.toMinutes() / 60.0
    val cycles: Double get() = duration.toMinutes() / SleepModel.CYCLE_MINUTES.toDouble()
    val isNap: Boolean get() = hours < SleepModel.NAP_MAX_HOURS
}

/**
 * Modelo de dois processos (Borbély, 1982; Daan, Beersma & Borbély, 1984).
 *
 * - Processo S (homeostático / adenosina): sobe exponencialmente acordado,
 *   cai exponencialmente dormindo.
 * - Processo C (circadiano): oscilação de ~24h com vale de madrugada,
 *   pico no fim da tarde e uma queda leve depois do almoço.
 *
 * Energia = combinação de (baixa pressão de sono) + (alta circadiana),
 * menos a inércia do sono logo após acordar. É uma estimativa, não diagnóstico.
 */
object SleepModel {
    const val CYCLE_MINUTES = 90L
    const val FALL_ASLEEP_MINUTES = 15L
    const val NEED_HOURS = 8.0
    const val NAP_MAX_HOURS = 3.0

    private const val TAU_RISE_H = 18.2
    private const val TAU_DECAY_H = 4.2

    /** S de alguém em regime normal (16h acordado / 8h dormindo) no fim do dia. */
    private const val S_EQUILIBRIUM_EVENING = 0.62
    private const val S_RESTED = 0.09
    private const val S_EXHAUSTED = 0.85

    private const val HISTORY_DAYS = 10L

    private fun rise(s: Double, hours: Double) = 1 - (1 - s) * exp(-hours / TAU_RISE_H)
    private fun decay(s: Double, hours: Double) = s * exp(-hours / TAU_DECAY_H)
    private fun hoursBetween(a: Instant, b: Instant) = Duration.between(a, b).toMillis() / 3_600_000.0

    /** Pressão de sono (0..1) no instante [at], simulada a partir do histórico. */
    fun homeostatic(sessions: List<SleepSession>, at: Instant): Double {
        val relevant = sessions
            .filter { it.end.isAfter(at.minus(Duration.ofDays(HISTORY_DAYS))) && it.start.isBefore(at) }
            .sortedBy { it.start }
        if (relevant.isEmpty()) return S_EQUILIBRIUM_EVENING

        var s = S_EQUILIBRIUM_EVENING
        var t = relevant.first().start
        for (session in relevant) {
            if (session.start.isAfter(t)) {
                s = rise(s, hoursBetween(t, session.start))
                t = session.start
            }
            val sleepEnd = minOf(session.end, at)
            if (sleepEnd.isAfter(t)) {
                s = decay(s, hoursBetween(t, sleepEnd))
                t = sleepEnd
            }
        }
        if (at.isAfter(t)) s = rise(s, hoursBetween(t, at))
        return s
    }

    /**
     * Deslocamento do relógio biológico em horas, estimado pelo horário médio
     * em que a pessoa acorda (referência: 7h). Quem acorda às 10h tem o vale e o
     * pico ~3h mais tarde.
     */
    fun chronotypeShift(sessions: List<SleepSession>, zone: ZoneId): Double {
        val recent = sessions.sortedByDescending { it.end }.take(7)
        if (recent.isEmpty()) return 0.0
        // média circular para não quebrar em horários perto da meia-noite
        var x = 0.0
        var y = 0.0
        for (s in recent) {
            val local = s.end.atZone(zone)
            val h = local.hour + local.minute / 60.0
            val a = 2 * PI * h / 24
            x += cos(a); y += sin(a)
        }
        var meanWake = atan2(y, x) * 24 / (2 * PI)
        if (meanWake < 0) meanWake += 24
        return (meanWake - 7.0).coerceIn(-4.0, 4.0)
    }

    /** Processo C normalizado para 0..1 (0 = vale da madrugada, 1 = pico da tarde). */
    fun circadian(at: Instant, zone: ZoneId, shift: Double): Double {
        val local = at.atZone(zone)
        var h = local.hour + local.minute / 60.0 - shift
        h = ((h % 24) + 24) % 24
        val base = cos(2 * PI * (h - 17.0) / 24)
        val postLunchDip = 0.3 * exp(-((h - 14.0) * (h - 14.0)) / (2 * 1.5 * 1.5))
        return ((base - postLunchDip + 1.3) / 2.3).coerceIn(0.0, 1.0)
    }

    private fun lastWakeBefore(sessions: List<SleepSession>, at: Instant): SleepSession? =
        sessions.filter { !it.end.isAfter(at) }.maxByOrNull { it.end }

    /** Acordou no meio de um ciclo (fora da janela de sono leve do fim do ciclo)? */
    fun wokeMidCycle(session: SleepSession): Boolean {
        val intoCycle = (session.duration.toMinutes() - FALL_ASLEEP_MINUTES).mod(CYCLE_MINUTES)
        return intoCycle in 20..70
    }

    private fun inertiaPenalty(sessions: List<SleepSession>, at: Instant): Double {
        val last = lastWakeBefore(sessions, at) ?: return 0.0
        val minutes = hoursBetween(last.end, at) * 60
        if (minutes < 0 || minutes > 90) return 0.0
        val strength = if (wokeMidCycle(last)) 0.22 else 0.12
        return strength * exp(-minutes / 25.0)
    }

    /** Energia estimada de 0 a 100. */
    fun energy(sessions: List<SleepSession>, at: Instant, zone: ZoneId): Int {
        val s = homeostatic(sessions, at)
        val homeo = ((S_EXHAUSTED - s) / (S_EXHAUSTED - S_RESTED)).coerceIn(0.0, 1.0)
        val c = circadian(at, zone, chronotypeShift(sessions, zone))
        val value = 0.86 * homeo + 0.22 * c - inertiaPenalty(sessions, at)
        return (value.coerceIn(0.0, 1.0) * 100).roundToInt()
    }

    data class Point(val at: Instant, val energy: Int)

    /** Curva de energia a partir de [from], assumindo que a pessoa continua acordada. */
    fun forecast(
        sessions: List<SleepSession>,
        from: Instant,
        zone: ZoneId,
        hours: Long = 24,
        stepMinutes: Long = 15,
    ): List<Point> = (0..hours * 60 / stepMinutes).map { i ->
        val t = from.plus(Duration.ofMinutes(i * stepMinutes))
        Point(t, energy(sessions, t, zone))
    }

    /** Dívida de sono (horas) nas noites registradas dos últimos 7 dias. */
    fun sleepDebtHours(sessions: List<SleepSession>, now: Instant, zone: ZoneId): Double {
        val weekAgo = now.minus(Duration.ofDays(7))
        val recent = sessions.filter { it.end.isAfter(weekAgo) && !it.end.isAfter(now) }
        // só dias com sono principal registrado geram "necessidade"; cochilos apenas abatem a dívida
        val days = recent.filterNot { it.isNap }.map { it.end.atZone(zone).toLocalDate() }.distinct().size
        if (days == 0) return 0.0
        val slept = recent.sumOf { it.hours }
        return (days * NEED_HOURS - slept).coerceAtLeast(0.0)
    }

    /** Último sono principal (não cochilo) terminado antes de [at]. */
    fun lastMainSleep(sessions: List<SleepSession>, at: Instant): SleepSession? =
        sessions.filter { !it.isNap && !it.end.isAfter(at) }.maxByOrNull { it.end }

    fun level(energy: Int): String = when {
        energy >= 75 -> "Carregado"
        energy >= 55 -> "Ok"
        energy >= 35 -> "Cansado"
        else -> "No limite"
    }
}
