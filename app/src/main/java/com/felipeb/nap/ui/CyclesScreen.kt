package com.felipeb.nap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.felipeb.nap.model.SleepModel.CYCLE_MINUTES
import com.felipeb.nap.model.SleepModel.FALL_ASLEEP_MINUTES
import java.time.LocalTime

private val cycleOptions = listOf(6, 5, 4)

@Composable
fun CyclesScreen(modifier: Modifier = Modifier) {
    var wakeTarget by remember { mutableStateOf(LocalTime.of(7, 0)) }
    val now = LocalTime.now()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ciclos de sono", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        SectionCard("Se eu dormir agora, acordar às…") {
            cycleOptions.forEach { n ->
                val wake = now.plusMinutes(FALL_ASLEEP_MINUTES + n * CYCLE_MINUTES)
                CycleRow(wake, n)
            }
        }

        SectionCard("Preciso acordar às…") {
            TimeField("Acordar às", wakeTarget, { wakeTarget = it }, Modifier.fillMaxWidth())
            Text("Deite-se às:", style = MaterialTheme.typography.bodyMedium)
            cycleOptions.forEach { n ->
                val bed = wakeTarget.minusMinutes(FALL_ASLEEP_MINUTES + n * CYCLE_MINUTES)
                CycleRow(bed, n)
            }
        }

        Text(
            "Considera ~${FALL_ASLEEP_MINUTES} min para pegar no sono e ciclos de ~${CYCLE_MINUTES} min. " +
                "Acordar no fim de um ciclo reduz a sensação de \"atropelado\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CycleRow(time: LocalTime, cycles: Int) {
    val hours = cycles * CYCLE_MINUTES / 60.0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(time.hm(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            color = if (cycles >= 5) EnergyHigh else EnergyLow)
        Text(
            "$cycles ciclos · ${hours.hoursMinutes()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
