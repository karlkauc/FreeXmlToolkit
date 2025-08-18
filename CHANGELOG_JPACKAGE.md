# JPackage Extensions Changelog

## Version 1.0.0 - JPackage Complete Implementation

### Hinzugefügt

#### Neue jpackage Tasks

**Windows Tasks:**
- `createWindowsMsi` - Windows MSI Installer für systemweite Installation
- `createWindowsAppImage` - Windows App Image (portable Version)

**macOS Tasks:**
- `createMacOSPkg` - macOS PKG Installer für systemweite Installation
- `createMacOSAppImage` - macOS App Bundle (portable Version)

**Linux Tasks:**
- `createLinuxAppImage` - Linux AppImage für universelle Distributionen
- `createLinuxTar` - Linux tar.gz Archiv (portable Version)

#### Convenience Tasks

**Plattformspezifische Tasks:**
- `createWindowsPackages` - Alle Windows-Pakete (exe, msi, app-image)
- `createMacOSPackages` - Alle macOS-Pakete (dmg, pkg, app-image)
- `createLinuxPackages` - Alle Linux-Pakete (deb, rpm, app-image, tar.gz)

**Kategorienspezifische Tasks:**
- `createAllInstallers` - Alle Installer-Pakete (exe, msi, dmg, pkg, deb, rpm)
- `createAllAppImages` - Alle App-Image-Pakete (portable Versionen)

#### Erweiterte `createAllExecutables` Task

Die bestehende `createAllExecutables` Task wurde erweitert und erstellt jetzt alle verfügbaren Paketformate:

**Vorher (4 Tasks):**
- createWindowsExecutable
- createMacOSExecutable  
- createLinuxDeb
- createLinuxRpm

**Nachher (10 Tasks):**
- createWindowsExecutable
- createWindowsMsi
- createWindowsAppImage
- createMacOSExecutable
- createMacOSPkg
- createMacOSAppImage
- createLinuxDeb
- createLinuxRpm
- createLinuxAppImage
- createLinuxTar

### Verfügbare Paketformate

| Plattform | Installer | Portable | Enterprise |
|-----------|-----------|----------|------------|
| Windows | .exe | App Image | .msi |
| macOS | .dmg | App Bundle | .pkg |
| Linux | .deb/.rpm | AppImage/.tar.gz | .deb/.rpm |

### Verwendung

```bash
# Alle Pakete erstellen
./gradlew createAllExecutables

# Nur Installer
./gradlew createAllInstallers

# Nur portable Versionen
./gradlew createAllAppImages

# Plattformspezifisch
./gradlew createWindowsPackages
./gradlew createMacOSPackages
./gradlew createLinuxPackages
```

### Dokumentation

- `JPACKAGE_TASKS.md` - Vollständige Dokumentation aller jpackage Tasks
- `CHANGELOG_JPACKAGE.md` - Diese Änderungsliste

### Technische Details

- Alle Tasks verwenden das gleiche Runtime Image
- Konsistente Konfiguration über alle Plattformen
- Plattformspezifische Optimierungen (Icons, Metadaten)
- Automatische Abhängigkeitsverwaltung
- Fehlerbehandlung und Validierung
