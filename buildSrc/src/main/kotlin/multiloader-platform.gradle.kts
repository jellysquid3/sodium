plugins {
    id("multiloader-base")
    id("maven-publish")
}

val configurationDesktopIntegrationJava: Configuration = configurations.create("commonDesktopIntegration") {
    isCanBeResolved = true
}

dependencies {
    configurationDesktopIntegrationJava(project(path = ":common", configuration = "commonDesktopJava"))
}

tasks {
    processResources {
        inputs.property("version", version)

        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
            expand(mapOf("version" to version))
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.FAIL
        from(rootDir.resolve("LICENSE.md"))

        // Entry-point for desktop integration when the file is executed directly
        from(configurationDesktopIntegrationJava)
        manifest.attributes["Main-Class"] = "net.caffeinemc.mods.sodium.desktop.LaunchWarn"
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = project.name as String
            version = version

            from(components["java"])
        }
    }
}
