import org.gradle.api.tasks.JavaExec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.compose") version "2.3.20"
    id("org.jetbrains.compose") version "1.10.2"
}

group = "com.agentsessiontab"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

compose.desktop {
    application {
        mainClass = "com.agentsessiontab.AppKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "RunningAgentTracker"
            packageVersion = "1.0.0"

            windows {
                menuGroup = "Running agent tracker"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

/** Short console alias: `./gradlew agents` (same as `run`). */
tasks.register("agents") {
    group = "application"
    description = "Run RunningAgentTracker (Compose desktop — same as :run)."
    dependsOn(tasks.named("run"))
}

val forceAgentTrackerTerminal =
    project.findProperty("agentTrackerTerminal")?.toString().equals("true", ignoreCase = true) ||
        project.findProperty("tty")?.toString().equals("true", ignoreCase = true)

afterEvaluate {
    tasks.named<JavaExec>("run") {
        if (forceAgentTrackerTerminal) {
            systemProperty("agent.tracker.terminal", "true")
        }
    }
}
