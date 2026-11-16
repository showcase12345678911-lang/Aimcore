package com.aimcore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

private val Bg = Color(0xFF080808)
private val Panel = Color(0xFF171010)
private val Crimson = Color(0xFFFF3434)
private val Muted = Color(0xFFB9A8A8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AimcoreApp(AimcoreStore(applicationContext)) }
    }
}

private enum class MainTab(val label: String) {
    TRAIN("Train"), RANGE("3D Range"), SENS("Sensitivity"), PROFILES("Profiles")
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AimcoreApp(store: AimcoreStore) {
    var selected by remember { mutableStateOf(MainTab.TRAIN) }
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
        primary = Crimson, background = Bg, surface = Panel
    )) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                Column(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 18.dp, vertical = 13.dp)) {
                    Text("AIMCORE", color = Crimson, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("COUNTERFLAME TRAINING LAB", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    MainTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab,
                            onClick = { selected = tab },
                            icon = {
                                androidx.compose.material3.Icon(
                                    when (tab) {
                                        MainTab.TRAIN -> Icons.Default.GpsFixed
                                        MainTab.RANGE -> Icons.Default.Visibility
                                        MainTab.SENS -> Icons.Default.Tune
                                        MainTab.PROFILES -> Icons.Default.Games
                                    }, contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Crimson, selectedTextColor = Crimson,
                                indicatorColor = Color(0xFF3A1515),
                                unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selected) {
                    MainTab.TRAIN -> BasicTrainingScreen()
                    MainTab.RANGE -> RangeScreen(store)
                    MainTab.SENS -> SensitivityScreen(store)
                    MainTab.PROFILES -> ProfilesScreen(store)
                }
            }
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(15.dp), content = content)
    }
}

@Composable
private fun BasicTrainingScreen() {
    var score by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(30) }
    val positions = remember { mutableListOf(0.5f to 0.5f, 0.25f to 0.3f, 0.75f to 0.65f, 0.4f to 0.8f) }
    androidx.compose.runtime.LaunchedEffect(running) {
        while (running && seconds > 0) { delay(1000); seconds-- }
        if (seconds == 0) running = false
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Heading("Tap Trainer", "Quick warm-up and precision practice.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("SCORE", score.toString(), Modifier.weight(1f))
                MetricCard("TAPS", taps.toString(), Modifier.weight(1f))
                MetricCard("TIME", "${seconds}s", Modifier.weight(1f))
            }
        }
        item {
            SurfaceCard {
                Canvas(
                    Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF0D0D0D), RoundedCornerShape(13.dp))
                        .pointerInput(running, target) {
                            detectTapGestures { point ->
                                if (running) {
                                    taps++
                                    val p = positions[target]
                                    val center = Offset(size.width * p.first, size.height * p.second)
                                    if ((point - center).getDistance() <= 32.dp.toPx()) {
                                        score++
                                        target = Random.nextInt(positions.size)
                                    }
                                }
                            }
                        }
                ) {
                    val p = positions[target]
                    val center = androidx.compose.ui.geometry.Offset(size.width * p.first, size.height * p.second)
                    drawCircle(Crimson.copy(alpha = 0.2f), 47.dp.toPx(), center)
                    drawCircle(Crimson, 27.dp.toPx(), center)
                    drawCircle(Color.White, 12.dp.toPx(), center)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (running) running = false else { score = 0; taps = 0; seconds = 30; target = 0; running = true }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Crimson)) {
                    Text(if (running) "PAUSE" else "START 30-SECOND TRAINING")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(11.dp)) {
            Text(label, color = Muted, fontSize = 9.sp)
            Text(value, color = Crimson, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RangeScreen(store: AimcoreStore) {
    val context = LocalContext.current
    var rangeView by remember { mutableStateOf<RangeSurfaceView?>(null) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready. Start the blind sensitivity finder.") }
    var roundLabel by remember { mutableStateOf("No active round") }
    var result by remember { mutableStateOf<FinderResult?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(rangeView) {
        rangeView?.onResume()
        onDispose { rangeView?.onPause() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("COUNTERFLAME // RANGE", color = Crimson, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (running) "$status  •  SENSITIVITY HIDDEN" else status, color = Muted, fontSize = 10.sp)
            }
            Text(roundLabel, color = Color.White, fontSize = 10.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { RangeSurfaceView(context).also { rangeView = it } },
                modifier = Modifier.fillMaxSize()
            )
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawCircle(Crimson, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, cy))
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx - 12.dp.toPx(), cy), androidx.compose.ui.geometry.Offset(cx - 4.dp.toPx(), cy), 1.5.dp.toPx())
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx + 4.dp.toPx(), cy), androidx.compose.ui.geometry.Offset(cx + 12.dp.toPx(), cy), 1.5.dp.toPx())
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx, cy - 12.dp.toPx()), androidx.compose.ui.geometry.Offset(cx, cy - 4.dp.toPx()), 1.5.dp.toPx())
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(cx, cy + 4.dp.toPx()), androidx.compose.ui.geometry.Offset(cx, cy + 12.dp.toPx()), 1.5.dp.toPx())
            }
            Button(
                onClick = { rangeView?.fire() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).size(82.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Crimson)
            ) { Text("FIRE", fontWeight = FontWeight.Black) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (running) {
                        job?.cancel()
                        running = false
                        rangeView?.cancelRound()
                        status = "Finder cancelled."
                    } else {
                        val search = SensitivitySearch(store.startingSensitivity())
                        result = null
                        running = true
                        job = scope.launch {
                            while (true) {
                                val round = search.nextRound() ?: break
                                roundLabel = "${round.phase} ${round.index}"
                                status = "${round.scenario}  •  ${round.phase}"
                                rangeView?.setSensitivityDegreesPerDp(round.sensitivityDegPerDp)
                                rangeView?.setScenario(round.scenario)
                                rangeView?.beginRound()
                                delay(20000L)
                                val metrics = rangeView?.endRound() ?: RoundMetrics(0.0, 0, 0, 20000L, 0, 0, 0.0, 0.0)
                                search.record(round, metrics)
                                if (search.isComplete) break
                                status = "REST  •  next sensitivity loading"
                                roundLabel = "REST"
                                delay(search.restAfterRoundMs())
                            }
                            result = search.result()
                            result?.let { store.saveFinderResult(it) }
                            running = false
                            roundLabel = "COMPLETE"
                            status = if (result == null) "No result was produced." else "Finder complete. Review your result below."
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFF6A2525) else Crimson)
            ) { Text(if (running) "CANCEL FINDER" else "START SENS FINDER") }
            OutlinedButton(onClick = { rangeView?.beginRound(); status = "Free practice round started." }, modifier = Modifier.weight(1f)) {
                Text("FREE PRACTICE", color = Crimson)
            }
        }
        result?.let { FinderResultPanel(it, onSave = { store.saveCounterflameSensitivity(it.recommendedSensitivity); store.saveStartingSensitivity(it.recommendedSensitivity); status = "Recommended sensitivity saved to Counterflame and set as the next finder baseline." }) }
    }
}

@Composable
private fun FinderResultPanel(result: FinderResult, onSave: () -> Unit) {
    SurfaceCard {
        Text("RECOMMENDED SENSITIVITY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("%.4f° / dp".format(result.recommendedSensitivity), color = Crimson, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("Score %.1f  •  %s confidence  •  %s".format(result.score, result.confidence, result.tendency), color = Color.White, fontSize = 11.sp)
        Text("SCORE VS SENSITIVITY", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        SensitivityChart(result.points)
        Text("Accuracy %.1f%%  |  Hits %d/%d".format(result.metrics.accuracyPercent, result.metrics.hits, result.metrics.shots), color = Muted, fontSize = 10.sp)
        Text("Time-to-target ${result.metrics.timeToTargetMs} ms  |  Overshoot ${result.metrics.overshoots}  |  Undershoot ${result.metrics.undershoots}", color = Muted, fontSize = 10.sp)
        Text("Path efficiency %.0f%%  |  Time on target %.0f%%".format(result.metrics.pathEfficiency * 100.0, result.metrics.timeOnTargetPercent), color = Muted, fontSize = 10.sp)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Crimson)) {
            Text("SAVE TO COUNTERFLAME PROFILE")
        }
    }
}

@Composable
private fun SensitivityChart(points: List<SensitivityPoint>) {
    Canvas(Modifier.fillMaxWidth().height(100.dp).padding(top = 8.dp)) {
        if (points.isEmpty()) return@Canvas
        val sorted = points.sortedBy { it.sensitivityDegPerDp }
        val minScore = (sorted.minOf { it.score } - 5.0).coerceAtLeast(0.0)
        val maxScore = (sorted.maxOf { it.score } + 5.0).coerceAtMost(100.0).coerceAtLeast(minScore + 1.0)
        val maxSens = sorted.maxOf { it.sensitivityDegPerDp }
        val minSens = sorted.minOf { it.sensitivityDegPerDp }
        val xRange = (maxSens - minSens).coerceAtLeast(0.000001)
        val yRange = maxScore - minScore
        val coordinates = sorted.map { point ->
            val x = ((point.sensitivityDegPerDp - minSens) / xRange * size.width).toFloat()
            val y = (size.height - ((point.score - minScore) / yRange * size.height)).toFloat()
            androidx.compose.ui.geometry.Offset(x, y)
        }
        for (index in 0 until coordinates.lastIndex) drawLine(Crimson, coordinates[index], coordinates[index + 1], 2.dp.toPx())
        coordinates.forEach { drawCircle(Color.White, 3.dp.toPx(), it) }
    }
}

@Composable
private fun SensitivityScreen(store: AimcoreStore) {
    var dpi by remember { mutableStateOf(store.calculatorDpi().toFloat()) }
    var sensitivity by remember { mutableStateOf(store.calculatorSensitivity().toFloat()) }
    var startingSens by remember { mutableStateOf(store.startingSensitivity().toFloat()) }
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val fullSwipeDegrees = startingSens * screenWidthDp
    val cm360 = if (dpi > 0f && sensitivity > 0f) 360.0 * 2.54 / (dpi * sensitivity * 0.022) else 0.0
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Heading("Sensitivity Lab", "Counterflame reference calculator and profile settings.") }
        item {
            SurfaceCard {
                Text("DPI", color = Muted, fontSize = 10.sp)
                Text(dpi.roundToInt().toString(), color = Crimson, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Slider(value = dpi, onValueChange = { dpi = it }, valueRange = 100f..3200f)
                Text("IN-GAME SENSITIVITY", color = Muted, fontSize = 10.sp)
                Text("%.3f".format(sensitivity), color = Crimson, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0.05f..5f)
            }
        }
        item {
            SurfaceCard {
                Text("CM / 360°  •  YAW 0.022", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("%.2f cm".format(cm360), color = Crimson, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Formula: 360 × 2.54 ÷ (DPI × sensitivity × 0.022). This is a CS-style reference formula; individual games may use different input scaling.", color = Muted, fontSize = 11.sp)
                Button(onClick = { store.saveCalculatorSettings(dpi.toDouble(), sensitivity.toDouble()) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Crimson)) { Text("SAVE CONVERTER SETTINGS") }
            }
        }
        item {
            SurfaceCard {
                Text("FINDER BASE SENSITIVITY", color = Muted, fontSize = 10.sp)
                Text("%.3f° / dp".format(startingSens), color = Crimson, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Slider(value = startingSens, onValueChange = { startingSens = it }, valueRange = 0.02f..0.8f)
                Text("Full-width swipe ≈ %.1f°".format(fullSwipeDegrees), color = Color.White, fontSize = 13.sp)
                Text("The 3D finder starts from this degrees-per-dp baseline. It is not the same unit as an in-game sensitivity number.", color = Muted, fontSize = 11.sp)
                Button(onClick = { store.saveStartingSensitivity(startingSens.toDouble()) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Crimson)) { Text("SAVE FINDER BASELINE") }
            }
        }
    }
}

@Composable
private fun ProfilesScreen(store: AimcoreStore) {
    val saved = store.counterflameSensitivity()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Heading("Game Profiles", "Original profiles and saved sensitivity recommendations.") }
        item {
            SurfaceCard {
                Text("COUNTERFLAME", color = Crimson, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("Original tactical FPS training profile • geometric range • no borrowed game assets.", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (saved == null) "No finder result saved yet." else "Saved sensitivity: %.4f° / dp".format(saved), color = Color.White, fontSize = 13.sp)
                Text("Finder history entries: ${store.historyCount()}", color = Muted, fontSize = 10.sp)
            }
        }
        listOf(
            "Roblox" to "Per-experience sensitivity notes.",
            "Call of Duty: Mobile" to "Camera and firing sensitivity notes.",
            "PUBG Mobile" to "Camera, ADS and gyro notes.",
            "Minecraft" to "Touch-look sensitivity notes."
        ).forEach { (name, description) ->
            item {
                SurfaceCard {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(description, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}
