package org.fxt.freexmltoolkit.controls.diff;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.controls.shared.PlainTextCodeArea;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.ThreeWayMerge;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Tools → Merge Files: the three-way merge ({@link ThreeWayMerge}) of a base and two changed
 * versions as an editable text. Conflicts are blocks with Git's markers; the toolbar resolves the
 * current one (at or after the caret) with the left, the right, both or the base version, and the
 * text can be edited freely. The status shows the conflicts left and, for XML, whether the result
 * is well-formed.
 */
public class MergeView extends VBox {

    @FXML private Button useLeft;
    @FXML private Button useRight;
    @FXML private Button useBoth;
    @FXML private Button useBase;
    @FXML private Label status;
    @FXML private Label sources;
    @FXML private StackPane editorSlot;

    /** Test hook: answers "save with conflicts?" instead of a dialog ({@code null} = dialog). */
    static BooleanSupplier saveWithConflictsAnswer;

    private final CodeArea area = new PlainTextCodeArea();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(200));
    private final boolean xml;
    private Path output;
    private List<ThreeWayMerge.Block> blocks = List.of();

    /**
     * @param mergedText the merge result with conflict markers
     * @param xml        whether the documents are XML (the result is checked for well-formedness)
     * @param output     where Save writes, or {@code null} (Save asks)
     * @param sourceText the line naming base, left and right
     */
    MergeView(String mergedText, boolean xml, Path output, String sourceText) {
        this.xml = xml;
        this.output = output;
        FxmlViews.load(this, "/pages/views/MergeView.fxml");
        sources.setText(sourceText);
        area.getStyleClass().add("xml-code-area");
        area.setId("merge-editor");
        area.replaceText(mergedText);
        editorSlot.getChildren().add(new VirtualizedScrollPane<>(area));
        debounce.setOnFinished(e -> refresh());
        org.fxt.freexmltoolkit.controls.v2.editor.TextEdits.of(area).subscribe(change -> debounce.playFromStart());
        area.caretPositionProperty().addListener((obs, o, n) -> updateButtons());
        refresh();
        area.moveTo(0);
        if (!blocks.isEmpty()) {
            select(blocks.get(0));
        }
    }

    /**
     * Reads the three files and merges them off the FX thread.
     *
     * @param output where Save writes, or {@code null}
     * @return completes on the FX thread with the view
     */
    public static CompletableFuture<MergeView> open(Path base, Path left, Path right, Path output) {
        CompletableFuture<MergeView> view = new CompletableFuture<>();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                String baseText = read(base);
                String leftText = read(left);
                String rightText = read(right);
                boolean isXml = XmlStructuralDiff.looksLikeXml(baseText) || XmlStructuralDiff.looksLikeXml(leftText);
                ThreeWayMerge.Result result = ThreeWayMerge.merge(baseText, leftText, rightText, isXml);
                String merged = result.text(name(left), name(base), name(right), true);
                String sourceText = "Base: " + base + "   Left: " + left + "   Right: " + right
                        + (output != null ? "   Result: " + output : "");
                Platform.runLater(() -> {
                    try {
                        view.complete(new MergeView(merged, isXml, output, sourceText));
                    } catch (RuntimeException e) {
                        view.completeExceptionally(e);
                    }
                });
            } catch (IOException | RuntimeException e) {
                view.completeExceptionally(e);
            }
        });
        return view;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static String name(Path file) {
        return file.getFileName() == null ? file.toString() : file.getFileName().toString();
    }

    /** Finds the conflicts again, colours them and updates the status. */
    void refresh() {
        String text = area.getText();
        blocks = ThreeWayMerge.blocks(text);
        area.setStyleSpans(0, highlighting(text, blocks));
        String state;
        if (!blocks.isEmpty()) {
            state = blocks.size() == 1 ? "1 conflict left" : blocks.size() + " conflicts left";
            status.getStyleClass().setAll("label", "fxt-merge-status-conflicts");
        } else if (xml) {
            String error = XmlStructuralDiff.wellFormednessError(text);
            state = error == null ? "No conflicts · well-formed XML" : "No conflicts · " + error;
            status.getStyleClass().setAll("label", error == null ? "fxt-merge-status-ok" : "fxt-merge-status-conflicts");
        } else {
            state = "No conflicts";
            status.getStyleClass().setAll("label", "fxt-merge-status-ok");
        }
        status.setText(state);
        updateButtons();
    }

    private static StyleSpans<Collection<String>> highlighting(String text, List<ThreeWayMerge.Block> blocks) {
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        int cursor = 0;
        for (ThreeWayMerge.Block block : blocks) {
            spans.add(List.of(), block.start() - cursor);
            int at = block.start();
            at = add(spans, text, at, lineEnd(text, at), "merge-marker");
            at = add(spans, text, at, at + block.left().length(), "merge-left");
            if (block.base() != null) {
                at = add(spans, text, at, lineEnd(text, at), "merge-marker");
                at = add(spans, text, at, at + block.base().length(), "merge-base");
            }
            at = add(spans, text, at, lineEnd(text, at), "merge-marker");
            at = add(spans, text, at, at + block.right().length(), "merge-right");
            at = add(spans, text, at, block.end(), "merge-marker");
            cursor = at;
        }
        spans.add(List.of(), Math.max(0, text.length() - cursor));
        return spans.create();
    }

    private static int add(StyleSpansBuilder<Collection<String>> spans, String text, int from, int to, String style) {
        int end = Math.min(Math.max(from, to), text.length());
        spans.add(List.of(style), end - from);
        return end;
    }

    private static int lineEnd(String text, int from) {
        int newline = text.indexOf('\n', from);
        return newline < 0 ? text.length() : newline + 1;
    }

    /** @return the conflict at the caret, else the next one after it, else the last one */
    private ThreeWayMerge.Block current() {
        int caret = area.getCaretPosition();
        for (ThreeWayMerge.Block block : blocks) {
            if (caret < block.end()) {
                return block;
            }
        }
        return blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
    }

    private void updateButtons() {
        boolean none = blocks.isEmpty();
        useLeft.setDisable(none);
        useRight.setDisable(none);
        useBoth.setDisable(none);
        ThreeWayMerge.Block block = current();
        useBase.setDisable(none || block == null || block.base() == null);
    }

    private void select(ThreeWayMerge.Block block) {
        area.moveTo(block.start());
        area.requestFollowCaret();
        updateButtons();
    }

    @FXML
    public void onNext() {
        int caret = area.getCaretPosition();
        blocks.stream().filter(b -> b.start() > caret).findFirst()
                .or(() -> blocks.stream().findFirst()).ifPresent(this::select);
    }

    @FXML
    public void onPrevious() {
        int caret = area.getCaretPosition();
        ThreeWayMerge.Block previous = null;
        for (ThreeWayMerge.Block block : blocks) {
            if (block.start() < caret) {
                previous = block;
            }
        }
        if (previous == null && !blocks.isEmpty()) {
            previous = blocks.get(blocks.size() - 1);
        }
        if (previous != null) {
            select(previous);
        }
    }

    @FXML
    public void onUseLeft() {
        resolve(b -> b.left());
    }

    @FXML
    public void onUseRight() {
        resolve(b -> b.right());
    }

    @FXML
    public void onUseBoth() {
        resolve(b -> b.left() + b.right());
    }

    @FXML
    public void onUseBase() {
        resolve(b -> b.base() == null ? b.left() : b.base());
    }

    private void resolve(java.util.function.Function<ThreeWayMerge.Block, String> choice) {
        ThreeWayMerge.Block block = current();
        if (block == null) {
            return;
        }
        area.replaceText(block.start(), block.end(), choice.apply(block));
        area.moveTo(block.start());
        refresh();
        onNext();
    }

    @FXML
    public void onSave() {
        if (output == null) {
            onSaveAs();
            return;
        }
        save(output);
    }

    @FXML
    public void onSaveAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Merge Result");
        if (output != null) {
            chooser.setInitialDirectory(output.toAbsolutePath().getParent().toFile());
            chooser.setInitialFileName(output.getFileName().toString());
        }
        File file = FileChooserHelper.showSaveDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            output = file.toPath();
            save(output);
        }
    }

    private void save(Path target) {
        refresh();
        if (!blocks.isEmpty() && !confirmSaveWithConflicts()) {
            return;
        }
        try {
            Files.writeString(target, area.getText(), StandardCharsets.UTF_8);
            status.setText(status.getText() + " · saved to " + target.getFileName());
        } catch (IOException e) {
            DialogHelper.showError("Save Merge Result", "The merge result could not be saved.", e.getMessage(), e);
        }
    }

    private boolean confirmSaveWithConflicts() {
        if (saveWithConflictsAnswer != null) {
            return saveWithConflictsAnswer.getAsBoolean();
        }
        var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "The result still contains " + blocks.size() + (blocks.size() == 1 ? " conflict" : " conflicts")
                        + " with their markers. Save anyway?",
                javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.CANCEL);
        alert.setTitle("Save Merge Result");
        alert.setHeaderText("Unresolved conflicts");
        if (getScene() != null) {
            alert.initOwner(getScene().getWindow());
        }
        return alert.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.YES).isPresent();
    }

    // test access
    String textForTesting() {
        return area.getText();
    }

    String statusForTesting() {
        return status.getText();
    }

    int conflictsForTesting() {
        return blocks.size();
    }

    void setCaretForTesting(int offset) {
        area.moveTo(offset);
    }

    void setTextForTesting(String text) {
        area.replaceText(text);
        refresh();
    }
}
