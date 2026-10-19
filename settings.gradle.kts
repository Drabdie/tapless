pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ai-phone-agent"

include(
    ":app",
    ":core-common",
    ":core-ui",
    ":core-network",
    ":core-database",
    ":agent-accessibility",
    ":feature-chat",
    ":feature-agent",
    ":feature-onboarding",
)
