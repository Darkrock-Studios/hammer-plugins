rootProject.name = "hammer-kotlin-plugins"

pluginManagement {
	includeBuild("gradle-plugin")
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		mavenCentral()
	}
}

include("plugin-kit", "upper", "wordcount", "wordfreq", "style", "mcp", "hello")
project(":plugin-kit").projectDir = file("hammer")
