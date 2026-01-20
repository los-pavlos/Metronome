package cz.pavl.metronome

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cz.pavl.metronome.ui.theme.MetronomeTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val prefs = getSharedPreferences("metronome_prefs", Context.MODE_PRIVATE)
        val tunerEngine = TunerEngine()
        val factory = MetronomeViewModelFactory(applicationContext, tunerEngine, prefs)
        val viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[MetronomeViewModel::class.java]

        setContent {
            MetronomeTheme(darkTheme = viewModel.isDarkMode.value) {
                MetronomeApp(viewModel, tunerEngine)
            }
        }
    }
}

@Composable
fun MetronomeApp(viewModel: MetronomeViewModel, tunerEngine: TunerEngine) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestinations.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination == screen,
                        onClick = { currentDestination = screen }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val bgBrush = Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceContainer
            )
        )

        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(bgBrush)
        ) {
            when (currentDestination) {
                AppDestinations.HOME -> ModernMetronomeScreen(viewModel)
                AppDestinations.TUNER -> TunerScreen(tunerEngine, viewModel)
                AppDestinations.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
fun ModernMetronomeScreen(viewModel: MetronomeViewModel) {
    val currentBeat = viewModel.currentBeat.value
    val isPlaying = viewModel.isPlaying.value

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            ModernPolygonVisualizer(viewModel.activeBpm.value, viewModel.beatsPerBar.value, viewModel.subdivisions.value, currentBeat, isPlaying, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isPlaying) Text("$currentBeat", style = MaterialTheme.typography.displayLarge.copy(fontSize = 100.sp, fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                else {
                    Text("${viewModel.bpm.value}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("BPM", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Tempo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${viewModel.bpm.value} BPM", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = viewModel.bpm.value.toFloat(), onValueChange = { viewModel.onBpmChange(it.toInt(), updateEngine = false) }, onValueChangeFinished = { viewModel.onBpmSliderFinished() }, valueRange = 40f..220f, modifier = Modifier.fillMaxWidth())
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp)) {
                        IconButton(onClick = { viewModel.onBeatsChange(viewModel.beatsPerBar.value - 1) }) { Text("-", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                        Text("${viewModel.beatsPerBar.value}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { viewModel.onBeatsChange(viewModel.beatsPerBar.value + 1) }) { Text("+", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                    }
                    OutlinedButton(onClick = { viewModel.onTap() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(50.dp)) { Text("TAP") }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Subdivisions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary); Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SubdivisionToggle(1, viewModel) { c -> Icon(painterResource(R.drawable.note_1), "1", tint = c, modifier = Modifier.size(24.dp)) }
                        SubdivisionToggle(2, viewModel) { c -> Icon(painterResource(R.drawable.note_2), "2", tint = c, modifier = Modifier.size(28.dp)) }
                        SubdivisionToggle(3, viewModel) { c -> Icon(painterResource(R.drawable.note_3), "3", tint = c, modifier = Modifier.size(28.dp)) }
                        SubdivisionToggle(4, viewModel) { c -> Icon(painterResource(R.drawable.note_4), "4", tint = c, modifier = Modifier.size(32.dp)) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.togglePlay() }, modifier = Modifier.fillMaxWidth().height(64.dp).shadow(8.dp, CircleShape), shape = RoundedCornerShape(32.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
                    Icon(if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.size(32.dp)); Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPlaying) "STOP" else "START", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SubdivisionToggle(value: Int, viewModel: MetronomeViewModel, iconContent: @Composable (Color) -> Unit) {
    val isSelected = viewModel.subdivisions.value == value
    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    val contentColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    Box(modifier = Modifier.size(70.dp, 50.dp).clip(RoundedCornerShape(12.dp)).background(bgColor).clickable { viewModel.onSubdivisionChange(value) }, contentAlignment = Alignment.Center) { iconContent(contentColor) }
}

@Composable
fun ModernPolygonVisualizer(bpm: Int, beatsPerBar: Int, subdivisions: Int, currentBeat: Int, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val ballPosition = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var vertices by remember { mutableStateOf(listOf<Offset>()) }
    LaunchedEffect(currentBeat, isPlaying, beatsPerBar, bpm) {
        if (!isPlaying || vertices.isEmpty()) { if (vertices.isNotEmpty()) ballPosition.snapTo(vertices[0]); return@LaunchedEffect }
        val startIndex = (currentBeat - 1) % vertices.size; val nextIndex = (startIndex + 1) % vertices.size
        ballPosition.snapTo(vertices[startIndex]); val beatDurationMs = (60000 / bpm).toInt()
        ballPosition.animateTo(vertices[nextIndex], tween(beatDurationMs, easing = LinearEasing))
    }
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f); val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val activeColor = MaterialTheme.colorScheme.primary; val beatColor = MaterialTheme.colorScheme.error; val inactiveBeatColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val r = size.minDimension / 2 * 0.85f; val center = Offset(size.width / 2, size.height / 2)
        val newVertices = mutableListOf<Offset>(); if (beatsPerBar == 2) { newVertices.add(Offset(center.x - r, center.y)); newVertices.add(Offset(center.x + r, center.y)) } else { val startA = -PI / 2; for (i in 0 until beatsPerBar) { val a = startA + (2 * PI * i / beatsPerBar); newVertices.add(Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())) } }; vertices = newVertices
        if (vertices.size >= 2) {
            val path = Path(); path.moveTo(vertices[0].x, vertices[0].y)
            for (i in 0 until vertices.size) {
                val cv = vertices[i]; val nv = if (beatsPerBar == 2 && i == 1) null else vertices[(i + 1) % vertices.size]
                if (nv != null) {
                    path.lineTo(nv.x, nv.y)
                    if (subdivisions > 1) { for (s in 1 until subdivisions) { val f = s.toFloat() / subdivisions; drawCircle(dotColor, 4.dp.toPx(), Offset(cv.x + (nv.x - cv.x) * f, cv.y + (nv.y - cv.y) * f)) } }
                }
            }
            if (beatsPerBar > 2) path.close(); drawPath(path, trackColor, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        vertices.forEachIndexed { i, pt -> val isA = (i + 1) == currentBeat && isPlaying; if (isA) drawCircle(beatColor.copy(alpha = 0.3f), 20.dp.toPx(), pt); drawCircle(if (isA) beatColor else inactiveBeatColor, if (isA) 12.dp.toPx() else 8.dp.toPx(), pt) }
        if (isPlaying && vertices.isNotEmpty()) { drawCircle(activeColor, 16.dp.toPx(), ballPosition.value); drawCircle(Color.White, 6.dp.toPx(), ballPosition.value) }
    }
}

@Composable
fun TunerScreen(tunerEngine: TunerEngine, viewModel: MetronomeViewModel) {
    val context = LocalContext.current
    val result by tunerEngine.tunerResult.collectAsState()
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean -> hasPermission = isGranted; if (isGranted) tunerEngine.startListening() }
    DisposableEffect(Unit) { if (hasPermission) tunerEngine.startListening(); onDispose { tunerEngine.stopListening() } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(top = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                IconButton(onClick = { viewModel.setReferenceFrequency(viewModel.referenceFreq.value - 1) }) { Text("-", style = MaterialTheme.typography.titleLarge) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("A4 = ${viewModel.referenceFreq.value} Hz", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                IconButton(onClick = { viewModel.setReferenceFrequency(viewModel.referenceFreq.value + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (!hasPermission) { Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Enable Microphone") } } else {
            Box(contentAlignment = Alignment.Center) {
                ProfessionalTunerVisualizer(result)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(result.noteName, style = MaterialTheme.typography.displayLarge.copy(fontSize = 90.sp, fontWeight = FontWeight.Bold), color = getTunerColor(result.deviation))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(String.format("%.1f Hz", result.frequency), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(16.dp))
                val devInt = result.deviation.toInt(); val sign = if (devInt > 0) "+" else ""
                Text("$sign$devInt cents", style = MaterialTheme.typography.bodyLarge, color = getTunerColor(result.deviation))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun getTunerColor(deviation: Float): Color {
    val absDev = abs(deviation)
    return when { absDev < 5 -> Color(0xFF4CAF50); absDev < 20 -> Color(0xFFFFEB3B); else -> MaterialTheme.colorScheme.error }
}

@Composable
fun ProfessionalTunerVisualizer(result: TunerResult) {
    val animatedDeviation by animateFloatAsState(targetValue = result.deviation.coerceIn(-50f, 50f), animationSpec = tween(150, easing = LinearEasing), label = "Needle")
    val targetColor = getTunerColor(result.deviation)
    val animatedColor by animateColorAsState(targetColor, label = "Color")
    Canvas(modifier = Modifier.size(300.dp, 180.dp)) {
        val w = size.width; val arcSize = Size(w, w); val arcTopLeft = Offset(0f, 20f)
        drawArc(Color.LightGray.copy(alpha = 0.3f), 180f, 180f, false, topLeft = arcTopLeft, size = arcSize, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
        val cx = w / 2; val cy = w / 2 + 20f
        val angleDegrees = 270f + (animatedDeviation * 1.8f); val angleRad = angleDegrees * (PI / 180)
        val indicatorX = cx + (w / 2) * cos(angleRad).toFloat(); val indicatorY = cy + (w / 2) * sin(angleRad).toFloat()
        drawArc(Brush.horizontalGradient(listOf(animatedColor.copy(alpha=0.1f), animatedColor, animatedColor.copy(alpha=0.1f))), if (animatedDeviation < 0) angleDegrees else 270f, if (animatedDeviation < 0) (270f - angleDegrees) else (angleDegrees - 270f), false, topLeft = arcTopLeft, size = arcSize, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color.White, 12.dp.toPx(), Offset(indicatorX, indicatorY))
        drawCircle(animatedColor, 9.dp.toPx(), Offset(indicatorX, indicatorY))
    }
}

@Composable
fun SettingsScreen(viewModel: MetronomeViewModel) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium); Spacer(modifier = Modifier.height(24.dp))
        Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Dark mode", style = MaterialTheme.typography.bodyLarge); Switch(checked = viewModel.isDarkMode.value, onCheckedChange = { viewModel.toggleDarkMode(it) })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
        Text("About the Author", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.height(16.dp))
        AuthorInfoItem(Icons.Default.Person, "Name", "Pavel Michenka"); Spacer(modifier = Modifier.height(16.dp))
        AuthorInfoItem(Icons.Default.Email, "Email", "pavelmichenka373@gmail.com", onClick = { val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:pavelmichenka373@gmail.com"); putExtra(Intent.EXTRA_SUBJECT, "Metronome App Feedback") }; try { context.startActivity(intent) } catch (e: Exception) {} })
        Spacer(modifier = Modifier.weight(1f))
        Text("App version 1.0", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
fun AuthorInfoItem(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp)); Spacer(modifier = Modifier.width(16.dp))
        Column { Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray); Text(value, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Metronome", Icons.Default.Home), TUNER("Tuner", Icons.Default.Edit), SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title, style = MaterialTheme.typography.headlineMedium) }
}