# Specification: Refactoring Refactoring Plan

This track implements the decomposition of the `XsdController` and `XsdDocumentationService` classes as outlined in `docs/REFACTORING_PLAN.md`.

## Goals
- Decompose the `XsdController` (6,600+ lines) into modular sub-controllers.
- Refactor the `XsdDocumentationService` (3,900+ lines) using a strategy pattern for multiple output formats.
- Standardize on `XsdParsingService` and the `v2` model.
- Decouple the UI from business logic using the `EditorEventBus`.

## Key Changes
- Introduction of `DocumentationTabController`, `FlattenTabController`, and `SchemaAnalysisTabController`.
- Implementation of `DocumentationExporter` interface and format-specific exporters (HTML, PDF, etc.).
- Move validation logic to `XmlValidationService`.

