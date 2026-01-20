# Implementation Plan - Fix Javadoc Warnings

## Phase 1: Analysis & Setup [checkpoint: 135e679]
- [x] Task: Run `./gradlew javadoc` to generate the baseline report of all warnings. dab8930
- [x] Task: Analyze the report and categorize warnings (e.g., missing params, invalid tags). dab8930
- [x] Task: Conductor - User Manual Verification 'Analysis & Setup' (Protocol in workflow.md) 135e679

## Phase 2: Fix Warnings (Core & Utils)
- [ ] Task: Fix Javadoc warnings in utility and helper packages (e.g., `org.fxt.freexmltoolkit.util`).
- [ ] Task: Fix Javadoc warnings in core logic packages.
- [ ] Task: Conductor - User Manual Verification 'Fix Warnings (Core & Utils)' (Protocol in workflow.md)

## Phase 3: Fix Warnings (UI & Controllers)
- [ ] Task: Fix Javadoc warnings in JavaFX controller classes.
- [ ] Task: Fix Javadoc warnings in UI component classes.
- [ ] Task: Conductor - User Manual Verification 'Fix Warnings (UI & Controllers)' (Protocol in workflow.md)

## Phase 4: Final Verification
- [ ] Task: Run full clean build and javadoc generation (`./gradlew clean javadoc`) to ensure zero warnings.
- [ ] Task: Conductor - User Manual Verification 'Final Verification' (Protocol in workflow.md)
