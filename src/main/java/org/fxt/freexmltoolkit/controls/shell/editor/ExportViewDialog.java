package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.Locale;

import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.util.StringConverter;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.v2.view.render.ExportableDiagram;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions.Area;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions.Background;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions.Format;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions.PageSize;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions.ThemeChoice;
import org.fxt.freexmltoolkit.service.export.DiagramExporter;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Asks how a graphical view is exported: format (PNG, SVG, PDF), area, scale (PNG), page size
 * (PDF), palette, background, padding and font embedding (SVG). Shows the resulting size and
 * refuses a PNG too large to build. The file is chosen afterwards (see {@link ViewExport}).
 */
public class ExportViewDialog extends Dialog<DiagramExportOptions> {

    /** The confirm button. */
    static final ButtonType EXPORT = new ButtonType(I18n.t("ExportViewDialog.export"), ButtonBar.ButtonData.OK_DONE);

    private static final List<Double> SCALES = List.of(1.0, 1.5, 2.0, 3.0, 4.0);

    @FXML private ComboBox<Format> formatBox;
    @FXML private ComboBox<Area> areaBox;
    @FXML private Label scaleLabel;
    @FXML private ComboBox<Double> scaleBox;
    @FXML private Label pageSizeLabel;
    @FXML private ComboBox<PageSize> pageSizeBox;
    @FXML private ComboBox<ThemeChoice> themeBox;
    @FXML private ComboBox<Background> backgroundBox;
    @FXML private Spinner<Integer> paddingSpinner;
    @FXML private CheckBox embedFontsBox;
    @FXML private Label sizeLabel;
    @FXML private Label errorLabel;

    private final ExportableDiagram diagram;
    private final String title;

    /**
     * @param diagram the view to export
     * @param initial the options to start with (the last ones used)
     * @param title   the document title written into SVG / PDF
     */
    public ExportViewDialog(ExportableDiagram diagram, DiagramExportOptions initial, String title) {
        DialogHelper.prepare(this);
        this.diagram = diagram;
        this.title = title == null ? "" : title;
        setTitle(I18n.t("ExportViewDialog.title"));
        setHeaderText(I18n.t("ExportViewDialog.header"));
        getDialogPane().getButtonTypes().addAll(EXPORT, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/ExportViewDialog.fxml"));

        formatBox.getItems().setAll(Format.values());
        formatBox.setConverter(converter(f -> I18n.t("ExportViewDialog.format." + f.name())));
        areaBox.getItems().setAll(Area.values());
        areaBox.setConverter(converter(a -> I18n.t("ExportViewDialog.area." + a.name())));
        scaleBox.getItems().setAll(SCALES);
        scaleBox.setConverter(converter(s -> (s == Math.rint(s) ? Integer.toString(s.intValue()) : s.toString()) + "×"));
        pageSizeBox.getItems().setAll(PageSize.values());
        pageSizeBox.setConverter(converter(p -> I18n.t("ExportViewDialog.page." + p.name())));
        themeBox.getItems().setAll(ThemeChoice.values());
        themeBox.setConverter(converter(t -> I18n.t("ExportViewDialog.theme." + t.name())));
        backgroundBox.getItems().setAll(Background.values());
        backgroundBox.setConverter(converter(b -> I18n.t("ExportViewDialog.background." + b.name())));

        setOptions(initial);
        formatBox.valueProperty().addListener((obs, o, n) -> update());
        areaBox.valueProperty().addListener((obs, o, n) -> update());
        scaleBox.valueProperty().addListener((obs, o, n) -> update());
        backgroundBox.valueProperty().addListener((obs, o, n) -> update());
        paddingSpinner.valueProperty().addListener((obs, o, n) -> update());
        update();

        setResultConverter(button -> button == EXPORT ? currentOptions() : null);
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : text.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    /** Sets the controls from options (also used by tests). */
    public final void setOptions(DiagramExportOptions options) {
        formatBox.setValue(options.format());
        areaBox.setValue(options.area());
        double scale = SCALES.stream().min((a, b) -> Double.compare(Math.abs(a - options.scale()),
                Math.abs(b - options.scale()))).orElse(2.0);
        scaleBox.setValue(scale);
        pageSizeBox.setValue(options.pageSize());
        themeBox.setValue(options.theme());
        backgroundBox.setValue(options.background());
        paddingSpinner.getValueFactory().setValue((int) Math.round(options.padding()));
        embedFontsBox.setSelected(options.embedFonts());
    }

    /** @return the options as currently set */
    public DiagramExportOptions currentOptions() {
        Integer padding = paddingSpinner.getValue();
        return new DiagramExportOptions(formatBox.getValue(), areaBox.getValue(),
                scaleBox.getValue() == null ? 2 : scaleBox.getValue(), themeBox.getValue(), backgroundBox.getValue(),
                padding == null ? 16 : padding, embedFontsBox.isSelected(), pageSizeBox.getValue(), title);
    }

    /** Shows the rows that apply to the format, the resulting size, and blocks impossible exports. */
    private void update() {
        Format format = formatBox.getValue() == null ? Format.PNG : formatBox.getValue();
        show(format == Format.PNG, scaleLabel, scaleBox);
        show(format == Format.PDF, pageSizeLabel, pageSizeBox);
        show(format == Format.SVG, embedFontsBox);
        String error = null;
        String size = "";
        try {
            DiagramExportOptions options = currentOptions();
            Rectangle2D region = DiagramExporter.exportRegion(diagram, options);
            if (format == Format.PNG) {
                int[] pixels = DiagramExporter.pixelSize(region, options.scale());
                size = I18n.t("ExportViewDialog.size.pixels", pixels[0], pixels[1]);
                if ((long) pixels[0] * pixels[1] > DiagramExporter.MAX_PIXELS) {
                    error = I18n.t("ExportViewDialog.too-large");
                }
            } else {
                size = I18n.t("ExportViewDialog.size.units", String.format(Locale.ROOT, "%.0f", region.getWidth()),
                        String.format(Locale.ROOT, "%.0f", region.getHeight()));
            }
        } catch (DiagramExporter.DiagramExportException e) {
            error = e.getMessage();
        }
        sizeLabel.setText(size);
        errorLabel.setText(error == null ? "" : error);
        errorLabel.setVisible(error != null);
        errorLabel.setManaged(error != null);
        Node exportButton = getDialogPane().lookupButton(EXPORT);
        if (exportButton != null) {
            exportButton.setDisable(error != null);
        }
    }

    private static void show(boolean visible, Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }
}
