<img src="common/src/main/resources/sodium-icon.png" width="128">

# Sodium

Sodium is a powerful rendering engine and optimization mod for the Minecraft client which improves frame rates and reduces
micro-stutter, while fixing many graphical issues in Minecraft.

### 📥 Installation

The latest version of Sodium can be downloaded from our official [Modrinth](https://modrinth.com/mod/sodium) and
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/sodium) pages. 

Since the release of Sodium 0.6.0, both the _Fabric_ and _NeoForge_ mod loaders are supported. We generally recommend
that new users prefer to use the _Fabric_ mod loader, since it is more lightweight and stable (for the time being.)

For more information about downloading and installing the mod, please refer to our [Installation Guide](https://github.com/CaffeineMC/sodium/wiki/Installation).

### 🐛 Reporting Issues

You can report bugs and crashes by opening an issue on our [issue tracker](https://github.com/CaffeineMC/sodium/issues).
Before opening a new issue, use the search tool to make sure that your issue has not already been reported and ensure
that you have completely filled out the issue template. Issues that are duplicates or do not contain the necessary
information to triage and debug may be closed.

Please note that while the issue tracker is open to feature requests, development is primarily focused on
improving hardware compatibility, performance, and finishing any unimplemented features necessary for parity with
the vanilla renderer.

### 💬 Join the Community

We have an [official Discord community](https://caffeinemc.net/discord) for all of our projects. By joining, you can:
- Get installation help and technical support for all of our mods
- Get the latest updates about development and community events
- Talk with and collaborate with the rest of our team
- ... and just hang out with the rest of our community.

## ✅ Hardware Compatibility

We only provide support for graphics cards which have up-to-date drivers for OpenGL 4.6. Most graphics cards which have
been released since year 2010 are supported, such as the...

- AMD Radeon HD 7000 Series (GCN 1) or newer
- NVIDIA GeForce 400 Series (Fermi) or newer
- Intel HD Graphics 500 Series (Skylake) or newer

In some cases, older graphics cards may also work (so long as they have up-to-date drivers which have support for
OpenGL 3.3), but they are not officially supported, and may not be compatible with future versions of Sodium.

#### OpenGL Compatibility Layers

Devices which need to use OpenGL translation layers (such as GL4ES, ANGLE, etc) are not supported and will very likely
not work with Sodium. These translation layers do not implement required functionality and they suffer from underlying
driver bugs which cannot be worked around.

## 🛠️ Developer Guide

### Building from sources

Sodium uses a typical Gradle project structure and can be compiled by simply running the default `build` task. The build
artifacts (typical mod binaries, and their sources) can be found in the `build/libs` directory.

#### Requirements

We recommend using a package manager (such as [SDKMAN](https://sdkman.io/)) to manage toolchain dependencies and keep
them up to date. For many Linux distributions, these dependencies will be standard packages in your software
repositories.

- OpenJDK 21
    - We recommend using the [Eclipse Temurin](https://adoptium.net/) distribution, as it's known to be high quality 
      and to work without issues.
- Gradle 8.6.x (optional)
    - The [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:using_wrapper) is provided
      in this repository can be used instead of installing a suitable version of Gradle yourself. However, if you are
      building many projects, you may prefer to install it yourself through a suitable package manager as to save disk
      space and to avoid many different Gradle daemons sitting around in memory.
    - Typically, newer versions of Gradle will work without issues, but the build script is only tested against the
      version specified by the wrapper script.

## 📜 License

Except where otherwise stated (see [third-party license notices](thirdparty/NOTICE.txt)), the content of this repository is provided
under the [Polyform Shield 1.0.0](LICENSE.md) license by [JellySquid](https://jellysquid.me).
