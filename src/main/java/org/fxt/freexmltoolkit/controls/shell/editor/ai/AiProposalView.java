package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import java.util.List;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.controls.diff.DiffChunk;
import org.fxt.freexmltoolkit.controls.diff.DiffEngine;
import org.fxt.freexmltoolkit.controls.diff.DiffHighlighter;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shared.PlainTextCodeArea;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.ai.agent.Check;
import org.fxt.freexmltoolkit.service.ai.agent.Verification;
import org.fxt.freexmltoolkit.util.DialogHelper;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The AI panel in the editor's bottom dock (like the Transform output and the debugger): progress while the model
 * works and the checks run, then the proposal — the model's explanation, the change (a diff or the generated text),
 * the verification report — and the actions. Apply is refused when the document changed since the request; an
 * unverified proposal can only be applied after a second, explicit confirmation (D8).
 */
public class AiProposalView extends VBox {

    public static final String DOCK_KEY = "AI";

    /** One view per editor host (the dock has no lookup of its panels). */
    private static final java.util.Map<EditorHost, AiProposalView> VIEWS = new java.util.WeakHashMap<>();

    /**
     * What the view offers once the run ended.
     *
     * @param content     the change to show (diff or code), or {@code null}
     * @param copyText    what Copy puts on the clipboard, or {@code null}
     * @param applyLabel  the label of the apply button, or {@code null} when there is nothing to apply
     * @param apply       applies the proposal (one undo step)
     * @param staleReason why applying is no longer possible ({@code null} = still possible), asked at click time
     * @param openAsNew   opens the result as a new unsaved document, or {@code null}
     */
    public record Proposal(String answer, Verification verification, Node content, String copyText, String applyLabel,
                           Runnable apply, Supplier<String> staleReason, Runnable openAsNew) {
    }

    @FXML private Label title;
    @FXML private Label status;
    @FXML private ProgressIndicator progress;
    @FXML private Button cancelButton;
    @FXML private TextArea answer;
    @FXML private StackPane contentHost;
    @FXML private VBox checks;
    @FXML private Button applyButton;
    @FXML private Button applyUnverifiedButton;
    @FXML private Button openButton;
    @FXML private Button copyButton;
    @FXML private Button discardButton;

    private Runnable onCancel = () -> { };
    private Proposal proposal;
    private final EditorHost host;

    public AiProposalView(EditorHost host) {
        this.host = host;
        FxmlViews.load(this, "/pages/views/AiProposalView.fxml");
        reset("");
    }

    /** The view in the bottom dock of {@code host}, created on first use, shown and selected. */
    public static AiProposalView open(EditorHost host, String heading) {
        var dock = host.bottomDock();
        AiProposalView view = VIEWS.get(host);
        if (view == null || !dock.has(DOCK_KEY)) {
            view = new AiProposalView(host);
            VIEWS.put(host, view);
            dock.register(DOCK_KEY, view, I18n.t("AiProposalView.dock"));
        }
        view.reset(heading);
        dock.show(DOCK_KEY);
        return view;
    }

    /** Clears the view for a new run. */
    public void reset(String heading) {
        proposal = null;
        title.setText(heading);
        status.setText("");
        progress.setVisible(false);
        cancelButton.setVisible(false);
        answer.clear();
        answer.setVisible(false);
        answer.setManaged(false);
        contentHost.getChildren().clear();
        checks.getChildren().clear();
        for (Button b : List.of(applyButton, applyUnverifiedButton, openButton, copyButton)) {
            b.setVisible(false);
            b.setManaged(false);
        }
    }

    /** The run is going on: a status line and Cancel. */
    public void running(String text, Runnable cancel) {
        status.setText(text);
        progress.setVisible(true);
        cancelButton.setVisible(true);
        onCancel = cancel == null ? () -> { } : cancel;
    }

    /** Text the model streams while it answers. */
    public void streaming(String delta) {
        if (!answer.isVisible()) {
            answer.setVisible(true);
            answer.setManaged(true);
        }
        answer.appendText(delta);
    }

    /** The run ended without a proposal. */
    public void failed(String message) {
        progress.setVisible(false);
        cancelButton.setVisible(false);
        status.setText(message);
    }

    /** The run ended with a proposal. */
    public void show(Proposal p, String statusText) {
        this.proposal = p;
        progress.setVisible(false);
        cancelButton.setVisible(false);
        status.setText(statusText);
        if (p.answer() != null && !p.answer().isBlank()) {
            answer.setText(p.answer());
            answer.setVisible(true);
            answer.setManaged(true);
        }
        contentHost.getChildren().clear();
        if (p.content() != null) {
            contentHost.getChildren().add(p.content());
        }
        checks.getChildren().clear();
        if (p.verification() != null) {
            for (Check check : p.verification().checks()) {
                IconifyIcon icon = new IconifyIcon(check.passed() ? "bi-check-circle" : "bi-x-circle");
                icon.setIconSize(14);
                icon.getStyleClass().add(check.passed() ? "sev-ok" : "sev-error");
                Label label = new Label(check.label() + (check.detail() == null || check.detail().isBlank() ? ""
                        : " — " + check.detail()));
                label.setWrapText(true);
                HBox row = new HBox(6, icon, label);
                checks.getChildren().add(row);
            }
        }
        boolean verified = p.verification() != null && p.verification().verified();
        boolean canApply = p.apply() != null && p.applyLabel() != null;
        show(applyButton, canApply && verified);
        show(applyUnverifiedButton, canApply && !verified);
        if (canApply) {
            applyButton.setText(p.applyLabel());
        }
        show(openButton, p.openAsNew() != null);
        show(copyButton, p.copyText() != null);
    }

    private static void show(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    @FXML
    public void onCancel() {
        onCancel.run();
    }

    @FXML
    public void onApply() {
        apply(false);
    }

    @FXML
    public void onApplyUnverified() {
        if (DialogHelper.showConfirmation(I18n.t("AiProposalView.unverified.title"),
                I18n.t("AiProposalView.unverified.header"), I18n.t("AiProposalView.unverified.content"))) {
            apply(true);
        }
    }

    private void apply(boolean unverified) {
        if (proposal == null || proposal.apply() == null) {
            return;
        }
        String stale = proposal.staleReason() == null ? null : proposal.staleReason().get();
        if (stale != null) {
            status.setText(stale);
            show(applyButton, false);
            show(applyUnverifiedButton, false);
            return;
        }
        proposal.apply().run();
        status.setText(I18n.t(unverified ? "AiProposalView.appliedUnverified" : "AiProposalView.applied"));
        show(applyButton, false);
        show(applyUnverifiedButton, false);
    }

    @FXML
    public void onOpen() {
        if (proposal != null && proposal.openAsNew() != null) {
            proposal.openAsNew().run();
        }
    }

    @FXML
    public void onCopy() {
        if (proposal != null && proposal.copyText() != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(proposal.copyText());
            Clipboard.getSystemClipboard().setContent(content);
            status.setText(I18n.t("AiProposalView.copied"));
        }
    }

    @FXML
    public void onDiscard() {
        onCancel.run();
        reset(title.getText());
        host.bottomDock().hide();
    }

    // ---------------------------------------------------------------- content helpers

    /** A read-only code area with {@code text}. */
    public static Node code(String text) {
        PlainTextCodeArea area = new PlainTextCodeArea();
        area.replaceText(text == null ? "" : text);
        area.setEditable(false);
        area.setPrefHeight(220);
        return new org.fxmisc.flowless.VirtualizedScrollPane<>(area);
    }

    /** Before and after side by side, the differences highlighted. */
    public static Node diff(String before, String after) {
        List<DiffChunk> chunks = DiffEngine.compute(before, after);
        PlainTextCodeArea left = new PlainTextCodeArea();
        PlainTextCodeArea right = new PlainTextCodeArea();
        left.replaceText(before);
        right.replaceText(after);
        left.setEditable(false);
        right.setEditable(false);
        if (!before.isEmpty()) {
            left.setStyleSpans(0, DiffHighlighter.computeHighlighting(before, chunks, DiffHighlighter.Side.LEFT));
        }
        if (!after.isEmpty()) {
            right.setStyleSpans(0, DiffHighlighter.computeHighlighting(after, chunks, DiffHighlighter.Side.RIGHT));
        }
        var leftPane = new org.fxmisc.flowless.VirtualizedScrollPane<>(left);
        var rightPane = new org.fxmisc.flowless.VirtualizedScrollPane<>(right);
        HBox box = new HBox(6, leftPane, rightPane);
        HBox.setHgrow(leftPane, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(rightPane, javafx.scene.layout.Priority.ALWAYS);
        box.setPrefHeight(240);
        return box;
    }
}
