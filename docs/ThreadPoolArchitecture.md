# Thread Pool Management Architecture

> **Last Updated:** November 2025 | **Version:** 1.0.0

## Overview

The `ThreadPoolManager` system replaces individual thread creation with a centralized, optimized thread pool management system.

## Architecture Diagram

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
│                            Application Components                               │
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

## Thread Pool Types

### 1. UI Executor

- **Purpose**: Fast UI-related operations
- **Threads**: 2-4 threads (depending on CPU cores)
- **Priority**: High
- **Usage**: Button clicks, UI updates, short calculations

### 2. CPU Intensive Executor

- **Purpose**: Compute-intensive operations
- **Implementation**: ForkJoinPool (Work-Stealing)
- **Threads**: Number of CPU cores
- **Usage**: Syntax highlighting, XML validation, parsing

### 3. I/O Executor

- **Purpose**: I/O-bound operations
- **Threads**: CPU cores * 2
- **Usage**: File access, network requests, schema loading

### 4. Scheduled Executor

- **Purpose**: Timed and delayed operations
- **Threads**: 2 dedicated threads
- **Usage**: Debouncing, periodic updates, cache cleanup

### 5. Background Executor

- **Purpose**: Low-priority background tasks
- **Threads**: 2 threads
- **Priority**: Low
- **Usage**: Cache updates, garbage collection, maintenance tasks

## Performance Improvements

### Before (Individual Threads)

```java
// Old: Individual thread creation
new Thread(() -> {
    // Syntax Highlighting
}).start();

new Thread(() -> {
    // Validation
}).start();

// Various ExecutorServices in different classes
ExecutorService executor1 = Executors.newCachedThreadPool();
ExecutorService executor2 = Executors.newFixedThreadPool(4);
```

### After (Centralized)

```java
// New: Centralized management
threadPoolManager.executeCPUIntensive("syntax-highlighting", () -> {
    // Syntax Highlighting with automatic thread reuse
});

threadPoolManager.executeCPUIntensive("validation", () -> {
    // Validation with optimal thread distribution
});
```

## Benefits

### 1. Resource Efficiency

- Thread reuse reduces overhead
- Optimal thread count based on hardware
- Less memory fragmentation

### 2. Performance Monitoring

- Central statistics for all operations
- Automatic performance metrics
- Proactive bottleneck detection

### 3. Maintainability

- Uniform thread naming conventions
- Centralized configuration
- Easier debugging capabilities

### 4. Scalability

- Automatic adaptation to hardware
- Work-stealing for optimal CPU usage
- Adaptive pool sizes

## Implementation Details

### Thread Naming

```
FXT-UI-1, FXT-UI-2, ...           // UI threads
FXT-CPU-1, FXT-CPU-2, ...         // CPU-intensive threads
FXT-IO-1, FXT-IO-2, ...           // I/O threads
FXT-Scheduled-1, FXT-Scheduled-2  // Scheduled threads
FXT-Background-1, FXT-Background-2 // Background threads
```

### Statistics

```java
ThreadPoolStats {
    tasksSubmitted: 1247,
    tasksCompleted: 1245,
    tasksFailed: 2,
    runningTasks: 3,
    averageExecutionTimeMs: 45
}
```

### Task Management

```java
// Task with ID for cancellation
CompletableFuture<String> future = threadPoolManager
    .executeCPUIntensive("xml-parse-" + documentId, () -> {
        return parseXml(document);
    });

// Task cancellation
threadPoolManager.cancelTask("xml-parse-" + documentId);
```

## Migration Status

The migration was performed step by step:

1. ThreadPoolManager implemented
2. XmlCodeEditor migrated
3. XmlIntelliSenseEngine migrated
4. Tests created
5. Integration in FxtGui
6. Further components (optional)

## Testing

Comprehensive tests cover:

- Singleton behavior
- All thread pool types
- Task cancellation
- Performance monitoring
- Error handling
- Concurrent execution
- Resource cleanup
- Load testing (100+ tasks)

## Configuration

The system configures itself automatically based on:

- **CPU Cores**: `Runtime.getRuntime().availableProcessors()`
- **UI Pool**: `max(2, cores/2)`
- **CPU Pool**: `cores`
- **I/O Pool**: `max(4, cores*2)`
- **Background**: `2`

This configuration can be adjusted if needed.

## Usage Examples

### Submit CPU-Intensive Task

```java
ThreadPoolManager.getInstance().executeCPUIntensive("task-name", () -> {
    // CPU-intensive work
    return result;
});
```

### Submit I/O Task

```java
ThreadPoolManager.getInstance().executeIO("file-read", () -> {
    return Files.readString(path);
});
```

### Schedule Delayed Task

```java
ThreadPoolManager.getInstance().schedule("cleanup", () -> {
    // Cleanup logic
}, 5, TimeUnit.MINUTES);
```

### Execute on UI Thread

```java
ThreadPoolManager.getInstance().executeUI("ui-update", () -> {
    // Update UI component
});
```

## Key Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `ThreadPoolManager` | `service/ThreadPoolManager.java` | Central thread pool management |
| `ThreadPoolStats` | `service/ThreadPoolManager.java` | Statistics data class |
| `FxtGui` | `FxtGui.java` | Application entry, initializes ThreadPoolManager |

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [Templates](template-management.md) | [Home](index.md) | [Technology Stack](technology-stack.md) |

**All Pages:** [XML Editor](xml-controller.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-controller.md) | [XSD Validation](xsd-validation-controller.md) | [XSLT](xslt-controller.md) | [FOP/PDF](fop-controller.md) | [Signatures](signature-controller.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
