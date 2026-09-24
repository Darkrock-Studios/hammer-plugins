import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
	kotlin("multiplatform") version "2.4.10"
	kotlin("plugin.serialization") version "2.4.10"
}

kotlin {
	@OptIn(ExperimentalWasmDsl::class)
	wasmWasi {
		// Hammer runs the module; Node is only named because the Kotlin plugin requires an environment.
		nodejs()
		binaries.executable()
	}

	sourceSets {
		val wasmWasiMain by getting {
			dependencies {
				implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
			}
		}
	}
}
