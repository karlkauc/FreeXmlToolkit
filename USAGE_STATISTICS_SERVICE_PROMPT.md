# Prompt: Usage Statistics REST Service for FreeXmlToolkit

## Kontext

FreeXmlToolkit ist eine Open-Source JavaFX-Desktop-Anwendung fuer XML/XSD-Bearbeitung. Die App sammelt bereits lokal Usage-Statistiken (Feature-Nutzung, taegliche Metriken, Productivity-Score). Es gibt im Settings-Dialog einen Toggle "Send anonymous usage statistics", der bisher keine Funktion hat.

**Ziel:** Ein separates REST-Service-Projekt, das anonyme Nutzungsstatistiken von FreeXmlToolkit-Installationen empfaengt, speichert und auswertbar macht.

---

## Aufgabe

Erstelle ein neues Spring Boot 3 Projekt mit dem Namen `freexmltoolkit-analytics` mit folgender Funktionalitaet:

### 1. REST API - Datenempfang

Erstelle REST-Endpoints zum Empfangen anonymer Nutzungsstatistiken:

```
POST /api/v1/statistics        - Empfaengt aggregierte Statistiken einer Installation
POST /api/v1/statistics/event  - Empfaengt einzelne Events in Echtzeit (optional)
GET  /api/v1/health            - Health-Check
```

**Payload fuer POST /api/v1/statistics:**
```json
{
  "installationId": "uuid-v4 (anonymisiert, wird beim ersten Start generiert)",
  "appVersion": "1.5.0",
  "osName": "Windows 11",
  "osArch": "amd64",
  "javaVersion": "25",
  "locale": "de_DE",
  "reportingPeriod": {
    "from": "2026-02-01",
    "to": "2026-02-23"
  },
  "metrics": {
    "totalLaunches": 45,
    "totalUsageMinutes": 1240,
    "filesValidated": 230,
    "validationErrors": 89,
    "errorsCorrected": 67,
    "transformationsPerformed": 15,
    "documentsFormatted": 98,
    "xpathQueriesExecuted": 44,
    "xqueryExecutions": 12,
    "schematronValidations": 8,
    "schemasGenerated": 3,
    "signaturesCreated": 2,
    "signaturesVerified": 5,
    "pdfsGenerated": 7,
    "filesOpened": 156
  },
  "featureUsage": {
    "xml_validation": { "useCount": 230, "discovered": true },
    "xml_formatting": { "useCount": 98, "discovered": true },
    "xsd_validation": { "useCount": 45, "discovered": true },
    "xsd_visualization": { "useCount": 30, "discovered": true },
    "xpath_queries": { "useCount": 44, "discovered": true },
    "xquery_execution": { "useCount": 12, "discovered": true },
    "xslt_transformation": { "useCount": 15, "discovered": true },
    "schematron_validation": { "useCount": 8, "discovered": true },
    "schema_generation": { "useCount": 3, "discovered": true },
    "digital_signature": { "useCount": 7, "discovered": true },
    "pdf_generation": { "useCount": 7, "discovered": true },
    "batch_validation": { "useCount": 0, "discovered": false },
    "intellisense": { "useCount": 120, "discovered": true },
    "xsd_documentation": { "useCount": 5, "discovered": true }
  },
  "dailyActiveMinutes": {
    "2026-02-20": 65,
    "2026-02-21": 120,
    "2026-02-22": 45,
    "2026-02-23": 80
  }
}
```

### 2. Datenbank-Schema (PostgreSQL)

Erstelle JPA Entities fuer:

**installation_reports** - Jeder Bericht einer Installation:
- `id` (UUID, PK)
- `installation_id` (UUID, anonyme Installations-ID)
- `app_version` (String)
- `os_name`, `os_arch`, `java_version`, `locale` (String)
- `reporting_period_from`, `reporting_period_to` (LocalDate)
- `received_at` (LocalDateTime)
- Alle Metriken als einzelne Spalten (fuer effiziente Queries)

**feature_usage_reports** - Feature-Nutzung pro Bericht:
- `id` (UUID, PK)
- `report_id` (FK zu installation_reports)
- `feature_id` (String)
- `use_count` (int)
- `discovered` (boolean)

**daily_activity** - Taegliche Aktivitaet pro Bericht:
- `id` (UUID, PK)
- `report_id` (FK zu installation_reports)
- `activity_date` (LocalDate)
- `active_minutes` (int)

### 3. Auswertungs-Endpoints (Analytics API)

```
GET /api/v1/analytics/overview
```
Liefert:
- Gesamtanzahl aktiver Installationen (letzte 30 Tage)
- Gesamtanzahl Installationen insgesamt
- Verteilung nach App-Version
- Verteilung nach OS
- Verteilung nach Locale/Sprache

```
GET /api/v1/analytics/features
```
Liefert:
- Beliebteste Features (sortiert nach Nutzungshaeufigkeit)
- Feature-Discovery-Rate (Prozent der Installationen, die ein Feature entdeckt haben)
- Am wenigsten genutzte Features
- Trend der Feature-Nutzung ueber Zeit

```
GET /api/v1/analytics/usage-trends?period=30d|90d|1y
```
Liefert:
- Taegliche/woechentliche/monatliche aktive Installationen (DAU/WAU/MAU)
- Durchschnittliche Nutzungsdauer pro Session
- Gesamtmetriken im Zeitverlauf (Validierungen, Transformationen etc.)
- Wachstumstrend (neue Installationen pro Woche)

```
GET /api/v1/analytics/versions
```
Liefert:
- Aktive Versionen mit Installationszahlen
- Migrations-Rate auf neue Versionen
- Version-Adoption-Kurve

```
GET /api/v1/analytics/retention
```
Liefert:
- Retention-Rate (Installationen die nach 7/30/90 Tagen noch aktiv sind)
- Churn-Rate
- Durchschnittliche Lebensdauer einer Installation

### 4. Dashboard (Web-UI)

Erstelle ein einfaches Web-Dashboard mit **Thymeleaf + Chart.js** (oder htmx):

- **Uebersichtsseite:** Aktive Installationen, Version-Verteilung, OS-Verteilung
- **Feature-Analyse:** Bar-Charts der Feature-Nutzung, Discovery-Rates
- **Trends:** Line-Charts fuer DAU/MAU, Nutzungsmetriken ueber Zeit
- **Versionen:** Version-Adoption-Kurve, Migrations-Fortschritt
- **Retention:** Retention-Kurven, Kohortenanalyse

Das Dashboard soll ohne Login zugaenglich sein (die Daten sind anonymisiert und unkritisch).

### 5. Datenschutz & Sicherheit

- **Keine persoenlichen Daten:** Kein Username, keine Email, keine IP-Speicherung
- **Installation-ID:** UUID, wird lokal generiert, nicht rueckfuehrbar auf Person
- **Rate-Limiting:** Max 1 Report pro Installation pro Stunde
- **Validierung:** Alle Eingaben validieren, keine SQL-Injection etc.
- **CORS:** Nur POST von FreeXmlToolkit-Clients erlauben (kein Browser-Origin noetig, da Desktop-App)
- **Optional:** API-Key pro App-Version fuer minimale Authentifizierung

### 6. Client-Integration (FreeXmlToolkit-Seite)

Erstelle im FreeXmlToolkit-Projekt einen `UsageStatisticsReportService` der:

- Bei aktiviertem "Send anonymous usage statistics" Toggle taeglich (oder woechentlich) die lokalen Statistiken an den REST-Service sendet
- Beim ersten Aufruf eine stabile `installationId` (UUID) generiert und in den Properties speichert
- Fehler still schluckt (darf die App niemals beeintraechtigen)
- Im Hintergrund-Thread laeuft
- Die Server-URL konfigurierbar macht (Default: die produktive URL)
- Die App-Version, OS-Info und Locale automatisch mitsendet

### 7. Technische Anforderungen

- **Java 21** (LTS, breit unterstuetzt fuer Server)
- **Spring Boot 3.4.x**
- **Spring Data JPA** + **PostgreSQL** (Production) / **H2** (Development/Tests)
- **Gradle** Build-System (wie das Hauptprojekt)
- **Docker** + **docker-compose.yml** fuer lokale Entwicklung
- **Flyway** fuer Datenbank-Migrationen
- **SpringDoc OpenAPI** fuer automatische API-Dokumentation
- **Spring Boot Actuator** fuer Health-Checks
- **Testcontainers** fuer Integrationstests mit PostgreSQL
- **GitHub Actions** CI/CD Pipeline
- **Dockerfile** fuer Produktion (multi-stage build)

### 8. Projekt-Struktur

```
freexmltoolkit-analytics/
├── src/main/java/org/fxt/freexmltoolkit/analytics/
│   ├── AnalyticsApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── CorsConfig.java
│   │   └── RateLimitConfig.java
│   ├── controller/
│   │   ├── StatisticsController.java      # POST endpoints
│   │   ├── AnalyticsController.java       # GET analytics endpoints
│   │   └── DashboardController.java       # Thymeleaf dashboard
│   ├── dto/
│   │   ├── StatisticsReportDto.java       # Eingehender Report
│   │   ├── AnalyticsOverviewDto.java      # Auswertung: Uebersicht
│   │   ├── FeatureAnalyticsDto.java       # Auswertung: Features
│   │   ├── UsageTrendDto.java             # Auswertung: Trends
│   │   └── RetentionDto.java              # Auswertung: Retention
│   ├── entity/
│   │   ├── InstallationReport.java
│   │   ├── FeatureUsageReport.java
│   │   └── DailyActivity.java
│   ├── repository/
│   │   ├── InstallationReportRepository.java
│   │   ├── FeatureUsageReportRepository.java
│   │   └── DailyActivityRepository.java
│   ├── service/
│   │   ├── StatisticsIngestionService.java  # Report entgegennehmen & speichern
│   │   ├── AnalyticsService.java            # Auswertungen berechnen
│   │   └── RetentionService.java            # Retention-Analyse
│   └── validation/
│       └── ReportValidator.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── db/migration/                        # Flyway migrations
│   │   └── V1__initial_schema.sql
│   └── templates/                           # Thymeleaf
│       ├── dashboard.html
│       ├── features.html
│       ├── trends.html
│       └── fragments/
├── src/test/java/
│   ├── controller/
│   ├── service/
│   └── repository/                          # Testcontainers
├── Dockerfile
├── docker-compose.yml
├── build.gradle.kts
└── README.md
```

### 9. Deployment-Optionen (kostenlos/guenstig)

Da es Open Source ohne Einnahmen ist:

| Anbieter | Free-Tier | PostgreSQL | Bemerkung |
|----------|-----------|------------|-----------|
| **Render** | 750h/Monat | Nein (extern) | Spin-down nach Inaktivitaet |
| **Railway** | $5 Credit/Monat | Ja (integriert) | Einfachstes Setup |
| **Fly.io** | 3 shared VMs | Ja (1GB free) | Gute Performance |
| **Supabase** | - | 500MB free DB | Nur DB, App woanders |
| **Neon** | - | 512MB free DB | Serverless PostgreSQL |

**Empfehlung:** Fly.io (App) + Neon (PostgreSQL) = komplett kostenlos.

### 10. Spaetere Erweiterungen (nicht im ersten Release)

- **Export:** CSV/JSON-Export der Auswertungen
- **Alerts:** Benachrichtigung bei ungewoehnlichen Mustern (z.B. plotzlicher Rueckgang)
- **A/B-Testing:** Feature-Flags fuer FreeXmlToolkit Features
- **Public Stats Page:** Oeffentliche Statistik-Seite (wie plausible.io) fuer Transparenz
- **Grafana-Integration:** Prometheus-Metriken fuer fortgeschrittenes Monitoring

---

## Existierende Datenmodelle im Client (Referenz)

Die folgenden Klassen existieren bereits in FreeXmlToolkit und definieren die Datenstruktur:

### UsageStatistics.java (Hauptcontainer)
```java
// Kumulative Metriken
int filesValidated, validationErrors, errorsCorrected
int transformationsPerformed, documentsFormatted
int xpathQueriesExecuted, xqueryExecutions
int schematronValidations, schemasGenerated
int signaturesCreated, signaturesVerified, pdfsGenerated, filesOpened

// Zeitbezogen
LocalDateTime firstLaunch, lastLaunch
long totalUsageSeconds
int totalLaunches

// Taegliche Aufschluesselung (letzte 30 Tage)
Map<LocalDate, DailyStatistics> dailyStats

// Feature-Nutzung (14 Features)
Map<String, FeatureUsage> featureUsage
```

### DailyStatistics.java (Pro Tag)
```java
LocalDate date
int filesValidated, errorsFound, errorsCorrected
int transformations, formattings, xpathQueries
int schematronValidations, usageMinutes
```

### FeatureUsage.java (Pro Feature)
```java
String id, name, category
int useCount
boolean discovered
LocalDateTime firstUsed, lastUsed
```

### 14 getrackte Features:
1. xml_validation, xml_formatting
2. xsd_validation, xsd_visualization
3. xpath_queries, xquery_execution
4. xslt_transformation, schematron_validation
5. schema_generation, digital_signature
6. pdf_generation, batch_validation
7. intellisense, xsd_documentation

---

## Wichtige Hinweise

- **Privacy by Design:** Keine personenbezogenen Daten sammeln
- **Opt-in:** Statistiken werden nur gesendet wenn der User explizit zustimmt
- **Graceful Degradation:** Wenn der Server nicht erreichbar ist, darf die App nicht beeintraechtigt werden
- **Idempotent:** Doppelte Reports (gleiche Installation + gleicher Zeitraum) sollen erkannt und dedupliziert werden
- **Open Source:** Das Analytics-Projekt soll ebenfalls Open Source sein (Apache 2.0 oder MIT Lizenz)
