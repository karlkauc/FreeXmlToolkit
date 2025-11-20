# FreeXmlToolkit - Native Executables Build Anweisungen

## Übersicht

Diese Anweisungen zeigen, wie Sie native Executables für alle unterstützten Betriebssysteme erstellen können. Alle
erstellten Pakete sind für benutzerbezogene Installationen konfiguriert und benötigen keine Administratorrechte.

## Voraussetzungen

1. **Java 23 JDK** - Stellen Sie sicher, dass Sie Java 23 installiert haben
2. **Gradle** - Verwenden Sie die mitgelieferte Gradle Wrapper (`./gradlew`)
3. **Betriebssystem-spezifische Tools**:
    - **Windows**: Keine zusätzlichen Tools erforderlich
    - **macOS**: Xcode Command Line Tools (`xcode-select --install`)
    - **Linux**: `fakeroot` und `dpkg` für DEB-Pakete, `rpmbuild` für RPM-Pakete

## Verfügbare Build-Tasks

### Alle Executables erstellen

```bash
./gradlew createAllExecutables
```

### Betriebssystem-spezifische Tasks

#### Windows

```bash
./gradlew createWindowsExecutable
```

- Erstellt: `FreeXmlToolkit.exe`
- Installation: Benutzerbezogen (keine Admin-Rechte erforderlich)
- Installationsort: `%LOCALAPPDATA%\FreeXmlToolkit`

#### macOS

```bash
./gradlew createMacOSExecutable
```

- Erstellt: `FreeXmlToolkit.dmg`
- Installation: Drag & Drop in `/Applications` oder `~/Applications`
- Benutzerbezogene Installation möglich

**⚠️ WICHTIG für macOS:** Die DMG-Datei muss **code-signiert** werden, um Sicherheitswarnungen zu vermeiden.

**Lösungen:**
- **Nur testen (lokal):** `./gradlew signMacOSExecutableArm64AdHoc` (kostenlos)
- **Verteilen:** Siehe [macOS Code Signing Guide](docs/MACOS_CODE_SIGNING.md) (erfordert Apple Developer Account)

Ausführliche Anleitung: [docs/MACOS_CODE_SIGNING.md](docs/MACOS_CODE_SIGNING.md)

#### Linux

```bash
# AppImage (empfohlen für benutzerbezogene Installation)
./gradlew createLinuxExecutable

# DEB Package
./gradlew createLinuxDebPackage

# RPM Package
./gradlew createLinuxRpmPackage
```

## Ausgabe-Verzeichnisse

Alle erstellten Dateien werden in folgende Verzeichnisse gespeichert:

- **Executables**: `build/dist/`
- **JLink Image**: `build/image/`
- **ZIP-Archiv**: `build/FreeXMLToolkit.zip`

## Installationsanweisungen für Endbenutzer

### Windows

1. Doppelklick auf `FreeXmlToolkit.exe`
2. Folgen Sie dem Installationsassistenten
3. Die Anwendung wird automatisch im Startmenü registriert

### macOS

1. Öffnen Sie die `FreeXmlToolkit.dmg` Datei
2. Ziehen Sie die App in den `Applications` Ordner
3. Die App ist sofort einsatzbereit

### Linux

#### AppImage (empfohlen)

1. Laden Sie die `.AppImage` Datei herunter
2. Machen Sie sie ausführbar: `chmod +x FreeXmlToolkit.AppImage`
3. Führen Sie sie aus: `./FreeXmlToolkit.AppImage`

#### DEB/RPM Package

```bash
# DEB (Ubuntu/Debian)
sudo dpkg -i FreeXmlToolkit.deb

# RPM (Fedora/RHEL)
sudo rpm -i FreeXmlToolkit.rpm
```

## Konfiguration

### Anpassung der Build-Parameter

Sie können die Build-Parameter in `build.gradle.kts` anpassen:

- **Vendor**: `--vendor "Ihr Name"`
- **Version**: `--app-version "1.0.0"`
- **Icon**: `--icon "path/to/icon.ico"`
- **Installationsverzeichnis**: `--install-dir "/custom/path"`

### Icons

Stellen Sie sicher, dass die Icons in den richtigen Formaten vorhanden sind:

- **Windows**: `.ico` Datei
- **macOS**: `.icns` Datei (wird automatisch aus .ico konvertiert)
- **Linux**: `.ico` oder `.png` Datei

## Troubleshooting

### Häufige Probleme

1. **"jpackage command not found"**
    - Stellen Sie sicher, dass Java 23 installiert ist
    - `jpackage` ist Teil des JDK, nicht des JRE

2. **Linux: "fakeroot command not found"**
   ```bash
   # Ubuntu/Debian
   sudo apt-get install fakeroot
   
   # Fedora/RHEL
   sudo dnf install fakeroot
   ```

3. **macOS: "xcode-select command not found"**
   ```bash
   xcode-select --install
   ```

4. **Windows: "Access denied"**
    - Führen Sie die Kommandozeile als Administrator aus
    - Oder verwenden Sie `--win-per-user-install` (bereits konfiguriert)

### Build-Logs

Für detaillierte Build-Informationen:

```bash
./gradlew createAllExecutables --info
```

## Distribution

Nach erfolgreichem Build finden Sie alle Dateien in:

- `build/dist/` - Native Executables
- `build/FreeXMLToolkit.zip` - Komplettes Archiv

Die erstellten Pakete können direkt an Endbenutzer verteilt werden und benötigen keine zusätzliche Java-Installation.
