<img src="common/src/main/resources/sodium-icon.png" width="128">

# Sodium

Sodium is a powerful rendering engine and optimization mod for the Minecraft client which improves frame rates and reduces
micro-stutter, while fixing many graphical issues in Minecraft.

**This mod is the result of thousands of hours of development, and is made possible thanks to players like you.** If you
would like to show a token of your appreciation for my work, and help support the development of Sodium in the process,
then consider [buying me a coffee](https://caffeinemc.net/donate).

<a href="https://caffeinemc.net/donate"><img src="https://storage.ko-fi.com/cdn/kofi2.png?v=3" width="180"/></a>

---

### 📥 Downloads

#### Stable builds

The latest stable release of Sodium can be downloaded from our official [Modrinth](https://modrinth.com/mod/sodium) and
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/sodium) pages.

#### Nightly builds (for developers)

We also provide bleeding-edge builds ("nightlies") which are useful for testing the very latest changes before they're
packaged into a release. These builds are only provided for other mod developers and users with expert skills, and do
not come with any support or warranty. It is often the case they have issues and lack compatibility with other mods.

The latest nightly build for each current branch of development can be downloaded below.

- Minecraft 1.21.4 (latest): [Download nightly](https://nightly.link/CaffeineMC/sodium/workflows/build-commit/dev/sodium-artifacts-dev.zip) or [View all builds](https://github.com/CaffeineMC/sodium/actions/workflows/build-commit.yml?query=branch%3Adev)
- Minecraft 1.21.3: [Download nightly](https://nightly.link/CaffeineMC/sodium/workflows/build-commit/1.21.3%2Fstable/sodium-artifacts-1.21.3-stable.zip) or [View all builds](https://github.com/CaffeineMC/sodium/actions/workflows/build-commit.yml?query=branch%3A1.21.3%2Fstable)
- Minecraft 1.21.1: [Download nightly](https://nightly.link/CaffeineMC/sodium/workflows/build-commit/1.21.1%2Fstable/sodium-artifacts-1.21.1-stable.zip) or [View all builds](https://github.com/CaffeineMC/sodium/actions/workflows/build-commit.yml?query=branch%3A1.21.1%2Fstable)

### 🖥️ Installation

Since the release of Sodium 0.6.0, both the _Fabric_ and _NeoForge_ mod loaders are supported. We generally recommend
that new users prefer to use the _Fabric_ mod loader, since it is more lightweight and stable (for the time being.)

For more information about downloading and installing the mod, please refer to our [Installation Guide](https://github.com/CaffeineMC/sodium/wiki/Installation).

### 🙇 Getting Help

For technical support (including help with mod installation problems and game crashes), please use our
[official Discord server](https://caffeinemc.net/discord).

### 📬 Reporting Issues

If you do not need technical support and would like to report an issue (bug, crash, etc.) or otherwise request changes
(for mod compatibility, new features, etc.), then we encourage you to open an issue on the
[project issue tracker](https://github.com/CaffeineMC/sodium/issues).

Please note that while the issue tracker is open to feature requests, development is primarily focused on
improving compatibility, performance, and finishing any unimplemented features necessary for parity with
the vanilla renderer.

### 💬 Join the Community

We have an [official Discord community](https://caffeinemc.net/discord) for all of our projects. By joining, you can:
- Get installation help and technical support for all of our mods
- Get the latest updates about development and community events
- Talk with and collaborate with the rest of our team
- ... and just hang out with the rest of our community.

## ✅ Hardware Compatibility

We only provide official support for graphics cards which have up-to-date drivers that are compatible with OpenGL 4.5
or newer. Most graphics cards released in the past 12 years will meet these requirements, including the following:

- AMD Radeon HD 7000 Series (GCN 1) or newer
- NVIDIA GeForce 400 Series (Fermi) or newer
- Intel HD Graphics 500 Series (Skylake) or newer

Nearly all graphics cards that are already compatible with Minecraft (which requires OpenGL 3.3) should also work
with Sodium. But our team cannot ensure compatibility or provide support for older graphics cards, and they may
not work with future versions of Sodium.

#### OpenGL Compatibility Layers

Devices which need to use OpenGL translation layers (such as GL4ES, ANGLE, etc) are not supported and will very likely
not work with Sodium. These translation layers do not implement required functionality and they suffer from underlying
driver bugs which cannot be worked around.

## 🛠️ Building from sources

Sodium uses the [Gradle build tool](https://gradle.org/) and can be built with the `gradle build` command. The build
artifacts (production binaries and their source bundles) can be found in the `build/mods` directory.

The [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:using_wrapper) is provided for ease of use and will automatically download and install the
appropriate version of Gradle for the project build. To use the Gradle wrapper, substitute `gradle` in build commands
with `./gradlew.bat` (Windows) or `./gradlew` (macOS and Linux).

### Build Requirements

- OpenJDK 21
    - We recommend using the [Eclipse Temurin](https://adoptium.net/) distribution as it's regularly tested by our developers and known
      to be of high quality.
- Gradle 8.10.x
    - Typically, newer versions of Gradle will work without issues, but the build script is only tested against the
      version used by the [wrapper script](/gradle/wrapper/gradle-wrapper.properties).

## 📜 License

Except where otherwise stated (see [third-party license notices](thirdparty/NOTICE.txt)), the content of this repository is provided
under the [Polyform Shield 1.0.0](LICENSE.md) license by [JellySquid](https://jellysquid.me).
