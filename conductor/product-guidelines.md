# Product Guidelines - FreeXmlToolkit

## Design & Experience
*   **Professional & Clean Aesthetics:** Maintain a minimalist, productivity-focused interface using modern UI frameworks (like AtlantaFX). Use a neutral color palette and clear typography to minimize distraction.
*   **Accessibility & Helpfulness:** Use plain, actionable language in tooltips, status bars, and error messages. The goal is to guide non-experts while remaining efficient for power users.
*   **Responsive Feedback:** Any operation expected to take longer than 100ms MUST provide immediate visual feedback (e.g., progress bar, status update).

## Editor Principles
*   **Visual Hierarchy:** Prioritize clarity through robust syntax highlighting, consistent indentation, and code folding to make deeply nested structures manageable.
*   **Contextual Intelligence:** IntelliSense and Quick Actions must be context-aware, only suggesting elements, attributes, or actions that are valid at the current cursor position according to the active schema.
*   **Data Integrity (Non-Destructive):** Switching between different view modes (Text, Grid, Tree) must preserve the original document's content and structure perfectly. Reformatting or reordering should only occur upon explicit user request.

## Error Handling
*   **Actionable Error Reporting:** Prioritize human-readable summaries for validation or transformation failures. Always provide direct links or navigation to the specific line/node causing the issue within the editor.
*   **Tiered Technical Detail:** Balance accessibility with precision by showing a clear summary by default, with an optional "Show Details" view for full technical logs and stack traces.

## Security & Privacy
*   **Local-First Sovereignty:** All document processing, including signing and validation, must occur locally on the user's machine. Sensitive data must never be transmitted to external services without explicit user consent.
*   **Cryptographic Transparency:** Provide clear visual indicators for the status of digital signatures (Valid, Invalid, Missing). Use and enforce modern, high-strength cryptographic standards by default.

## Performance & Stability
*   **Asynchronous Processing:** Long-running tasks (validation, transformation, documentation generation) MUST run in background threads to keep the UI responsive.
*   **Resource Efficiency:** Use streaming and memory-optimized data structures to ensure stability and performance when handling very large XML files (hundreds of megabytes).
