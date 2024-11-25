plugins {
    id("multiloader-platform")

    id("net.neoforged.moddev") version("2.0.42-beta")
}

base {
    archivesName = "sodium-neoforge"
}

repositories {
    maven("https://maven.pkg.github.com/ims212/ForgifiedFabricAPI") {
        credentials {
            username = "IMS212"
            // Read only token
            password = "ghp_" + "DEuGv0Z56vnSOYKLCXdsS9svK4nb9K39C1Hn"
        }
    }

    maven("https://maven.su5ed.dev/releases")
    maven("https://maven.neoforged.net/releases/")
}

sourceSets {
    create("service")
}

val configurationCommonModJava: Configuration = configurations.create("commonModJava") {
    isCanBeResolved = true
}
val configurationCommonModResources: Configuration = configurations.create("commonModResources") {
    isCanBeResolved = true
}

val configurationCommonServiceJava: Configuration = configurations.create("commonServiceJava") {
    isCanBeResolved = true
}
val configurationCommonServiceResources: Configuration = configurations.create("commonServiceResources") {
    isCanBeResolved = true
}

dependencies {
    configurationCommonModJava(project(path = ":common", configuration = "commonMainJava"))
    configurationCommonModJava(project(path = ":common", configuration = "commonApiJava"))
    configurationCommonServiceJava(project(path = ":common", configuration = "commonEarlyLaunchJava"))

    configurationCommonModResources(project(path = ":common", configuration = "commonMainResources"))
    configurationCommonModResources(project(path = ":common", configuration = "commonApiResources"))
    configurationCommonServiceResources(project(path = ":common", configuration = "commonEarlyLaunchResources"))

    fun addEmbeddedFabricModule(dependency: String) {
        dependencies.implementation(dependency)
        dependencies.jarJar(dependency)
    }

    addEmbeddedFabricModule("org.sinytra.forgified-fabric-api:fabric-api-base:0.4.42+d1308ded19")
    addEmbeddedFabricModule("org.sinytra.forgified-fabric-api:fabric-renderer-api-v1:5.0.0+babc52e504")
    addEmbeddedFabricModule("org.sinytra.forgified-fabric-api:fabric-rendering-data-attachment-v1:0.3.48+73761d2e19")
    addEmbeddedFabricModule("org.sinytra.forgified-fabric-api:fabric-block-view-api-v2:1.0.10+9afaaf8c19")

    jarJar(project(":neoforge", "service"))
}

val serviceJar = tasks.create<Jar>("serviceJar") {
    from(configurationCommonServiceJava)
    from(configurationCommonServiceResources)

    from(sourceSets["service"].output)

    from(rootDir.resolve("LICENSE.md"))

    manifest.attributes["FMLModType"] = "LIBRARY"

    archiveClassifier = "service"
}

val configurationService: Configuration = configurations.create("service") {
    isCanBeConsumed = true
    isCanBeResolved = true

    outgoing {
        artifact(serviceJar)
    }
}

sourceSets {
    named("service") {
        compileClasspath = sourceSets["main"].compileClasspath
        runtimeClasspath = sourceSets["main"].runtimeClasspath

        compileClasspath += configurationCommonServiceJava
        runtimeClasspath += configurationCommonServiceJava
    }

    main {
        compileClasspath += configurationCommonModJava
        runtimeClasspath += configurationCommonModJava
    }
}

neoForge {
    version = BuildConfig.NEOFORGE_VERSION

    if (BuildConfig.PARCHMENT_VERSION != null) {
        parchment {
            minecraftVersion = BuildConfig.MINECRAFT_VERSION
            mappingsVersion = BuildConfig.PARCHMENT_VERSION
        }
    }

    runs {
        create("Client") {
            client()
            ideName = "NeoForge/Client"
        }
    }

    mods {
        create("sodium") {
            sourceSet(sourceSets["main"])
            sourceSet(project(":common").sourceSets["main"])
            sourceSet(project(":common").sourceSets["api"])
        }

        create("sodium-service") {
            sourceSet(sourceSets["service"])
            sourceSet(project(":common").sourceSets["workarounds"])
        }
    }
}

tasks {
    jar {
        from(configurationCommonModJava)
        destinationDirectory.set(file(rootProject.layout.buildDirectory).resolve("mods"))
    }

    processResources {
        from(configurationCommonModResources)
    }
}

