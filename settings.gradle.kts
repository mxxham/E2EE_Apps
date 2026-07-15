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

rootProject.name = "e2ee-chat-app"

// ── Core Modules ──────────────────────────────────────────────────────────────
include(":core:model")
include(":core:database")
include(":core:security")
include(":core:network")

// ── Feature Modules ───────────────────────────────────────────────────────────
include(":features:chat")
include(":features:conversations")
include(":features:presence")
include(":features:media")
include(":features:notifications")

// ── Application Entry Point ───────────────────────────────────────────────────
include(":app")
