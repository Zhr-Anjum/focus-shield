@file:OptIn(ExperimentalMaterial3Api::class)

package com.focusshield.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var serviceOn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent { AppTheme { MainScreen(store, serviceOn) } }
    }

    override fun onResume() {
        super.onResume()
        serviceOn = isServiceOn()
    }

    private fun isServiceOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(packageName) && enabled.contains("BlockerService")
    }
}

@Composable
fun MainScreen(store: Store, serviceOn: Boolean) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(
        "Home" to Icons.Filled.Home,
        "Apps" to Icons.Filled.Menu,
        "Schedule" to Icons.Filled.DateRange,
        "Strict" to Icons.Filled.Lock
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.second, contentDescription = null) },
                        label = { Text(t.first) }
                    )
                }
            }
        }
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (tab) {
                0 -> HomeTab(store, serviceOn)
                1 -> AppsTab(store)
                2 -> ScheduleTab(store)
                else -> StrictTab(store)
            }
        }
    }
}

@Composable
fun HomeTab(store: Store, serviceOn: Boolean) {
    val ctx = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
        Text(
            "Focus Shield",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "Free for everyone. No ads.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (serviceOn) {
            Section(color = Color(0xFFD9F5EE)) {
                Text("Protection is ON", fontWeight = FontWeight.Bold)
                Text("Apps you locked will ask for a reason and a time limit before opening.")
            }
        } else {
            Section(color = Color(0xFFFFE9D6)) {
                Text("Protection is OFF", fontWeight = FontWeight.Bold)
                Text(
                    "Turn on Focus Shield under Installed apps in Accessibility settings. " +
                        "If the switch is greyed out, open App info, tap the three dots " +
                        "and choose Allow restricted settings."
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open Accessibility settings") }
                OutlinedButton(onClick = {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    i.data = Uri.parse("package:" + ctx.packageName)
                    ctx.startActivity(i)
                }) { Text("Open App info (restricted settings)") }
            }
        }

        Section {
            Text("Today", fontWeight = FontWeight.Bold)
            Text("Apps locked: ${store.locked.size}")
            Text("Times you opened a locked app: ${store.opensToday()}")
        }

        Section(color = Color(0xFFFFF1F5)) {
            Text("\u2665 Support Focus Shield", fontWeight = FontWeight.Bold)
            Text(
                "Everything is free. If it helps you stay focused, you can donate any amount " +
                    "in INR, USD or any currency to help it grow."
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL)))
            }) { Text("Donate") }
        }
    }
}

class AppInfo(val label: String, val pkg: String)

@Composable
fun AppsTab(store: Store) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val locked = remember { mutableStateListOf<String>().apply { addAll(store.locked) } }
    val trading = remember { mutableStateListOf<String>().apply { addAll(store.trading) } }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }

    fun strictToast() {
        Toast.makeText(
            ctx,
            "Strict mode is on. Request turn-off in the Strict tab.",
            Toast.LENGTH_LONG
        ).show()
    }

    val shown = apps.filter { it.label.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Pick apps to lock",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp)
        )
        Text(
            "Locked: ${locked.size}. Tick Trading app for broker or chart apps.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { app ->
                val icon = remember(app.pkg) { appIcon(pm, app.pkg, 96) }
                val isLocked = app.pkg in locked
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(app.label)
                        if (isLocked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = app.pkg in trading,
                                    onCheckedChange = { c ->
                                        if (c) {
                                            trading.add(app.pkg)
                                        } else if (store.canEdit()) {
                                            trading.remove(app.pkg)
                                        } else {
                                            strictToast()
                                        }
                                        store.trading = trading.toSet()
                                    }
                                )
                                Text("Trading app", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Switch(
                        checked = isLocked,
                        onCheckedChange = { on ->
                            if (on) {
                                locked.add(app.pkg)
                            } else if (store.canEdit()) {
                                locked.remove(app.pkg)
                                trading.remove(app.pkg)
                            } else {
                                strictToast()
                            }
                            store.locked = locked.toSet()
                            store.trading = trading.toSet()
                        }
                    )
                }
            }
        }
    }
}

fun pickTime(ctx: Context, cur: Int, set: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m) }, cur / 60, cur % 60, true).show()
}

@Composable
fun ScheduleCard(
    title: String,
    desc: String,
    on: Boolean,
    start: Int,
    end: Int,
    editable: Boolean,
    onOn: (Boolean) -> Unit,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit
) {
    val ctx = LocalContext.current
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = on, onCheckedChange = onOn, enabled = editable)
        }
        Text(desc, style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("From")
            OutlinedButton(enabled = editable, onClick = { pickTime(ctx, start, onStart) }) {
                Text(fmtTime(start))
            }
            Text("to")
            OutlinedButton(enabled = editable, onClick = { pickTime(ctx, end, onEnd) }) {
                Text(fmtTime(end))
            }
        }
    }
}

@Composable
fun ScheduleTab(store: Store) {
    val editable = remember { store.canEdit() }
    var tOn by remember { mutableStateOf(store.tradeOn) }
    var tS by remember { mutableStateOf(store.tradeStart) }
    var tE by remember { mutableStateOf(store.tradeEnd) }
    var sOn by remember { mutableStateOf(store.sleepOn) }
    var sS by remember { mutableStateOf(store.sleepStart) }
    var sE by remember { mutableStateOf(store.sleepEnd) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        Text(
            "Schedule",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (!editable) {
            Text(
                "Strict mode is on. Schedules can't be changed until the turn-off wait is over.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
        ScheduleCard(
            "Trading window",
            "Apps marked as Trading app open freely only inside this window. Outside it they are hard-locked.",
            tOn, tS, tE, editable,
            onOn = { tOn = it; store.tradeOn = it },
            onStart = { tS = it; store.tradeStart = it },
            onEnd = { tE = it; store.tradeEnd = it }
        )
        ScheduleCard(
            "Sleep lock",
            "All locked apps are hard-blocked during this time, no unlock option.",
            sOn, sS, sE, editable,
            onOn = { sOn = it; store.sleepOn = it },
            onStart = { sS = it; store.sleepStart = it },
            onEnd = { sE = it; store.sleepEnd = it }
        )
    }
}

@Composable
fun StrictTab(store: Store) {
    var on by remember { mutableStateOf(store.strictOn) }
    var req by remember { mutableStateOf(store.disableRequestedAt) }
    val waited = if (req > 0L) System.currentTimeMillis() - req else 0L
    val ready = req > 0L && waited >= DAY_MS

    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        Text(
            "Strict mode",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Section {
            Text(
                "With Strict mode on you can't unlock apps from the list or change schedules " +
                    "in a weak moment. To turn it off you must request it and wait 24 hours."
            )
            Spacer(Modifier.height(12.dp))
            if (!on) {
                Button(onClick = {
                    store.strictOn = true
                    store.disableRequestedAt = 0L
                    on = true
                    req = 0L
                }) { Text("Turn on Strict mode") }
            } else {
                Text("Strict mode is ON", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (req == 0L) {
                    OutlinedButton(onClick = {
                        val t = System.currentTimeMillis()
                        store.disableRequestedAt = t
                        req = t
                    }) { Text("Request turn-off (24 hour wait)") }
                } else if (!ready) {
                    val left = DAY_MS - waited
                    Text("Turn-off unlocks in ${left / 3_600_000}h ${(left / 60_000) % 60}m")
                    TextButton(onClick = {
                        store.disableRequestedAt = 0L
                        req = 0L
                    }) { Text("Cancel request") }
                } else {
                    Button(onClick = {
                        store.strictOn = false
                        store.disableRequestedAt = 0L
                        on = false
                        req = 0L
                    }) { Text("Turn off Strict mode now") }
                }
            }
        }
        Section {
            Text(
                "Note: Android still lets you switch Focus Shield off in Accessibility settings " +
                    "or uninstall it. Strict mode protects the app's own settings, not the phone's.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
