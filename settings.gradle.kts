pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
            content {
                excludeGroupByRegex("com\\.google\\.devtools\\.ksp.*")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
    }
}

rootProject.name = "GitScreen"
include(":app")
