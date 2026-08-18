// Racine de la construction Gradle du back (spec §10.1).
// Un module par contexte borné, plus shared-kernel, platform et app.

rootProject.name = "kadran-backend"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

include(
    "shared-kernel",
    "platform",
    "context-identity",
    "context-ingestion",
    "context-activity",
    "context-costmodel",
    "context-fiscal",
    "context-performance",
    "app",
)
