package org.fxt.freexmltoolkit.controls.shell.editor.quickfix;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.application.Platform;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.service.sqf.SqfCatalogCache;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine.SqfEditPlan;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine.SqfTextEdit;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionException;
import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfUserEntry;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Orchestrates applying a quick fix: prompts for {@code sqf:user-entry} values
 * (when a prompt is installed), computes the edit plan off-thread via
 * {@link SqfExecutionEngine}, then applies all edits of the fix as <em>one</em>
 * text-region replacement on the FX thread — so the whole fix is a single native
 * undo step and the document's formatting outside the edit stays untouched.
 */
public final class QuickFixController {

    private static final Logger logger = LogManager.getLogger(QuickFixController.class);

    /** Collects {@code sqf:user-entry} values on the FX thread; empty result = cancel. */
    public interface UserEntryPrompt {
        Optional<Map<String, String>> prompt(String fixTitle, List<SqfUserEntry> entries,
                                             Map<String, String> defaults);
    }

    private final EditorHost editorHost;
    private UserEntryPrompt userEntryPrompt = SqfUserEntryDialog::prompt;
    private Runnable onFixApplied;

    public QuickFixController(EditorHost editorHost) {
        this.editorHost = editorHost;
    }

    /**
     * Replaces the user-entry prompt (tests use a headless stub). {@code null}
     * disables prompting — entries then fall back to their {@code @default} XPath.
     */
    public void setUserEntryPrompt(UserEntryPrompt prompt) {
        this.userEntryPrompt = prompt;
    }

    /** Invoked (on the FX thread) after a fix was applied successfully. */
    public void setOnFixApplied(Runnable onFixApplied) {
        this.onFixApplied = onFixApplied;
    }

    /**
     * Applies the chosen fix to the active document. Call on the FX thread.
     *
     * @param problem the problem row the fix was offered on (context for messages)
     * @param fix     the chosen fix
     */
    public void applyFix(ValidationProblem problem, SqfFixSuggestion fix) {
        String text = editorHost.getActiveText().orElse(null);
        if (text == null) {
            DialogHelper.showActionError("Quick Fix", "No editable document is active.",
                    "Open the XML document the problem belongs to and re-run validation.");
            return;
        }
        SqfCatalog catalog = SqfCatalogCache.forFile(fix.schematronFile());

        Map<String, String> userValues = Map.of();
        List<SqfUserEntry> entries = SqfExecutionEngine.requiredUserEntries(catalog, fix);
        if (!entries.isEmpty() && userEntryPrompt != null) {
            Map<String, String> defaults =
                    SqfExecutionEngine.defaultUserEntryValues(text, catalog, fix);
            Optional<Map<String, String>> prompted =
                    userEntryPrompt.prompt(fix.title(), entries, defaults);
            if (prompted.isEmpty()) {
                return; // cancelled
            }
            userValues = prompted.get();
        }

        Map<String, String> values = userValues;
        FxtGui.executorService.submit(() -> {
            try {
                SqfEditPlan plan = SqfExecutionEngine.computeEdit(text, catalog, fix, values);
                Platform.runLater(() -> applyPlan(text, plan));
            } catch (SqfExecutionException e) {
                logger.info("Quick fix '{}' not applied: {}", fix.fixId(), e.getMessage());
                Platform.runLater(() -> DialogHelper.showActionError("Quick Fix",
                        "The fix \"" + fix.title() + "\" could not be applied.",
                        e.getMessage()));
            } catch (Exception e) {
                logger.warn("Quick fix '{}' failed", fix.fixId(), e);
                Platform.runLater(() -> DialogHelper.showActionError("Quick Fix",
                        "The fix \"" + fix.title() + "\" failed unexpectedly.",
                        "Re-validate the document and try again.", e));
            }
        });
    }

    private void applyPlan(String textAtCompute, SqfEditPlan plan) {
        String current = editorHost.getActiveText().orElse(null);
        if (current == null || !current.equals(textAtCompute)) {
            DialogHelper.showActionError("Quick Fix",
                    "The document changed while the fix was being computed.",
                    "Re-validate the document and apply the fix again.");
            return;
        }
        // merge all edits into one region → exactly one native undo entry
        int start = Integer.MAX_VALUE;
        int end = 0;
        for (SqfTextEdit edit : plan.edits()) {
            start = Math.min(start, edit.start());
            end = Math.max(end, edit.end());
        }
        StringBuilder region = new StringBuilder(textAtCompute.substring(start, end));
        for (SqfTextEdit edit : plan.edits()) { // sorted descending by start
            region.replace(edit.start() - start, edit.end() - start, edit.replacement());
        }
        if (!editorHost.replaceActiveTextRegion(start, end, region.toString())) {
            DialogHelper.showActionError("Quick Fix", "The fix could not be applied to the editor.",
                    "Switch to the document's Text view and try again.");
            return;
        }
        logger.info("Applied quick fix: {}", plan.fixTitle());
        if (onFixApplied != null) {
            onFixApplied.run();
        }
    }
}
