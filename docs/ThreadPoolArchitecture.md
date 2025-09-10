# Thread Pool Management Architecture

## Übersicht

Das neue `ThreadPoolManager` System ersetzt die individuellen Thread-Erstellungen durch ein zentralisiertes, optimiertes
Thread Pool Management System.

## Architektur-Diagramm

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            ThreadPoolManager (Singleton)                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │   UI Executor   │  │  CPU Intensive  │  │  I/O Executor   │                │
│  │   (High Prio)   │  │   (Work Steal)  │  │   (Many Conc)   │                │
│  │   2-4 Threads   │  │   CPU Threads   │  │   CPU*2 Thread  │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐                                      │
│  │   Scheduled     │  │   Background    │                                      │
│  │   (2 Threads)   │  │   (Low Prio)    │                                      │
│  │                 │  │   2 Threads     │                                      │
│  └─────────────────┘  └─────────────────┘                                      │
│                                                                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                         Performance Monitoring                                  │
│  • Task Submission/Completion Counters    • Execution Time Tracking           │
│  • Error Rate Monitoring                  • Active Thread Count               │
│  • Queue Size Monitoring                  • Automatic Cache Cleanup           │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            Anwendungskomponenten                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │  XmlCodeEditor  │  │ IntelliSense    │  │ MinimapView     │                │
│  │  • Syntax High  │  │ • Element Comp  │  │ • Render Tasks  │                │
│  │  • Validation   │  │ • Attr Comp     │  │                 │                │
│  │  • Code Folding │  │ • Doc Tooltips  │  │                 │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                │
│  │ ProgressManager │  │ ErrorRecovery   │  │ SchematronDoc   │                │
│  │ • Progress UI   │  │ • Retry Logic   │  │ • Doc Gen       │                │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Thread Pool Typen

### 1. UI Executor

- **Zweck**: Schnelle UI-bezogene Operationen
- **Threads**: 2-4 Threads (abhängig von CPU-Kernen)
- **Priorität**: Hoch
- **Verwendung**: Button-Clicks, UI-Updates, kurze Berechnungen

### 2. CPU Intensive Executor

- **Zweck**: Rechenintensive Operationen
- **Implementation**: ForkJoinPool (Work-Stealing)
- **Threads**: Anzahl CPU-Kerne
- **Verwendung**: Syntax Highlighting, XML-Validierung, Parsing

### 3. I/O Executor

- **Zweck**: I/O-gebundene Operationen
- **Threads**: CPU-Kerne * 2
- **Verwendung**: Dateizugriff, Netzwerkanfragen, Schema-Loading

### 4. Scheduled Executor

- **Zweck**: Zeitgesteuerte und verzögerte Operationen
- **Threads**: 2 dedizierte Threads
- **Verwendung**: Debouncing, periodische Updates, Cache-Cleanup

### 5. Background Executor

- **Zweck**: Niedrigprioritäre Hintergrundaufgaben
- **Threads**: 2 Threads
- **Priorität**: Niedrig
- **Verwendung**: Cache-Updates, Garbage Collection, Wartungsaufgaben

## Performance-Verbesserungen

### Vorher (Individual Threads)

```java
// Alt: Individuelle Thread-Erstellung
new Thread(() -> {
    // Syntax Highlighting
}).start();

new Thread(() -> {
    // Validation
}).start();

// Verschiedene ExecutorServices in verschiedenen Klassen
ExecutorService executor1 = Executors.newCachedThreadPool();
ExecutorService executor2 = Executors.newFixedThreadPool(4);
```

### Nachher (Zentralisiert)

```java
// Neu: Zentralisiertes Management
threadPoolManager.executeCPUIntensive("syntax-highlighting", () -> {
    // Syntax Highlighting mit automatischem Thread-Reuse
});

threadPoolManager.executeCPUIntensive("validation", () -> {
    // Validation mit optimaler Thread-Verteilung
});
```

## Vorteile

### 1. **Ressourcen-Effizienz**

- Thread-Wiederverwendung reduziert Overhead
- Optimale Thread-Anzahl basierend auf Hardware
- Weniger Memory-Fragmentation

### 2. **Performance-Monitoring**

- Zentrale Statistiken für alle Operationen
- Automatische Performance-Metriken
- Proaktive Bottleneck-Erkennung

### 3. **Wartbarkeit**

- Einheitliche Thread-Namenskonventionen
- Zentralisierte Konfiguration
- Einfachere Debugging-Möglichkeiten

### 4. **Skalierbarkeit**

- Automatische Anpassung an Hardware
- Work-Stealing für optimale CPU-Nutzung
- Adaptive Pool-Größen

## Implementierungsdetails

### Thread-Benennung

```
FXT-UI-1, FXT-UI-2, ...           // UI-Threads
FXT-CPU-1, FXT-CPU-2, ...         // CPU-intensive Threads  
FXT-IO-1, FXT-IO-2, ...           // I/O-Threads
FXT-Scheduled-1, FXT-Scheduled-2  // Scheduled Threads
FXT-Background-1, FXT-Background-2 // Background Threads
```

### Statistiken

```java
ThreadPoolStats {
    1247,
            tasksCompleted: 1245,
    tasksFailed: 2,
    runningTasks: 3,
    averageExecutionTimeMs: 45
}
```

### Task-Verwaltung

```java
// Task mit ID für Cancellation
CompletableFuture<String> future = threadPoolManager
    .executeCPUIntensive("xml-parse-" + documentId, () -> {
        return parseXml(document);
    });

// Task cancellation
threadPoolManager.cancelTask("xml-parse-" + documentId);
```

## Migration

Die Migration erfolgte schrittweise:

1. ✅ ThreadPoolManager implementiert
2. ✅ XmlCodeEditor migriert
3. ✅ XmlIntelliSenseEngine migriert
4. ✅ Tests erstellt
5. ✅ Integration in FxtGui
6. 🔄 Weitere Komponenten (optional)

## Testing

Umfassende Tests decken ab:

- ✅ Singleton-Verhalten
- ✅ Alle Thread Pool-Typen
- ✅ Task-Cancellation
- ✅ Performance-Monitoring
- ✅ Error-Handling
- ✅ Concurrent Execution
- ✅ Resource Cleanup
- ✅ Load Testing (100+ Tasks)

## Konfiguration

Das System konfiguriert sich automatisch basierend auf:

- **CPU-Kerne**: `Runtime.getRuntime().availableProcessors()`
- **UI Pool**: `max(2, cores/2)`
- **CPU Pool**: `cores`
- **I/O Pool**: `max(4, cores*2)`
- **Background**: `2`

Diese Konfiguration kann bei Bedarf angepasst werden.