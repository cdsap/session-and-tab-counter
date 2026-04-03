package com.agentsessiontab

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.GraphicsEnvironment
import java.util.Locale
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private fun activityColor(kind: ProcessActivityKind): Color = when (kind) {
    ProcessActivityKind.Active -> Color(0xFF2E7D32)
    ProcessActivityKind.IdleOrWaiting -> Color(0xFF1565C0)
    ProcessActivityKind.Stopped -> Color(0xFFE65100)
    ProcessActivityKind.Zombie -> Color(0xFFC62828)
    ProcessActivityKind.Unknown -> Color.Gray
}

@Composable
private fun RunningAgentRow(agent: RunningAgent) {
    val loc = cwdDisplayForUi(agent)
    val cpuStr = agent.cpuPercent?.let { v -> String.format(Locale.US, "%.1f%% CPU", v) } ?: "CPU —"
    val rssStr = "RSS ${formatRssKiB(agent.rssKiB)}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = agent.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = agent.activity.shortLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = activityColor(agent.activity.kind),
            )
            Text(
                text = "ps ${agent.activity.rawState}".trim(),
                fontSize = 11.sp,
                color = Color.Gray,
            )
        }
    }
    Text(
        text = loc,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF1B5E20),
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = "Up ${agent.uptime} · $cpuStr · $rssStr",
        fontSize = 12.sp,
        color = Color(0xFF424242),
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = "pid ${agent.pid} · ${agent.argvPreview}",
        fontSize = 11.sp,
        color = Color(0xFF607D8B),
    )
}

@Composable
fun CounterView(
    runningAgents: List<RunningAgent>,
    lastUpdated: String?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Running agent tracker",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Live CLIs · cwd (lsof /proc) · uptime & CPU & RSS from ps",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            if (runningAgents.isEmpty()) {
                Text(
                    text = "No matching agent processes right now.\n" +
                        "When you run claude-code, codex, gemini-cli, cursor-agent, etc., they will appear here.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    runningAgents.forEachIndexed { i, agent ->
                        if (i > 0) {
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                        RunningAgentRow(agent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!lastUpdated.isNullOrEmpty()) {
                    Text(
                        text = "Last updated: $lastUpdated",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Refreshes every 5s · Token/context use is not from the OS (use each agent’s UI)",
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
                Text(
                    text = "Idle/sleep between tool calls is normal",
                    fontSize = 10.sp,
                    color = Color.Gray,
                )
            }
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
    MaterialTheme {
        CounterView(
            runningAgents = sample,
            lastUpdated = "10:15 PM",
        )
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
            state = androidx.compose.ui.window.rememberWindowState(width = 560.dp, height = 640.dp),
        ) {
            MaterialTheme {
                Surface(color = MaterialTheme.colors.surface) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        IconButton(
                            onClick = { refreshCounts() },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(8.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh")
                        }

                        Divider()

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
