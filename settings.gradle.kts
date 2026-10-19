pluginManagement { repositories {
    if (providers.gradleProperty("useMirror").orNull == "true") maven("https://repo.huaweicloud.com/repository/maven/")
    google(); mavenCentral(); gradlePluginPortal()
} }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("useMirror").orNull == "true") maven("https://repo.huaweicloud.com/repository/maven/")
        google(); mavenCentral()
    }
}
rootProject.name = "GestrinHub"
include(":app")
