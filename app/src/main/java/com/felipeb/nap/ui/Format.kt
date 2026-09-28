package com.felipeb.nap.ui

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val zone: ZoneId get() = ZoneId.systemDefault()

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayMonth = DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.forLanguageTag("pt-BR"))

fun Instant.hm(): String = atZone(zone).format(hm)
fun LocalTime.hm(): String = format(hm)
fun Instant.dayLabel(): String = atZone(zone).toLocalDate().dayLabel()
fun java.time.LocalDate.dayLabel(): String = format(dayMonth).replace(".", "").replaceFirstChar { it.uppercase() }

fun Duration.hoursMinutes(): String {
    val h = toHours()
    val m = toMinutes() % 60
    return if (m == 0L) "${h}h" else "${h}h${"%02d".format(m)}"
}

fun Double.hoursMinutes(): String = Duration.ofMinutes((this * 60).toLong()).hoursMinutes()
