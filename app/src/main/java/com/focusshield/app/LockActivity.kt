@file:OptIn(ExperimentalMaterial3Api::class)

package com.focusshield.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg")
        if (pkg == null) {
            finish()
            return
        }
        val msg = intent.getStringExtra("msg")
        val store = Store(this)
        setContent {
            AppTheme {
                LockScreen(
                    pkg = pkg,
                    msg = msg,
                    store = store,
                    onHome = { goHome() },
                    onOpen = { openApp(pkg) }
                )
            }
        }
    }

    private fun goHome() {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        finish()
    }

    private fun openApp(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i)
        finish()
    }
}

@Composable
fun LockScreen(
    pkg: String,
    msg: String?,
    store: Store,
    onHome: () -> Unit,
    onOpen: () -> Unit
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val label = remember { appLabel(pm, pkg) }
    val icon = remember { appIcon(pm, pkg, 128) }
    val reasons = listOf("Reply to a message", "Learning / tutorial", "Work", "Just bored")
    val minuteChoices = listOf(1, 5, 10, 15)

    var reason by remember { mutableStateOf<String?>(null) }
    var mins by remember { mutableStateOf(5) }
    val opens = remember { store.opensToday() }
    var wait by remember { mutableStateOf(minOf(60, 5 * (opens + 1))) }

    LaunchedEffect(Unit) {
        while (wait > 0) {
            delay(1000)
            wait -= 1
        }
    }
    BackHandler { onHome() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "$label is locked",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (msg != null) {
            Text(msg, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Go home") }
        } else {
            Text(
                "Opened $opens time(s) today. Why do you need it?",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            reasons.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { r ->
                        FilterChip(
                            selected = reason == r,
                            onClick = { reason = r },
                            label = { Text(r) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("For how long?", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                minuteChoices.forEach { m ->
                    FilterChip(
                        selected = mins == m,
                        onClick = { mins = m },
                        label = { Text("$m min") }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            val ready = wait == 0 && reason != null
            Button(
                onClick = {
                    store.setUntil(pkg, System.currentTimeMillis() + mins * 60_000L)
                    store.addOpen()
                    onOpen()
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        wait > 0 -> "Wait ${wait}s"
                        reason == null -> "Pick a reason"
                        else -> "Open for $mins min"
                    }
                )
            }
            TextButton(onClick = onHome) { Text("Not now") }
        }
    }
}
