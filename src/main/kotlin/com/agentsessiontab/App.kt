package com.agentsessiontab

import androidx.compose.desktop.ui.tooling.preview.Preview
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.GraphicsEnvironment
import java.util.Locale
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private val TrackerDark = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF061018),
    secondary = Color(0xFF5EEAD4),
    tertiary = Color(0xFFE2A6FF),
    background = Color(0xFF070A0D),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF0F141A),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1A2430),
    onSurfaceVariant = Color(0xFFB6C4D4),
    outline = Color(0xFF3D5366),
    outlineVariant = Color(0xFF2A3844),
)

private val GradientTop = Color(0xFF0A1018)
private val GradientBottom = Color(0xFF070A0D)

@Composable
private fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TrackerDark,
        content = content,
    )
}

private fun activityTint(kind: ProcessActivityKind): Color = when (kind) {
    ProcessActivityKind.Active -> Color(0xFF6EE7B7)
    ProcessActivityKind.IdleOrWaiting -> Color(0xFF7EB6FF)
    ProcessActivityKind.Stopped -> Color(0xFFFFB86B)
    ProcessActivityKind.Zombie -> Color(0xFFFF7B8A)
    ProcessActivityKind.Unknown -> Color(0xFF94A3B8)
}

@Composable
private fun StatusPill(activity: ProcessActivity) {
    val tint = activityTint(activity.kind)
    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.18f),
        contentColor = tint,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = activity.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "ps ${activity.rawState}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RunningAgentCard(agent: RunningAgent) {
    val loc = cwdDisplayForUi(agent)
    val cpuStr = agent.cpuPercent?.let { v -> String.format(Locale.US, "%.1f%% CPU", v) } ?: "CPU —"
    val rssStr = formatRssKiB(agent.rssKiB)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = agent.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                StatusPill(agent.activity)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = loc,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Up ${agent.uptime} · $cpuStr · RSS $rssStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (agent.configHints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Config hints (files / argv, not live usage)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Medium,
                )
                agent.configHints.forEach { line ->
                    Text(
                        text = "· $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "pid ${agent.pid} · ${agent.argvPreview}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun CounterView(
    runningAgents: List<RunningAgent>,
    lastUpdated: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = "Live agent processes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "cwd · uptime · CPU · RSS from the OS · model/token sniffs from local JSON when present",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (runningAgents.isEmpty()) {
            Text(
                text = "No matching CLIs. Claude Desktop is hidden on purpose — only terminals / agents like Claude Code, Codex, Cursor…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            runningAgents.forEach { agent ->
                RunningAgentCard(agent)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (!lastUpdated.isNullOrEmpty()) {
            Text(
                text = "Last poll: $lastUpdated · auto every 5s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
@Preview
fun AppPreview() {
    val sample = listOf(
        RunningAgent(
            label = "Claude",
            pid = 1001L,
            activity = ProcessActivity(ProcessActivityKind.IdleOrWaiting, "S"),
            cwd = "/Users/me/projects/demo",
            cwdResolutionNote = null,
            argvPreview = "node …/claude-code …",
            uptime = "01:42:07",
            rssKiB = 186_000L,
            cpuPercent = 1.2,
            configHints = listOf("~/.claude/settings.json · model field(s): claude-sonnet-4-20250514"),
        ),
        RunningAgent(
            label = "Codex",
            pid = 2002L,
            activity = ProcessActivity(ProcessActivityKind.Active, "R"),
            cwd = "/Users/me/work",
            cwdResolutionNote = null,
            argvPreview = "openai.cli.codex …",
            uptime = "00:03:12",
            rssKiB = 94_000L,
            cpuPercent = 8.9,
        ),
    )
    TrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CounterView(sample, "10:15 PM")
        }
    }
}

fun main() {
    if (GraphicsEnvironment.isHeadless()) {
        HeadlessTerminal.run()
        return
    }
    application {
        var runningAgents by remember { mutableStateOf(emptyList<RunningAgent>()) }
        var lastUpdated by remember { mutableStateOf<String?>(null) }

        fun refreshCounts() {
            runningAgents = listRunningAgents()
            lastUpdated = java.time.LocalTime.now().withNano(0).toString()
        }

        LaunchedEffect(Unit) {
            refreshCounts()
            while (true) {
                delay(TimeUnit.SECONDS.toMillis(5))
                refreshCounts()
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Running agent tracker",
            state = androidx.compose.ui.window.rememberWindowState(width = 600.dp, height = 720.dp),
        ) {
            TrackerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(GradientTop, GradientBottom),
                            ),
                        ),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.padding(start = 8.dp, top = 4.dp)) {
                                Text(
                                    text = "Agent tracker",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "CLIs only — no Claude Desktop",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { refreshCounts() },
                                modifier = Modifier.padding(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Refresh")
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            color = Color.Transparent,
                        ) {
                            CounterView(
                                runningAgents = runningAgents,
                                lastUpdated = lastUpdated,
                            )
                        }
                    }
                }
            }
        }
    }
}
