package com.felipeb.nap.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.felipeb.nap.data.SleepRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

private enum class Tab(val label: String, val icon: ImageVector) {
    Now("Agora", Icons.Filled.Home),
    Log("Registrar", Icons.Filled.Add),
    Cycles("Ciclos", Icons.Filled.DateRange),
}

@Composable
fun NapApp(repository: SleepRepository) {
    NapTheme {
        val sessions by repository.sessions.collectAsState(initial = null)
        val scope = rememberCoroutineScope()
        var tab by rememberSaveable { mutableStateOf(Tab.Now) }

        // relógio que atualiza a tela "Agora" a cada minuto
        var now by remember { mutableStateOf(Instant.now()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                now = Instant.now()
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t; now = Instant.now() },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val list = sessions ?: return@Scaffold
            val modifier = Modifier.padding(padding)
            when (tab) {
                Tab.Now -> NowScreen(list, now, onLogClick = { tab = Tab.Log }, modifier = modifier)
                Tab.Log -> LogScreen(
                    sessions = list,
                    onSave = {
                        scope.launch { repository.add(it) }
                        now = Instant.now()
                        tab = Tab.Now
                    },
                    onDelete = { scope.launch { repository.remove(it) } },
                    modifier = modifier,
                )
                Tab.Cycles -> CyclesScreen(modifier)
            }
        }
    }
}
