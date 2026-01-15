package cz.pavl.metronome

import android.content.Context
import android.content.Intent // NOVÝ IMPORT
import android.net.Uri // NOVÝ IMPORT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable // NOVÝ IMPORT
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.pavl.metronome.ui.theme.MetronomeTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences("metronome_prefs", Context.MODE_PRIVATE)
            }
            val engine = remember { MetronomeEngine(context) }
            val factory = MetronomeViewModelFactory(engine, prefs)
            val viewModel: MetronomeViewModel = viewModel(factory = factory)

            MetronomeTheme(darkTheme = viewModel.isDarkMode.value) {
                MetronomeApp(viewModel)
            }
        }
    }
}

// --- NAV ---
@Composable
fun MetronomeApp(viewModel: MetronomeViewModel) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.HOME -> MetronomeScreen(viewModel)
                    AppDestinations.SOUNDS -> PlaceholderScreen("Sound setting coming soon!")
                    AppDestinations.SETTINGS -> SettingsScreen(viewModel)
                }
            }
        }
    }
}

// --- METRONOME - MAIN SCREEN ---
@Composable
fun MetronomeScreen(viewModel: MetronomeViewModel) {
    val currentBeat by viewModel.currentBeat.collectAsState(initial = 1)
    val isPlaying = viewModel.isPlaying.value

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Visualizer
        Box(
            modifier = Modifier
                .size(320.dp)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            PolygonVisualizer(
                bpm = viewModel.bpm.value,
                beatsPerBar = viewModel.beatsPerBar.value,
                currentBeat = currentBeat,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )

            if (isPlaying) {
                Text(
                    text = "$currentBeat",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // BPM
        Text(
            text = "${viewModel.bpm.value}",
            style = MaterialTheme.typography.displayLarge
        )
        Text("BPM", style = MaterialTheme.typography.labelLarge)

        Spacer(modifier = Modifier.height(16.dp))

        // Tap Tempo
        OutlinedButton(
            onClick = { viewModel.onTap() },
            modifier = Modifier.height(48.dp),
            shape = CircleShape
        ) {
            Text("TAP TEMPO")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Slider
        Slider(
            modifier = Modifier.padding(horizontal = 32.dp),
            value = viewModel.bpm.value.toFloat(),
            onValueChange = { viewModel.onBpmChange(it.toInt()) },
            valueRange = 40f..220f
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Number of beats per bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { viewModel.onBeatsChange(viewModel.beatsPerBar.value - 1) }) {
                Icon(Icons.Default.KeyboardArrowLeft, "Méně")
            }
            Text(
                text = "${viewModel.beatsPerBar.value}/4",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            FilledTonalIconButton(onClick = { viewModel.onBeatsChange(viewModel.beatsPerBar.value + 1) }) {
                Icon(Icons.Default.KeyboardArrowRight, "Více")
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Play button
        Button(
            onClick = { viewModel.togglePlay() },
            modifier = Modifier.size(90.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// --- SETTINGS SCREEN ---
@Composable
fun SettingsScreen(viewModel: MetronomeViewModel) {
    // context for email intent
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance section
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dark mode", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = viewModel.isDarkMode.value,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        // About author
        Text(
            text = "About the Author",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Name
        AuthorInfoItem(
            icon = Icons.Default.Person,
            label = "Name",
            value = "Pavel Michenka"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // EMAIL
        AuthorInfoItem(
            icon = Icons.Default.Email,
            label = "Email",
            value = "pavelmichenka373@gmail.com",
            onClick = {
                // email intent
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:pavelmichenka373@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, "Metronome App Feedback")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // No email app available
                }
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "App version 1.0",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

// Author info composable
@Composable
fun AuthorInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null // null by default
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()

            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,

                color = if (onClick != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// visualization of metronome beats as polygon with moving ball
@Composable
fun PolygonVisualizer(
    bpm: Int,
    beatsPerBar: Int,
    currentBeat: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val ballPosition = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var vertices by remember { mutableStateOf(listOf<Offset>()) }

    LaunchedEffect(currentBeat, isPlaying, beatsPerBar, bpm) {
        if (!isPlaying || vertices.isEmpty()) {
            if (vertices.isNotEmpty()) ballPosition.snapTo(vertices[0])
            return@LaunchedEffect
        }

        val startIndex = (currentBeat - 1) % vertices.size
        val nextIndex = (startIndex + 1) % vertices.size

        ballPosition.snapTo(vertices[startIndex])
        val beatDurationMs = (60000 / bpm).toInt()

        ballPosition.animateTo(
            targetValue = vertices[nextIndex],
            animationSpec = tween(durationMillis = beatDurationMs, easing = LinearEasing)
        )
    }

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val ballColor = MaterialTheme.colorScheme.primary
    val activeCornerColor = MaterialTheme.colorScheme.error
    val inactiveCornerColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        val padding = 0.8f

        val newVertices = mutableListOf<Offset>()
        if (beatsPerBar == 2) {
            newVertices.add(Offset(center.x - r * padding, center.y))
            newVertices.add(Offset(center.x + r * padding, center.y))
        } else {
            val startAngle = -PI / 2
            for (i in 0 until beatsPerBar) {
                val angle = startAngle + (2 * PI * i / beatsPerBar)
                val x = center.x + r * padding * cos(angle).toFloat()
                val y = center.y + r * padding * sin(angle).toFloat()
                newVertices.add(Offset(x, y))
            }
        }
        vertices = newVertices

        if (vertices.size >= 2) {
            val path = Path()
            path.moveTo(vertices[0].x, vertices[0].y)
            if (beatsPerBar == 2) {
                path.lineTo(vertices[1].x, vertices[1].y)
            } else {
                for (i in 1 until vertices.size) path.lineTo(vertices[i].x, vertices[i].y)
                path.close()
            }
            drawPath(path, trackColor, style = Stroke(width = 4.dp.toPx()))
        }

        vertices.forEachIndexed { index, point ->
            val isCurrent = (index + 1) == currentBeat && isPlaying
            drawCircle(
                color = if (isCurrent) activeCornerColor else inactiveCornerColor,
                radius = if (isCurrent) 10.dp.toPx() else 6.dp.toPx(),
                center = point
            )
        }

        if (isPlaying && vertices.isNotEmpty()) {
            drawCircle(ballColor, 12.dp.toPx(), ballPosition.value)
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Metronome", Icons.Default.Home),
    SOUNDS("Sounds", Icons.Default.Notifications),
    SETTINGS("Settings", Icons.Default.Settings),
}