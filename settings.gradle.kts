rootProject.name = "hammer-plugins"

pluginManagement {
	// The SDK is checked out beside this repo.
	includeBuild("../hammer-plugin-sdk/kotlin/gradle-plugin")
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

// Supplies the kit in place of the published one.
includeBuild("../hammer-plugin-sdk/kotlin")

include("mcp")
include("fantasy-names")
include("real-names")
