plugins {
    id("multiloader-base")
    id("java-library")

    id("fabric-loom") version ("1.8.9")
}

base {
    archivesName = "sodium-common"
}

val configurationPreLaunch = configurations.create("preLaunchDeps") {
    isCanBeResolved = true
}

sourceSets {
    val main = getByName("main")
    val api = create("api")
    val workarounds = create("workarounds")

    api.apply {
        java {
            compileClasspath += main.compileClasspath
        }
    }

    workarounds.apply {
        java {
            compileClasspath += configurationPreLaunch
        }
    }

    main.apply {
        java {
            compileClasspath += api.output
            compileClasspath += workarounds.output
        }
    }

    create("desktop")
}

dependencies {
    minecraft(group = "com.mojang", name = "minecraft", version = BuildConfig.MINECRAFT_VERSION)
    mappings(loom.layered {
        officialMojangMappings()

        if (BuildConfig.PARCHMENT_VERSION != null) {
            parchment("org.parchmentmc.data:parchment-${BuildConfig.MINECRAFT_VERSION}:${BuildConfig.PARCHMENT_VERSION}@zip")
        }
    })

    compileOnly("io.github.llamalad7:mixinextras-common:0.3.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.3.5")

    compileOnly("net.fabricmc:sponge-mixin:0.13.2+mixin.0.8.5")
    compileOnly("net.fabricmc:fabric-loader:${BuildConfig.FABRIC_LOADER_VERSION}")

    fun addDependentFabricModule(name: String) {
        modCompileOnly(fabricApi.module(name, BuildConfig.FABRIC_API_VERSION))
    }

    addDependentFabricModule("fabric-api-base")
    addDependentFabricModule("fabric-block-view-api-v2")
    addDependentFabricModule("fabric-renderer-api-v1")
    addDependentFabricModule("fabric-rendering-data-attachment-v1")

    // We need to be careful during pre-launch that we don't touch any Minecraft classes, since other mods
    // will not yet have an opportunity to apply transformations.
    configurationPreLaunch("org.lwjgl:lwjgl:3.3.3")
    configurationPreLaunch("org.lwjgl:lwjgl-opengl:3.3.3")
    configurationPreLaunch("net.java.dev.jna:jna:5.14.0")
    configurationPreLaunch("net.java.dev.jna:jna-platform:5.14.0")
    configurationPreLaunch("org.slf4j:slf4j-api:2.0.9")
    configurationPreLaunch("org.jetbrains:annotations:25.0.0")
}

loom {
    accessWidenerPath = file("src/main/resources/sodium-common.accesswidener")

    mixin {
        useLegacyMixinAp = false
    }
}

fun exportSourceSetJava(name: String, sourceSet: SourceSet) {
    val configuration = configurations.create("${name}Java") {
        isCanBeResolved = true
        isCanBeConsumed = true
    }

    val compileTask = tasks.getByName<JavaCompile>(sourceSet.compileJavaTaskName)
    artifacts.add(configuration.name, compileTask.destinationDirectory) {
        builtBy(compileTask)
    }
}

fun exportSourceSetResources(name: String, sourceSet: SourceSet) {
    val configuration = configurations.create("${name}Resources") {
        isCanBeResolved = true
        isCanBeConsumed = true
    }

    val compileTask = tasks.getByName<ProcessResources>(sourceSet.processResourcesTaskName)
    compileTask.apply {
        exclude("**/README.txt")
        exclude("/*.accesswidener")
    }

    artifacts.add(configuration.name, compileTask.destinationDir) {
        builtBy(compileTask)
    }
}

// Exports the compiled output of the source set to the named configuration.
fun exportSourceSet(name: String, sourceSet: SourceSet) {
    exportSourceSetJava(name, sourceSet)
    exportSourceSetResources(name, sourceSet)
}

exportSourceSet("commonMain", sourceSets["main"])
exportSourceSet("commonApi", sourceSets["api"])
exportSourceSet("commonEarlyLaunch", sourceSets["workarounds"])
exportSourceSet("commonDesktop", sourceSets["desktop"])

tasks.jar { enabled = false }
tasks.remapJar { enabled = false }