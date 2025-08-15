# Windows Installer Bilder

Für den Windows-Installer werden folgende benutzerdefinierte Bilder benötigt:

## Erforderliche Bilder

### 1. `win-banner.png`

- **Größe**: 493x58 Pixel
- **Format**: PNG
- **Verwendung**: Banner am oberen Rand des Installers
- **Empfehlung**: Logo mit transparentem Hintergrund

### 2. `win-dialog.png`

- **Größe**: 493x312 Pixel
- **Format**: PNG
- **Verwendung**: Hauptbild im Installer-Dialog
- **Empfehlung**: Screenshot der Anwendung oder Logo mit Beschreibung

## Bildanforderungen

### Technische Spezifikationen:

- **Format**: PNG (empfohlen) oder JPG
- **Farbtiefe**: 24-bit oder 32-bit (mit Transparenz)
- **Auflösung**: Exakte Pixelmaße einhalten
- **Dateigröße**: Unter 1MB pro Bild

### Design-Empfehlungen:

- **Konsistente Farbgebung** mit dem Hauptlogo
- **Professionelles Design** für seriöses Erscheinungsbild
- **Gute Lesbarkeit** auch bei kleiner Darstellung
- **Branding** entsprechend der Anwendung

## Platzierung

Legen Sie die Bilder im `release/` Verzeichnis ab:

```
release/
├── logo.ico
├── win-banner.png    ← Hier platzieren
├── win-dialog.png    ← Hier platzieren
└── ...
```

## Alternative: Standard-Bilder verwenden

Falls Sie keine benutzerdefinierten Bilder erstellen möchten, können Sie die entsprechenden Zeilen in `build.gradle.kts`
auskommentieren:

```kotlin
// "--win-banner-image", "${projectDir}/release/win-banner.png",
// "--win-dialog-image", "${projectDir}/release/win-dialog.png",
```

## Tools zur Bildbearbeitung

Empfohlene Tools für die Bildbearbeitung:

- **GIMP** (kostenlos)
- **Photoshop** (kommerziell)
- **Canva** (online)
- **Figma** (online)

## Beispiel-Bilder erstellen

### Banner (493x58):

- Logo links, Text rechts
- Einfacher, klarer Stil
- Passend zur Anwendung

### Dialog (493x312):

- Screenshot der Anwendung
- Oder Logo mit Beschreibung
- Professionelles Layout
