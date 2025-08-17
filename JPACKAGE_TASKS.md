# JPackage Tasks Documentation

Dieses Dokument beschreibt alle verfügbaren jpackage Tasks für die FreeXmlToolkit-Anwendung.

## Übersicht

Die `build.gradle.kts` Datei enthält jetzt alle verfügbaren jpackage Formate für Windows, macOS und Linux. Jeder Task
erstellt ein spezifisches Paketformat mit entsprechenden Eigenschaften.

## Verfügbare Tasks

### Windows Tasks

| Task                      | Format    | Beschreibung                                         | Verwendung                 |
|---------------------------|-----------|------------------------------------------------------|----------------------------|
| `createWindowsExecutable` | `.exe`    | Windows Executable für benutzerbezogene Installation | Standard Windows Installer |
| `createWindowsMsi`        | `.msi`    | Windows MSI Installer für systemweite Installation   | Enterprise Deployment      |
| `createWindowsAppImage`   | App Image | Windows App Image (ohne Installer)                   | Portable Version           |

### macOS Tasks

| Task                    | Format     | Beschreibung                      | Verwendung                  |
|-------------------------|------------|-----------------------------------|-----------------------------|
| `createMacOSExecutable` | `.dmg`     | macOS DMG Installer               | Standard macOS Installation |
| `createMacOSPkg`        | `.pkg`     | macOS PKG Installer               | Systemweite Installation    |
| `createMacOSAppImage`   | App Bundle | macOS App Bundle (ohne Installer) | Portable Version            |

### Linux Tasks

| Task                  | Format    | Beschreibung        | Verwendung                       |
|-----------------------|-----------|---------------------|----------------------------------|
| `createLinuxDeb`      | `.deb`    | Debian/Ubuntu Paket | Debian-basierte Distributionen   |
| `createLinuxRpm`      | `.rpm`    | Red Hat Paket       | Red Hat-basierte Distributionen  |
| `createLinuxAppImage` | AppImage  | Linux AppImage      | Universelle Linux Distributionen |
| `createLinuxTar`      | `.tar.gz` | Tar Archive         | Portable Linux Version           |

## Convenience Tasks

### Plattformspezifische Tasks

- **`createWindowsPackages`**: Erstellt alle Windows-Pakete (exe, msi, app-image)
- **`createMacOSPackages`**: Erstellt alle macOS-Pakete (dmg, pkg, app-image)
- **`createLinuxPackages`**: Erstellt alle Linux-Pakete (deb, rpm, app-image, tar.gz)

### Kategorienspezifische Tasks

- **`createAllInstallers`**: Erstellt alle Installer-Pakete (exe, msi, dmg, pkg, deb, rpm)
- **`createAllAppImages`**: Erstellt alle App-Image-Pakete (portable Versionen)
- **`createAllExecutables`**: Erstellt alle verfügbaren Pakete

## Verwendung

### Alle Pakete erstellen

```bash
./gradlew createAllExecutables
```

### Nur Installer erstellen

```bash
./gradlew createAllInstallers
```

### Nur portable Versionen erstellen

```bash
./gradlew createAllAppImages
```

### Plattformspezifische Pakete erstellen

```bash
# Windows (nur auf Windows)
./gradlew createWindowsPackages

# macOS (nur auf macOS)
./gradlew createMacOSPackages

# Linux (nur auf Linux)
./gradlew createLinuxPackages
```

### Einzelne Pakete erstellen

```bash
# Windows
./gradlew createWindowsExecutable
./gradlew createWindowsMsi
./gradlew createWindowsAppImage

# macOS
./gradlew createMacOSExecutable
./gradlew createMacOSPkg
./gradlew createMacOSAppImage

# Linux
./gradlew createLinuxDeb
./gradlew createLinuxRpm
./gradlew createLinuxAppImage
./gradlew createLinuxTar
```

## Voraussetzungen

### Allgemein

- Java 21 JDK mit jpackage Tool
- Gradle Build System
- Korrekte JavaFX Module (bereits in jmods/ Verzeichnis)

### Plattformspezifisch

#### Windows

- Windows Betriebssystem
- WiX Toolset (für MSI)
- Administratorrechte für systemweite Installation

#### macOS

- macOS Betriebssystem
- Apple Developer Certificate (für App Store Distribution)
- Xcode Command Line Tools

#### Linux

- Linux Betriebssystem
- dpkg (für DEB Pakete)
- rpmbuild (für RPM Pakete)
- AppImage Tools (für AppImage)

## Ausgabe

Alle erstellten Pakete werden im `build/dist/` Verzeichnis gespeichert:

```
build/dist/
├── FreeXmlToolkit-1.0.0.exe          # Windows Executable
├── FreeXmlToolkit-1.0.0.msi          # Windows MSI
├── FreeXmlToolkit-1.0.0.dmg          # macOS DMG
├── FreeXmlToolkit-1.0.0.pkg          # macOS PKG
├── freexmltoolkit_1.0.0_amd64.deb    # Linux DEB
├── freexmltoolkit-1.0.0-1.x86_64.rpm # Linux RPM
├── FreeXmlToolkit-1.0.0.tar.gz       # Linux TAR
└── FreeXmlToolkit/                   # App Images (Verzeichnisse)
    ├── FreeXmlToolkit.exe            # Windows App Image
    ├── FreeXmlToolkit.app            # macOS App Image
    └── FreeXmlToolkit                # Linux App Image
```

## Troubleshooting

### Häufige Probleme

1. **"jpackage command not found"**
    - Stellen Sie sicher, dass Java 21 JDK installiert ist
    - Überprüfen Sie JAVA_HOME Umgebungsvariable

2. **"Runtime image not found"**
    - Führen Sie zuerst `./gradlew createWindowsRuntimeImage` (oder entsprechende Plattform) aus
    - Stellen Sie sicher, dass JavaFX Module verfügbar sind

3. **"Permission denied"**
    - Verwenden Sie Administratorrechte für systemweite Installationen
    - Überprüfen Sie Dateiberechtigungen im build/ Verzeichnis

4. **"Icon file not found"**
    - Stellen Sie sicher, dass die Icon-Dateien im release/ Verzeichnis vorhanden sind
    - Verwenden Sie die korrekten Icon-Formate (.ico für Windows, .icns für macOS)

### Debugging

Aktivieren Sie Gradle Debug-Ausgabe:

```bash
./gradlew createWindowsExecutable --debug
```

Überprüfen Sie die jpackage Logs:

```bash
./gradlew createWindowsExecutable --info
```
