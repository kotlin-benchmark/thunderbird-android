# How to Build Thunderbird for Android (and K-9 Mail)

This document explains how to build this project from source on a clean machine, on **any
operating system** (Linux, macOS, Windows) and **any CPU architecture** (x86-64 / AMD64,
Apple Silicon / ARM64, ARM Linux). It is written for someone who has never built the project
before and does not assume any particular username, home directory, drive letter, or
filesystem layout: wherever a path appears, substitute your own.

The repository builds **two apps** from one codebase:
- **Thunderbird for Android** — module `:app-thunderbird`, application id `net.thunderbird.android`
- **K-9 Mail** — module `:app-k9mail`, application id `com.fsck.k9`

> TL;DR (Thunderbird debug APK)
> ```bash
> # JDK 21+ is mandatory (see section 1.1)
> export JAVA_HOME=/path/to/jdk-21
> export ANDROID_HOME=/path/to/Android/Sdk
> ./gradlew :app-thunderbird:assembleFullDebug
> ```
> Output APK(s): `app-thunderbird/build/outputs/apk/full/debug/`

---

## 1. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | **21 or newer** | Mandatory to *run* the build (Temurin/Adoptium recommended). The compiled **bytecode target is Java 17** (`jvmTarget = JVM_17`), but the toolchain that runs Gradle must be 21+. |
| Android SDK | Platform **36**, Build-Tools **36.x** | `compileSdk = 36`, `minSdk = 23`, `targetSdk = 35`. |
| Gradle | **9.6.1** | Do **not** install manually. The bundled wrapper (`./gradlew`) downloads the exact version on first run. This repo uses the `-all` distribution (~230 MB first download). |
| Git | any recent | Needed to clone; the build also reads Git metadata. |
| Disk space | ~10 GB free | 42 Gradle modules + the dependency cache + two apps with several flavors. |
| RAM | 16 GB recommended (8 GB minimum) | Large multi-module Kotlin/Compose build. See 6.6 if memory-constrained. |

Everything resolves from public repositories (Maven Central, Google's Maven, etc.). **No
tokens, credentials, or private registries are required** for a normal debug build.

### Architecture note (important for "any part of the galaxy")

Gradle, the Android Gradle Plugin, and Kotlin are **pure JVM** and run identically on x86-64,
Apple Silicon (M1/M2/M3/M4), and ARM Linux. The only architecture-specific pieces are the
**JDK** and the **Android SDK command-line tools** — both ship native builds for all common
architectures. Pick the JDK/SDK build that matches your CPU (see 1.1/1.2). Producing the APK
itself is architecture-independent; you can build an APK for any device ABI from any host.

### 1.1 Install a JDK 21+

The version matters more than the vendor: Temurin, Zulu, Corretto, Liberica, Microsoft Build
of OpenJDK, and the JetBrains Runtime all work. Match the download to your OS **and CPU
architecture** (x64 vs aarch64/ARM64).

- Linux x86-64 (Debian/Ubuntu): `sudo apt install openjdk-21-jdk`
- Linux ARM64: same package names on ARM64 distros, or an aarch64 tarball from Adoptium.
- Linux (Fedora/RHEL): `sudo dnf install java-21-openjdk-devel`
- Linux (Arch): `sudo pacman -S jdk21-openjdk`
- macOS (Intel or Apple Silicon, Homebrew): `brew install openjdk@21` (Homebrew picks the right arch)
- Windows (x64 or ARM64): `winget install EclipseAdoptium.Temurin.21.JDK`, or the Adoptium MSI.
- Any OS/arch, no admin rights: download a JDK 21 archive for your platform from
  <https://adoptium.net/> and unpack it anywhere you can write to.

If Android Studio is installed, it ships a JetBrains Runtime 21 you can point at instead of
installing a JDK:

| OS | Bundled JBR path |
|----|------------------|
| Linux | `<android-studio-dir>/jbr` (often `/opt/android-studio/jbr`) |
| macOS | `/Applications/Android Studio.app/Contents/jbr/Contents/Home` |
| Windows | `C:\Program Files\Android\Android Studio\jbr` |

Point `JAVA_HOME` at whichever JDK 21 you chose and verify (must print `21.x`):

```bash
# Linux / macOS (bash/zsh) - substitute your own path
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version
```
```powershell
# Windows (PowerShell), current session
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
```
```bat
:: Windows (cmd.exe), current session
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%
java -version
```

To avoid exporting it in every shell, set it once in your **home** Gradle file
(`~/.gradle/gradle.properties`, or `%USERPROFILE%\.gradle\gradle.properties` on Windows),
which lives outside the repo:

```properties
org.gradle.java.home=/absolute/path/to/jdk-21
```

### 1.2 Install the Android SDK

If you have Android Studio, the SDK is already installed — skip to setting `ANDROID_HOME`
below. Otherwise install the **command-line tools** for your OS/arch from
<https://developer.android.com/studio#command-line-tools-only>, unpack them, then:

```bash
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
sdkmanager --licenses      # accept once
```

Tell the build where the SDK lives, using **either** an environment variable:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"          # Linux
export ANDROID_HOME="$HOME/Library/Android/sdk"  # macOS
```
```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"   # Windows
```

**or** a `local.properties` file in the project root (git-ignored, machine-specific):

```properties
# Linux / macOS
sdk.dir=/absolute/path/to/Android/Sdk
```
```properties
# Windows - forward slashes or escaped backslashes
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

`local.properties` wins over the environment variable, which is handy when several checkouts
on one machine need different SDKs.

---

## 2. Getting the code

```bash
git clone https://github.com/thunderbird/thunderbird-android.git
cd thunderbird-android
```

Windows: if you hit `Filename too long`, run `git config --global core.longpaths true` once.
Prefer cloning to a short path (e.g. `C:\src\tb`) to stay clear of the legacy path-length
limit.

---

## 3. Project layout

42 Gradle modules. The pieces you are most likely to touch:

| Path | What it is |
|------|------------|
| `app-thunderbird/` | The Thunderbird app module (flavors + build types). |
| `app-k9mail/` | The K-9 Mail app module. |
| `app-common/` | Shared application scaffolding, including `MainActivity`. |
| `feature/…` | Feature modules (notification, account, settings, launcher, widget, …). Many are **Kotlin Multiplatform (KMP)** with `androidMain` / `commonMain` source sets. |
| `legacy/…` | Legacy K-9 code being migrated (core, ui, storage). |
| `core/…` | Cross-cutting infrastructure. |
| `build-plugin/` | The project's own convention plugins (`ThunderbirdProjectConfig` holds the SDK/JVM versions). Compiled first. |
| `gradle/libs.versions.toml` | Central version catalog. |

---

## 4. Building

On Windows use `gradlew.bat` (or `.\gradlew.bat`) everywhere below; the extensionless
`gradlew` is the POSIX shell script and will not run in cmd.exe/PowerShell.

### 4.1 Understand the variants first

`:app-thunderbird` has a **flavor dimension `app`** with two flavors — **`foss`** and
**`full`** — crossed with build types **`debug`**, **`release`**, **`beta`**, **`daily`**.
So the task name encodes flavor + build type. There is **no plain `assembleDebug`**; you name
the variant:

| Task | Produces |
|------|----------|
| `:app-thunderbird:assembleFullDebug` | Thunderbird, full flavor, debug — the normal choice |
| `:app-thunderbird:assembleFossDebug` | Thunderbird, FOSS flavor, debug |
| `:app-thunderbird:assembleFullRelease` | Full release (minified; needs signing) |
| `:app-k9mail:assembleDebug` | K-9 Mail debug |

List everything with: `./gradlew :app-thunderbird:tasks --all | grep -i assemble`.

### 4.2 Debug build (typical)

```bash
./gradlew :app-thunderbird:assembleFullDebug     # Thunderbird
./gradlew :app-k9mail:assembleDebug              # K-9 Mail
```
```powershell
.\gradlew.bat :app-thunderbird:assembleFullDebug
```

### 4.3 Compile-only check (faster, no packaging)

To type-check a single module without packaging an APK, use its compile task. **Two naming
schemes exist**, depending on the module type — this trips people up:

- **Android app / plain Android library module** → `compileDebugKotlin`
  ```bash
  ./gradlew :app-common:compileDebugKotlin
  ```
- **Kotlin Multiplatform (KMP) module** (has `androidMain`/`commonMain`) → the Android
  compilation is `compileAndroidMain`, **not** `compileDebugKotlinAndroid`:
  ```bash
  ./gradlew :feature:notification:impl:compileAndroidMain
  ```
  (If unsure which a module is, run `./gradlew :the:module:tasks --all | grep -i compile`.)

### 4.4 Release build

```bash
./gradlew :app-thunderbird:assembleFullRelease
```
Release builds are minified (R8) and expect a signing configuration; without one you get an
unsigned APK, which is fine for local inspection.

### 4.5 Everything / full check

```bash
./gradlew assemble     # compile + package all apps/variants (heavy)
./gradlew build        # assemble + run tests and checks (very heavy)
```

### 4.6 Fully clean, reproducible build

```bash
./gradlew clean :app-thunderbird:assembleFullDebug --no-build-cache --no-configuration-cache --rerun-tasks
```

---

## 5. Output and installing

```
app-thunderbird/build/outputs/apk/full/debug/      # Thunderbird full debug APK(s)
app-thunderbird/build/outputs/apk/foss/debug/      # FOSS debug
app-k9mail/build/outputs/apk/debug/                # K-9 Mail debug
```

Find what was produced without guessing:

```bash
find . -path '*/build/outputs/apk/*' -name '*.apk'
```
```powershell
Get-ChildItem -Recurse -Path . -Filter *.apk | Where-Object FullName -Match 'build.outputs.apk'
```

Install on a connected device/emulator:

```bash
adb install -r app-thunderbird/build/outputs/apk/full/debug/<the-apk>.apk
```

---

## 6. Troubleshooting

### 6.1 Wrong JDK: toolchain errors, `Unsupported class file major version`, Kotlin/AGP complaints

Build with JDK 21+. Check what Gradle actually resolved, then reset the daemon so a new JDK is
picked up:

```bash
./gradlew --version     # look at the "JVM:" line
./gradlew --stop
```
If `JAVA_HOME` is awkward to manage, set `org.gradle.java.home` in `~/.gradle/gradle.properties`
(section 1.1). Remember the bytecode target is Java 17 — that is expected and not an error.

### 6.2 `SDK location not found`

Neither `ANDROID_HOME` nor `local.properties` is set for this shell/checkout. See 1.2. An env
var exported in one terminal does not exist in another, and IDEs do not inherit your shell
profile — `local.properties` is the more reliable option for a machine you return to.

### 6.3 `Failed to find target with hash string 'android-36'` / missing build-tools

```bash
sdkmanager --update
sdkmanager "platforms;android-36" "build-tools;36.0.0"
sdkmanager --licenses
```
If platform 36 is not offered, update the cmdline-tools package itself
(`sdkmanager "cmdline-tools;latest"`) and retry.

### 6.4 `task 'compileDebugKotlinAndroid' not found` (or `assembleDebug` is ambiguous)

Two separate causes:
- A **KMP module** uses `compileAndroidMain`, not `compileDebugKotlinAndroid`. See 4.3.
- `:app-thunderbird` has flavors, so there is no plain `assembleDebug`; use
  `assembleFullDebug` / `assembleFossDebug`. See 4.1.
When in doubt: `./gradlew :the:module:tasks --all | grep -i <compile|assemble>`.

### 6.5 `Cannot lock ... has already been locked` / stuck build

A previous run was killed and left a stale daemon or lock:
```bash
./gradlew --stop
```
```bash
pkill -f GradleDaemon            # Linux/macOS, if it persists
```
```powershell
Get-Process java | Where-Object { $_.Path -like '*gradle*' } | Stop-Process   # Windows
```

### 6.6 Out-of-memory: `Java heap space`, `GC overhead limit exceeded`, or a killed daemon

This is the most common failure on modest machines — it is a large multi-module build. Give
Gradle more heap, or trade speed for a smaller footprint:

```bash
# more heap for this run
./gradlew :app-thunderbird:assembleFullDebug -Dorg.gradle.jvmargs=-Xmx6g

# or a lighter footprint
./gradlew :app-thunderbird:assembleFullDebug --no-parallel --no-daemon -Dorg.gradle.jvmargs=-Xmx4g
```
Persist a larger heap in `~/.gradle/gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8
```
On Linux, a daemon that dies with no message is usually the OOM killer — confirm with
`dmesg | tail`.

### 6.7 Configuration cache / build cache errors after editing build files

After editing `.gradle.kts` files or the version catalog:
```bash
./gradlew :app-thunderbird:assembleFullDebug --no-configuration-cache
```
If results look stale rather than broken: `./gradlew clean … --no-build-cache`.

### 6.8 Code-style / lint checks fail (`spotlessCheck` / ktlint)

`assemble` only compiles and packages, but `build` (and CI) also run formatting/lint checks.
If those fail on style, auto-fix and rebuild:
```bash
./gradlew spotlessApply
```
To skip checks for a quick local build, prefer `assembleFullDebug` over `build`.

### 6.9 First build is very slow

Expected. The first run downloads Gradle 9.6.1 (`-all`), the Android Gradle Plugin, Kotlin,
Compose, and every dependency, then configures 42 modules. Later builds reuse the cache in
your home directory (`~/.gradle`, or `%USERPROFILE%\.gradle`) and are far faster.

### 6.10 Corporate proxy / offline machine

Standard Gradle proxy properties in `~/.gradle/gradle.properties`:
```properties
systemProp.https.proxyHost=proxy.example.com
systemProp.https.proxyPort=8080
systemProp.http.proxyHost=proxy.example.com
systemProp.http.proxyPort=8080
```
For a fully offline build the dependency cache must already be populated; then add `--offline`.

### 6.11 Line endings / scripts on Windows

If Git is configured with `core.autocrlf=true`, shell scripts in the repo may end up CRLF.
That does not affect `gradlew.bat`, but if you build from Git Bash or WSL and see
`bad interpreter`, re-checkout with `core.autocrlf=input`, or build from cmd.exe/PowerShell
using `gradlew.bat`.

### 6.12 Apple Silicon / ARM specifics

Use an **aarch64/ARM64 JDK** and the ARM64 Android command-line tools. Rosetta is not needed.
If you accidentally installed an x64 JDK on Apple Silicon, `java -version` will still run under
Rosetta but is slower — reinstall the ARM64 build. Everything else is identical.

---

## 7. Verifying a checkout builds at all

The shortest end-to-end check from a clean clone:

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew --version                              # confirms JVM 21 and Gradle 9.6.1
./gradlew :app-common:compileDebugKotlin         # type-checks the shared app module
./gradlew :app-thunderbird:assembleFullDebug     # produces the Thunderbird APK
```

If step 2 passes but step 3 fails, the problem is packaging, SDK, or flavor/variant naming —
not the source code.
