package com.felipeb.nap.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipeb.nap.model.SleepModel
import com.felipeb.nap.model.SleepSession
import java.time.Duration
import java.time.Instant

@Composable
fun NowScreen(
    sessions: List<SleepSession>,
    now: Instant,
    onLogClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Nap", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        val lastSleep = sessions.filter { !it.end.isAfter(now) }.maxByOrNull { it.end }
        if (lastSleep == null) {
            EmptyState(onLogClick)
            return@Column
        }

        val energy = remember(sessions, now) { SleepModel.energy(sessions, now, zone) }
        val curve = remember(sessions, now) { SleepModel.forecast(sessions, now, zone) }
        val debt = remember(sessions, now) { SleepModel.sleepDebtHours(sessions, now, zone) }
        val mainSleep = SleepModel.lastMainSleep(sessions, now) ?: lastSleep
        val awake = Duration.between(lastSleep.end, now)

        EnergyGauge(energy)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Acordado há", awake.hoursMinutes(), Modifier.weight(1f))
            StatCard(if (mainSleep.isNap) "Último sono" else "Última noite", mainSleep.duration.hoursMinutes(), Modifier.weight(1f))
            StatCard("Dívida 7d", if (debt < 0.25) "0h" else debt.hoursMinutes(), Modifier.weight(1f))
        }

        SectionCard("Próximas 24h (se continuar acordado)") {
            EnergyChart(curve, Modifier.fillMaxWidth().height(180.dp))
        }

        SectionCard("O que o modelo diz") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                insights(mainSleep, lastSleep, awake, energy, curve, debt).forEach { Insight(it) }
            }
        }

        Text(
            "Estimativa baseada no modelo de dois processos (Borbély): pressão de sono + ritmo circadiano. " +
                "Não é diagnóstico médico.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun insights(
    main: SleepSession,
    last: SleepSession,
    awake: Duration,
    energy: Int,
    curve: List<SleepModel.Point>,
    debt: Double,
): List<String> = buildList {
    add("Você dormiu ${main.duration.hoursMinutes()} ≈ ${"%.1f".format(main.cycles)} ciclos de 90 min.")
    if (last != main) {
        val minutes = last.duration.toMinutes()
        add("Cochilo de ${last.duration.hoursMinutes()} às ${last.start.hm()}: aliviou um pouco a pressão de sono." +
            if (minutes > 30) " Cochilos acima de 30 min podem deixar grogue e atrapalhar a noite." else "")
    }
    if (awake.toMinutes() < 120 && SleepModel.wokeMidCycle(last)) {
        add("Acordou provavelmente no meio de um ciclo: a primeira hora tende a ser mais pesada (inércia do sono).")
    }
    if (awake.toHours() >= 20) {
        add("Mais de 20h acordado: seu desempenho equivale a estar levemente alcoolizado. Evite dirigir.")
    }

    val crash = curve.drop(1).firstOrNull { it.energy < 35 }
    if (energy >= 35 && crash != null) {
        add("Sua energia deve cair para a zona crítica por volta das ${crash.at.hm()}.")
        val caffeineCutoff = crash.at.minus(Duration.ofHours(6))
        if (caffeineCutoff.isAfter(curve.first().at)) {
            add("Se quiser dormir bem, evite cafeína depois das ${caffeineCutoff.hm()}.")
        }
    }

    val dip = curve.take(12 * 4).filter { it.at.atZone(zone).hour in 12..16 }.minByOrNull { it.energy }
    if (dip != null && dip.energy < 60) {
        add("Janela boa para um cochilo de 20 min: perto das ${dip.at.hm()} (queda pós-almoço).")
    }

    val rebound = curve.drop(1).firstOrNull { it.energy > energy + 10 }
    if (energy < 35 && rebound != null) {
        add("Você está no fundo do poço, mas o ritmo circadiano deve te dar um fôlego por volta das ${rebound.at.hm()}. " +
            "É esse \"segundo gás\" que engana quando se vira a noite.")
    }

    if (debt >= 5) add("Dívida de sono alta (${debt.hoursMinutes()}). Uma ou duas noites mais longas ajudam a recuperar.")
}

@Composable
private fun EmptyState(onLogClick: () -> Unit) {
    SectionCard("Bem-vindo") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Registre a que horas você dormiu e acordou para ver seu saldo de energia.")
            Button(onClick = onLogClick) { Text("Registrar sono") }
        }
    }
}

@Composable
private fun EnergyGauge(energy: Int) {
    val color = energyColor(energy)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * energy / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$energy%", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = color)
            Text(SleepModel.level(energy), style = MaterialTheme.typography.titleMedium)
            Text(
                "saldo de energia",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EnergyChart(points: List<SleepModel.Point>, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.primary
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = w * i / (points.size - 1)
            fun y(e: Int) = h * (1 - e / 100f)

            // zona crítica (< 35%)
            drawRect(EnergyCritical.copy(alpha = 0.08f), Offset(0f, y(35)), Size(w, h - y(35)))
            listOf(25, 50, 75).forEach { level ->
                drawLine(grid, Offset(0f, y(level)), Offset(w, y(level)), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }

            val path = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.energy)) else lineTo(x(i), y(p.energy)) }
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0f))))
            drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(energyColor(points.first().energy), 6.dp.toPx(), Offset(0f, y(points.first().energy)))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // marca a cada 6h
            val step = (points.size - 1) / 4
            (0..4).forEach { i ->
                Text(
                    if (i == 0) "agora" else points[i * step].at.hm(),
                    style = MaterialTheme.typography.labelSmall,
                    color = label,
                )
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Insight(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("•", color = Star)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
