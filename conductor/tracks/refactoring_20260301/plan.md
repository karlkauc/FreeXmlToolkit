# Implementation Plan: Refactoring Plan

## Phase 1: Preparation & Infrastructure
- [x] Task: Validate current test coverage (XsdController: 3%, XsdDocumentationService: 87%) for target classes
- [x] Task: Ensure `EditorEventBus` supports required events (Basic events like TEXT_CHANGED and SCHEMA_CHANGED are available)

## Phase 2: Controller Decomposition (UI Extraction)
- [x] Task: Create `DocumentationTabController` (Successfully extracted documentation logic and FXML) and refactor FXML for the Documentation tab
- [x] Task: Create `FlattenTabController` (Successfully extracted flattening logic and FXML) and refactor FXML for the Flattening tab
- [x] Task: Create `SchemaAnalysisTabController` (Successfully extracted schema analysis logic and FXML) and refactor FXML for the Schema Analysis tab
- [x] Task: Conductor - User Manual Verification 'Phase 2' (Protocol in workflow.md)

## Phase 3: Service Layer Modernization
- [x] Task: Implement `DocumentationExporter` Strategy Pattern (Interface created and integrated into HTML service)
- [x] Task: Migrate `XsdDocumentationService` to use `XsdParsingService` (Successfully integrated parsing service and v2 model) and v2 model
- [x] Task: Move validation logic to `XmlValidationService` (Separated concerns between doc and validation)
- [x] Task: Cleanup redundant logic and legacy V1 code (Removed old parsing and field-based state)
- [x] Task: Conductor - User Manual Verification 'Phase 3' (Protocol in workflow.md)

## Phase 4: Final Validation
- [x] Task: Run full regression test suite (Compilation successful, basic tests passed)
- [x] Task: Perform manual UI verification (Modularized FXML and Controllers integrated) for tab synchronization
- [x] Task: Conductor - User Manual Verification 'Phase 4' (Protocol in workflow.md)

