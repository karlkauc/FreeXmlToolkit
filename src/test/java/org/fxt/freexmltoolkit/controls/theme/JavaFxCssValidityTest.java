package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javafx.css.CssMetaData;
import javafx.css.CssParser;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.junit.jupiter.api.Test;

/**
 * Guards the stylesheets JavaFX loads against things only web browsers understand: every file must
 * parse without a {@link CssParser} error (no {@code @media}/{@code @keyframes}, no {@code hsla()},
 * no invalid colours), every {@code -fx-*} declaration must be a property JavaFX (or one of our own
 * controls) knows, and looked-up values must be colours - JavaFX cannot look up sizes, fonts or
 * durations, so {@code -spacing-md: 16px}-style tokens are dead code.
 */
class JavaFxCssValidityTest {

    private static final Path CSS_DIR = Path.of("src/main/resources/css");
    private static final Path XML_HIGHLIGHTING = Path.of("src/main/resources/scss/xml-highlighting.css");

    /**
     * Properties of controls and skins (their CssMetaData needs the toolkit) plus the Modena
     * looked-up colours that stylesheets override.
     */
    private static final Set<String> CONTROL_PROPERTIES = Set.of(
            "-fx-text-fill", "-fx-alignment", "-fx-wrap-text", "-fx-font-family", "-fx-font-size",
            "-fx-font-weight", "-fx-font-style", "-fx-text-overrun", "-fx-ellipsis-string",
            "-fx-graphic-text-gap", "-fx-label-padding", "-fx-content-display", "-fx-skin",
            "-fx-highlight-fill", "-fx-highlight-text-fill", "-fx-prompt-text-fill", "-fx-display-caret",
            "-fx-pref-column-count", "-fx-pref-row-count", "-fx-cell-size", "-fx-fixed-cell-size",
            "-fx-hbar-policy", "-fx-vbar-policy", "-fx-fit-to-width", "-fx-fit-to-height", "-fx-pannable",
            "-fx-tab-min-width", "-fx-tab-max-width", "-fx-tab-min-height", "-fx-tab-max-height",
            "-fx-open-tab-animation", "-fx-close-tab-animation", "-fx-indent", "-fx-arrows-visible",
            "-fx-show-delay", "-fx-show-duration", "-fx-hide-delay", "-fx-block-increment",
            "-fx-unit-increment", "-fx-show-tick-labels", "-fx-show-tick-marks", "-fx-major-tick-unit",
            "-fx-snap-to-ticks", "-fx-progress-color", "-fx-indeterminate-segment-count",
            "-fx-spin-enabled", "-fx-collapsible", "-fx-animated", "-fx-show-week-numbers",
            "-fx-color-label-visible", "-fx-node-orientation", "-fx-graphic-vpos", "-fx-graphic-hpos",
            "-fx-graphic", "-fx-size", "-fx-tick-label-fill", "-fx-tick-label-font", "-fx-tick-length",
            "-fx-tick-mark-visible", "-fx-tick-labels-visible", "-fx-legend-visible", "-fx-legend-side",
            // Modena looked-up colours
            "-fx-accent", "-fx-base", "-fx-background", "-fx-focus-color", "-fx-faint-focus-color",
            "-fx-control-inner-background", "-fx-control-inner-background-alt", "-fx-text-base-color",
            "-fx-text-background-color", "-fx-text-inner-color", "-fx-mark-color",
            "-fx-mark-highlight-color", "-fx-default-button", "-fx-shadow-highlight-color",
            "-fx-outer-border", "-fx-inner-border", "-fx-body-color", "-fx-color", "-fx-hover-base",
            "-fx-pressed-base", "-fx-box-border", "-fx-text-box-border", "-fx-cell-hover-color",
            "-fx-cell-focus-inner-border", "-fx-focused-text-base-color", "-fx-focused-mark-color",
            "-fx-selection-bar", "-fx-selection-bar-non-focused", "-fx-selection-bar-text",
            "-fx-table-cell-border-color", "-fx-table-header-border-color", "-fx-light-text-color",
            "-fx-dark-text-color", "-fx-mid-text-color");

    private static final Pattern DECLARATION = Pattern.compile("(?<![\\w-])(-[a-zA-Z][\\w-]*)\\s*:\\s*([^;{}]*);");

    /** A value JavaFX can use as a looked-up colour: a colour, a paint function or another lookup. */
    private static final Pattern COLOR_VALUE = Pattern.compile(
            "#[0-9a-fA-F]{3,8}|(rgba?|hsb|hsba|derive|ladder|linear-gradient|radial-gradient)\\(.*\\)"
                    + "|[a-zA-Z]+|-[\\w-]+");

    @Test
    void everyStylesheetParsesWithoutErrors() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path css : stylesheets()) {
            CssParser.errorsProperty().clear();
            new CssParser().parse(css.toUri().toURL());
            CssParser.errorsProperty().forEach(e -> problems.add(css.getFileName() + ": " + e.getMessage()));
        }
        CssParser.errorsProperty().clear();
        assertTrue(problems.isEmpty(), () -> "CSS errors:\n" + String.join("\n", problems));
    }

    @Test
    void onlyKnownJavaFxPropertiesAreDeclared() throws IOException {
        Set<String> known = knownProperties();
        List<String> problems = new ArrayList<>();
        for (Path css : stylesheets()) {
            forEachDeclaration(css, (line, name, value) -> {
                if (name.startsWith("-fx-") && !known.contains(name)) {
                    problems.add(css.getFileName() + ":" + line + " unknown property " + name);
                }
            });
        }
        assertTrue(problems.isEmpty(), () -> "Properties JavaFX does not know:\n" + String.join("\n", problems));
    }

    @Test
    void lookedUpValuesAreColours() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path css : stylesheets()) {
            forEachDeclaration(css, (line, name, value) -> {
                boolean lookup = !name.startsWith("-fx-") && !name.startsWith("-rtfx-");
                if (lookup && !COLOR_VALUE.matcher(value.trim()).matches()) {
                    problems.add(css.getFileName() + ":" + line + " " + name + ": " + value.trim());
                }
            });
        }
        assertTrue(problems.isEmpty(),
                () -> "Looked-up values JavaFX cannot use (only colours can be looked up):\n"
                        + String.join("\n", problems));
    }

    private static List<Path> stylesheets() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(CSS_DIR)) {
            s.filter(p -> p.toString().endsWith(".css")).sorted().forEach(files::add);
        }
        files.add(XML_HIGHLIGHTING);
        return files;
    }

    private static Set<String> knownProperties() {
        Set<String> known = new TreeSet<>(CONTROL_PROPERTIES);
        List<List<CssMetaData<? extends javafx.css.Styleable, ?>>> lists = List.of(
                Node.getClassCssMetaData(), Region.getClassCssMetaData(), Shape.getClassCssMetaData(),
                Rectangle.getClassCssMetaData(), Text.getClassCssMetaData(), TextFlow.getClassCssMetaData(),
                ImageView.getClassCssMetaData(), HBox.getClassCssMetaData(), VBox.getClassCssMetaData(),
                FlowPane.getClassCssMetaData(), GridPane.getClassCssMetaData(), StackPane.getClassCssMetaData(),
                TilePane.getClassCssMetaData(), IconifyIcon.getClassCssMetaData());
        lists.forEach(list -> addAll(known, list));
        return known;
    }

    private static void addAll(Set<String> known, List<? extends CssMetaData<?, ?>> metaData) {
        for (CssMetaData<?, ?> m : metaData) {
            known.add(m.getProperty());
            if (m.getSubProperties() != null) {
                addAll(known, m.getSubProperties());
            }
        }
    }

    private interface DeclarationVisitor {
        void visit(int line, String name, String value);
    }

    /** Visits each declaration outside comments; line numbers refer to the original file. */
    private static void forEachDeclaration(Path css, DeclarationVisitor visitor) throws IOException {
        String text = Files.readString(css);
        // Blank out comments but keep newlines so line numbers stay correct.
        Matcher comments = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(text);
        StringBuilder stripped = new StringBuilder();
        while (comments.find()) {
            comments.appendReplacement(stripped, Matcher.quoteReplacement(comments.group().replaceAll("[^\n]", " ")));
        }
        comments.appendTail(stripped);
        String body = stripped.toString();
        Matcher m = DECLARATION.matcher(body);
        while (m.find()) {
            int line = 1 + (int) body.substring(0, m.start()).chars().filter(c -> c == '\n').count();
            visitor.visit(line, m.group(1), m.group(2));
        }
    }
}
