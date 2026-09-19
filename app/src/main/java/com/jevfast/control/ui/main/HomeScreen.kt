package com.jevfast.control.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jevfast.control.Keys
import com.jevfast.control.a11y.ControlService
import com.jevfast.control.agent.RunState
import com.jevfast.control.theme.Accent
import com.jevfast.control.theme.AccentDim
import com.jevfast.control.theme.Border
import com.jevfast.control.theme.ErrorRed
import com.jevfast.control.theme.SurfaceCard
import com.jevfast.control.theme.SurfaceRaised
import com.jevfast.control.theme.TextMuted
import com.jevfast.control.theme.TextPrimary
import com.jevfast.control.theme.WarnAmber

private fun accessibilityEnabled(ctx: Context): Boolean {
    val cn = ComponentName(ctx, ControlService::class.java)
    val enabled = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any {
        it.equals(cn.flattenToString(), ignoreCase = true) ||
            it.equals(cn.flattenToShortString(), ignoreCase = true)
    }
}

@Composable
fun HomeScreen(vm: AgentViewModel = viewModel()) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val log by vm.log.collectAsState()
    val elapsed by vm.elapsedMs.collectAsState()

    var task by remember { mutableStateOf("") }
    var texts by remember { mutableStateOf("") }
    var a11yOn by remember { mutableStateOf(accessibilityEnabled(ctx)) }
    var showSettings by remember { mutableStateOf(false) }
    var needsTextInput by remember { mutableStateOf("") }

    // Re-check accessibility state when returning from Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) a11yOn = accessibilityEnabled(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val running = state is RunState.Running || state is RunState.NeedsText

    val snackbarHostState = remember { SnackbarHostState() }
    val snackMsg by vm.snackbar.collectAsState()
    LaunchedEffect(snackMsg) {
        snackMsg?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 12.dp)
    ) {
        // ---- header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Text("JevFast", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            StatusChip(
                ok = a11yOn,
                label = if (a11yOn) "service on" else "service off"
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextMuted)
            }
        }

        Spacer(Modifier.height(14.dp))

        if (!a11yOn) {
            Banner(
                color = WarnAmber,
                title = "Accessibility service required",
                body = "JevFast controls the phone through an accessibility service — enable it once."
            ) {
                TextButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open Accessibility Settings", color = WarnAmber) }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (Keys.typeSafe(ctx).isBlank()) {
            Banner(
                color = ErrorRed,
                title = "TypeSafe API key missing",
                body = "Jev decisions need a TYPESAFE_API_KEY — add it in settings."
            ) {
                TextButton(onClick = { showSettings = true }) { Text("Open Settings", color = ErrorRed) }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- task input ----
        Column(
            Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(16.dp))
                .border(1.dp, Border, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            OutlinedTextField(
                value = task,
                onValueChange = { task = it },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                placeholder = { Text("e.g. Open YouTube, search carryminati and subscribe", color = TextMuted) },
                label = { Text("Task") },
                colors = fieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = texts,
                onValueChange = { texts = it },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Optional — texts to enter, comma-separated", color = TextMuted) },
                label = { Text("Texts") },
                colors = fieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (running) vm.stop()
                        else vm.start(task, texts.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                    },
                    enabled = task.isNotBlank() || running,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) ErrorRed else AccentDim,
                        contentColor = TextPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) "Stop" else "Run")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stateLabel(state) + " · ${elapsed}ms",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // ---- needs-text prompt ----
        (state as? RunState.NeedsText)?.let { nt ->
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceRaised, RoundedCornerShape(12.dp))
                    .border(1.dp, WarnAmber, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text("Text needed for ${nt.fieldLabel}", color = WarnAmber, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = needsTextInput,
                        onValueChange = { needsTextInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Type the text to enter", color = TextMuted) },
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        vm.submitText(needsTextInput)
                        needsTextInput = ""
                    }) { Text("Send") }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- live log ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Run log", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(
                        ClipData.newPlainText(
                            "jevfast log",
                            log.joinToString("\n") { "${it.n}. ${it.text}" }
                        )
                    )
                    Toast.makeText(ctx, "Log copied", Toast.LENGTH_SHORT).show()
                },
                enabled = log.isNotEmpty(),
                modifier = Modifier.size(26.dp),
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy log",
                    tint = if (log.isEmpty()) Border else TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val listState = rememberLazyListState()
        LaunchedEffect(log.size) {
            if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(12.dp))
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (log.isEmpty()) {
                item {
                    Text(
                        "Steps appear here — Jev decides, code executes.",
                        color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
            items(log) { entry ->
                Row {
                    Text(
                        "${entry.n}.",
                        color = Accent,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(26.dp)
                    )
                    Text(
                        entry.text,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // ---- footer status ----
        (state as? RunState.Done)?.let {
            Footer("Goal achieved in ${it.steps} steps.", Accent)
        }
        (state as? RunState.Blocked)?.let {
            Footer("Stopped: ${it.reason}", WarnAmber)
        }
        (state as? RunState.Error)?.let {
            Footer("Error: ${it.message}", ErrorRed)
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
    )
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
}

@Composable
private fun StatusChip(ok: Boolean, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(SurfaceRaised, RoundedCornerShape(20.dp))
            .border(1.dp, if (ok) AccentDim else ErrorRed, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) Accent else ErrorRed,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (ok) Accent else ErrorRed, fontSize = 11.sp)
    }
}

@Composable
private fun Banner(color: androidx.compose.ui.graphics.Color, title: String, body: String, action: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SurfaceRaised, RoundedCornerShape(12.dp))
            .border(1.dp, color, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(title, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = TextMuted, fontSize = 12.sp)
        action()
    }
}

@Composable
private fun Footer(text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceRaised, RoundedCornerShape(10.dp))
            .border(1.dp, color, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Border,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextMuted,
    cursorColor = Accent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
)

private fun stateLabel(s: RunState): String = when (s) {
    RunState.Idle -> "idle"
    RunState.Running -> "running"
    is RunState.NeedsText -> "needs text"
    is RunState.Done -> "done"
    is RunState.Blocked -> "blocked"
    is RunState.Error -> "error"
}
