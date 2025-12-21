# Spec: Large File Performance Optimization and Stability

## Overview
As FreeXmlToolkit aims to handle very large XML files (hundreds of megabytes), the current implementation needs optimization to ensure the UI remains responsive and memory usage stays within reasonable limits.

## Goals
- **Background Validation:** Move XML/XSD validation from the UI thread to background threads.
- **Memory-Efficient Rendering:** Optimize the `XmlCanvasView` and `XmlVisualTreeBuilder` to handle large documents without freezing.
- **Progress Visibility:** Provide clear feedback to the user during long-running background tasks.

## Technical Requirements
- Use `ThreadPoolManager` for background task execution.
- Implement streaming or lazy loading strategies where applicable.
- Ensure thread safety when updating JavaFX UI components from background threads using `Platform.runLater()`.

## Acceptance Criteria
- Files up to 100MB can be opened and validated without the UI freezing for more than 100ms.
- Validation results are displayed as they become available.
- Progress bar correctly reflects the status of background operations.
