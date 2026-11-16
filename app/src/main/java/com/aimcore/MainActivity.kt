package com.aimcore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Bg = Color(0xFF080808)
private val Surface = Color(0xFF171010)
private val Crimson = Color(0xFFFF3434)
private val Muted = Color(0xFFB9A8A8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AimcoreApp() }
    }
}

private enum class Tab(val title: String) { TRAIN("Train"), SENS("Sensitivity"), PROFILES("Profiles") }

@Composable
private fun AimcoreApp() {
    var selected by remember { mutableStateOf(Tab.TRAIN) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Crimson, background = Bg, surface = Surface)) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                Column(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("AIMCORE", color = Crimson, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("AIM TRAINING  /  SENSITIVITY LAB", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Surface) {
                    Tab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab,
                            onClick = { selected = tab },
                            icon = { Icon(when (tab) { Tab.TRAIN -> Icons.Default.GpsFixed; Tab.SENS -> Icons.Default.Tune; Tab.PROFILES -> Icons.Default.Games }, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Crimson, selectedTextColor = Crimson, indicatorColor = Color(0xFF3A1515), unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selected) {
                    Tab.TRAIN -> TrainingScreen()
                    Tab.SENS -> SensitivityScreen()
                    Tab.PROFILES -> ProfilesScreen()
                }
            }
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(value, color = Crimson, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrainingScreen() {
    var score by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(30) }
    var target by remember { mutableStateOf(Offset(.5f, .5f)) }
    var message by remember { mutableStateOf("Start a session, then tap the target.") }

    LaunchedEffect(running) {
        while (running && seconds > 0) {
            delay(1000)
            seconds--
        }
        if (seconds == 0) {
            running = false
            message = "Session complete. Score: $score / $taps taps."
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("Aim Trainer", "Build precision and consistency with short drills.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Metric("SCORE", score.toString(), Modifier.weight(1f))
                Metric("TAPS", taps.toString(), Modifier.weight(1f))
                Metric("TIME", "${seconds}s", Modifier.weight(1f))
            }
        }
        item {
            SurfaceCard {
                Text("TARGET PRACTICE", color = Crimson, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Canvas(
                    Modifier.fillMaxWidth().height(310.dp).background(Color(0xFF0C0C0C), RoundedCornerShape(14.dp))
                        .pointerInput(running, target) {
                            detectTapGestures { point ->
                                if (running) {
                                    taps++
                                    val center = Offset(target.x * size.width, target.y * size.height)
                                    if ((point - center).getDistance() <= 34.dp.toPx()) {
                                        score++
                                        target = Offset(Random.nextFloat().coerceIn(.12f, .88f), Random.nextFloat().coerceIn(.12f, .88f))
                                        message = "Hit! Keep going."
                                    } else message = "Miss. Keep tracking the target."
                                }
                            }
                        }
                ) {
                    if (running) {
                        val center = Offset(target.x * size.width, target.y * size.height)
                        drawCircle(Crimson.copy(alpha = .18f), 52.dp.toPx(), center)
                        drawCircle(Crimson, 29.dp.toPx(), center)
                        drawCircle(Color.White, 15.dp.toPx(), center)
                        drawCircle(Crimson, 5.dp.toPx(), center)
                    } else {
                        drawCircle(Muted.copy(alpha = .35f), 4.dp.toPx(), Offset(size.width / 2, size.height / 2))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (running) {
                            running = false
                            message = "Session paused."
                        } else {
                            score = 0; taps = 0; seconds = 30; target = Offset(.5f, .5f)
                            message = "Session running. Tap the target!"
                            running = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Crimson),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (running) "PAUSE SESSION" else "START 30-SECOND SESSION", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                Text(message, color = Muted, fontSize = 11.sp)
            }
        }
        item { Text("Practice happens inside AIMCORE. The app does not control or modify other games.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun SensitivityScreen() {
    var dpi by remember { mutableFloatStateOf(800f) }
    var sens by remember { mutableFloatStateOf(1f) }
    var scale by remember { mutableFloatStateOf(1f) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Heading("Sensitivity Lab", "Experiment with and record reference values.") }
        item {
            SurfaceCard {
                Text("DPI / POINTER DPI", color = Muted, fontSize = 11.sp)
                Text(dpi.toInt().toString(), color = Crimson, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Slider(value = dpi, onValueChange = { dpi = it }, valueRange = 100f..3200f)
                Spacer(Modifier.height(8.dp))
                Text("SENSITIVITY", color = Muted, fontSize = 11.sp)
                Text("%.2f".format(sens), color = Crimson, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Slider(value = sens, onValueChange = { sens = it }, valueRange = .1f..10f)
                Spacer(Modifier.height(8.dp))
                Text("REFERENCE MULTIPLIER", color = Muted, fontSize = 11.sp)
                Text("%.2f".format(scale), color = Crimson, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Slider(value = scale, onValueChange = { scale = it }, valueRange = .1f..3f)
            }
        }
        item {
            SurfaceCard {
                Text("COMBINED REFERENCE", color = Muted, fontSize = 11.sp)
                Text("%.3f".format(sens * scale), color = Crimson, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Sensitivity × multiplier. Games use different scales, so this is not a universal in-game conversion.", color = Muted, fontSize = 11.sp)
            }
        }
        item { OutlinedButton(onClick = { dpi = 800f; sens = 1f; scale = 1f }, modifier = Modifier.fillMaxWidth()) { Text("RESET VALUES", color = Crimson) } }
    }
}

@Composable
private fun ProfilesScreen() {
    val games = listOf(
        "Roblox" to "Keep a separate record for each experience.",
        "Call of Duty: Mobile" to "Record camera and firing sensitivity.",
        "PUBG Mobile" to "Record camera, ADS and gyro preferences.",
        "Minecraft" to "Record touch-look sensitivity."
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Heading("Game Profiles", "Starter reference profiles for your games.") }
        items(games) { (name, description) ->
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Games, contentDescription = null, tint = Crimson)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(description, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        item { Text("Phase 1 profiles are starter examples. Editable saved profiles are planned for Phase 2.", color = Muted, fontSize = 11.sp) }
    }
}
