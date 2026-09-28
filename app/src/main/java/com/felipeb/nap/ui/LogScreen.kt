package com.felipeb.nap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.felipeb.nap.model.SleepModel
import com.felipeb.nap.model.SleepSession
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun LogScreen(
    sessions: List<SleepSession>,
    onSave: (SleepSession) -> Unit,
    onDelete: (SleepSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bedDate by remember { mutableStateOf(LocalDate.now().minusDays(1)) }
    var bed by remember { mutableStateOf(LocalTime.of(23, 0)) }
    var wake by remember { mutableStateOf(LocalTime.of(7, 0)) }
    var pickingDate by remember { mutableStateOf(false) }

    // se o horário de acordar é "antes" do de dormir, acordou no dia seguinte
    val bedAt = bedDate.atTime(bed).atZone(zone).toInstant()
    val wakeDate = if (wake.isAfter(bed)) bedDate else bedDate.plusDays(1)
    val wakeAt = wakeDate.atTime(wake).atZone(zone).toInstant()
    val preview = SleepSession(bedAt, wakeAt)
    val error = when {
        wakeAt.isAfter(Instant.now()) -> "O horário de acordar está no futuro."
        preview.duration > Duration.ofHours(16) -> "Mais de 16h de sono? Confira os horários."
        preview.duration < Duration.ofMinutes(10) -> "Sessão curta demais."
        sessions.any { it.start < wakeAt && bedAt < it.end } -> "Já existe um registro nesse horário."
        else -> null
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Registrar sono", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            SectionCard("Dia em que foi dormir") {
                val today = LocalDate.now()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(bedDate == today.minusDays(1), { bedDate = today.minusDays(1) }, { Text("Ontem") })
                    FilterChip(bedDate == today, { bedDate = today }, { Text("Hoje") })
                    val other = bedDate != today && bedDate != today.minusDays(1)
                    FilterChip(
                        selected = other,
                        onClick = { pickingDate = true },
                        label = { Text(if (other) bedDate.dayLabel() else "Outro dia…") },
                        leadingIcon = { Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp)) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField("Dormi às", bed, { bed = it }, Modifier.weight(1f))
                    TimeField("Acordei às", wake, { wake = it }, Modifier.weight(1f))
                }
                TextButton(onClick = {
                    val now = LocalTime.now().withSecond(0).withNano(0)
                    wake = now
                    bedDate = if (bed.isBefore(now)) today else today.minusDays(1)
                }) { Text("Acordei agora") }

                Text(
                    "${bedAt.dayLabel()} ${bed.hm()} → ${wakeAt.dayLabel()} ${wake.hm()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${preview.duration.hoursMinutes()} de ${if (preview.isNap) "cochilo" else "sono"} · " +
                        "${"%.1f".format(preview.cycles)} ciclos",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (error != null) {
                    Text(error, color = EnergyCritical, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { onSave(preview) },
                    enabled = error == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salvar") }
                Text(
                    "Cochilos também contam: registre cada um separado, mesmo no mesmo dia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (sessions.isNotEmpty()) {
            item {
                Text(
                    "Histórico",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(sessions.sortedByDescending { it.end }, key = { it.start.toEpochMilli() }) { s ->
                HistoryRow(s, onDelete = { onDelete(s) })
            }
        }
    }

    if (pickingDate) {
        DatePickerDialog(bedDate, onDismiss = { pickingDate = false }, onConfirm = { bedDate = it; pickingDate = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    // o DatePicker do Material trabalha com meia-noite em UTC
    val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
        },
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state, showModeToggle = false)
    }
}

@Composable
private fun HistoryRow(session: SleepSession, onDelete: () -> Unit) {
    val short = !session.isNap && session.hours < SleepModel.NEED_HOURS - 1
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (session.isNap) "${session.start.dayLabel()} · cochilo" else session.end.dayLabel(),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${session.start.hm()} → ${session.end.hm()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    session.duration.hoursMinutes(),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        session.isNap -> Moon
                        short -> EnergyLow
                        else -> EnergyHigh
                    },
                )
                Text(
                    "${"%.1f".format(session.cycles)} ciclos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Apagar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
