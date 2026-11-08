package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.i18n.I18n;

import java.util.Optional;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.stage.Window;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.DataFormatConverter;
import org.fxt.freexmltoolkit.service.DataFormatConverter.ConversionException;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Format;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Mapping;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Options;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Tools → Convert to JSON / XML / YAML: converts the active document's text (XML, JSON or YAML)
 * with {@link DataFormatConverter} in the background and opens the result in a new, unsaved tab.
 * The mapping and options of the last conversion are remembered in the settings.
 */
public final class DataConversionAction {

    private static final Logger logger = LogManager.getLogger(DataConversionAction.class);

    static final String KEY_MAPPING = "convert.mapping";
    static final String KEY_TYPED_VALUES = "convert.typedValues";
    static final String KEY_ROOT_NAME = "convert.rootName";

    /** Test hook: answers the options dialog instead of showing it ({@code null} = show it). */
    static Function<DataConversionDialog, Optional<Options>> dialogAnswer;

    private DataConversionAction() {
    }

    /** @return the conversion source format of a document type, or {@code null} when it cannot be converted */
    public static Format sourceFormat(EditorFileType type) {
        if (type == null) {
            return null;
        }
        if (type.isXmlFamily()) {
            return Format.XML;
        }
        return switch (type) {
            case JSON -> Format.JSON;
            case YAML -> Format.YAML;
            default -> null;
        };
    }

    /** @return whether a document of this type can be converted to {@code target} */
    public static boolean canConvert(EditorFileType type, Format target) {
        Format source = sourceFormat(type);
        return source != null && source != target;
    }

    /**
     * Converts the active document to {@code target}; does nothing when it cannot be converted.
     * Asks for the mapping and options first when XML is involved (JSON ↔ YAML has none).
     */
    public static void convertActive(EditorHost editorHost, Window owner, Format target) {
        Optional<OpenDocument> document = editorHost.getActiveDocument();
        if (document.isEmpty() || !canConvert(document.get().getFileType(), target)) {
            return;
        }
        Format source = sourceFormat(document.get().getFileType());
        String text = editorHost.getActiveText().orElse("");
        String outputName = baseName(document.get().getDisplayName()) + "." + target.extension();

        Options options = savedOptions(target);
        if (needsOptions(source, target)) {
            DataConversionDialog dialog = new DataConversionDialog(source, target, options);
            if (owner != null) {
                dialog.initOwner(owner);
            }
            Optional<Options> chosen = dialogAnswer != null ? dialogAnswer.apply(dialog) : dialog.showAndWait();
            if (chosen.isEmpty()) {
                return;
            }
            options = chosen.get();
            saveOptions(options);
        }
        Options chosenOptions = options;
        String title = "Convert to " + target.label();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                String result = DataFormatConverter.convert(text, source, target, chosenOptions);
                Platform.runLater(() -> editorHost.openGeneratedDocument(result, fileType(target), outputName));
            } catch (ConversionException e) {
                Platform.runLater(() -> DialogHelper.showWarning(title,
                        "The " + source.name() + " document could not be converted.", e.getMessage()));
            } catch (OutOfMemoryError e) {
                Platform.runLater(() -> DialogHelper.showWarning(title,
                        I18n.t("DataConversionAction.the-document-is-too-large"),
                        I18n.t("DataConversionAction.increase-the-maximum-memory-in")));
            } catch (RuntimeException e) {
                logger.warn("Conversion to {} failed", target, e);
                Platform.runLater(() -> DialogHelper.showWarning(title,
                        "The " + source.name() + " document could not be converted.", String.valueOf(e.getMessage())));
            }
        });
    }

    /** @return whether the conversion has options to ask for: only a mapping between XML and JSON/YAML */
    static boolean needsOptions(Format source, Format target) {
        return source == Format.XML || target == Format.XML;
    }

    static EditorFileType fileType(Format format) {
        return switch (format) {
            case XML -> EditorFileType.XML;
            case JSON -> EditorFileType.JSON;
            case YAML -> EditorFileType.YAML;
        };
    }

    /** The remembered options, with the indentation of the target format's formatter. */
    static Options savedOptions(Format target) {
        Options options = Options.DEFAULTS;
        try {
            PropertiesService properties = ServiceRegistry.get(PropertiesService.class);
            Mapping mapping = "W3C".equals(properties.get(KEY_MAPPING)) ? Mapping.W3C : Mapping.STRUCTURED;
            options = options.withMapping(mapping)
                    .withTypedValues(Boolean.parseBoolean(properties.get(KEY_TYPED_VALUES)))
                    .withRootName(properties.get(KEY_ROOT_NAME))
                    .withIndent(target == Format.XML ? properties.getXmlIndentSpaces() : properties.getJsonIndentSpaces());
        } catch (RuntimeException e) {
            logger.debug("Conversion settings unavailable: {}", e.getMessage());
        }
        return options;
    }

    private static void saveOptions(Options options) {
        try {
            PropertiesService properties = ServiceRegistry.get(PropertiesService.class);
            properties.set(KEY_MAPPING, options.mapping().name());
            properties.set(KEY_TYPED_VALUES, String.valueOf(options.typedValues()));
            properties.set(KEY_ROOT_NAME, options.rootName());
        } catch (RuntimeException e) {
            logger.debug("Conversion settings not saved: {}", e.getMessage());
        }
    }

    /** {@code order.xml} → {@code order}; untitled names stay as they are. */
    static String baseName(String displayName) {
        String name = displayName == null || displayName.isBlank() ? "converted" : displayName.strip();
        if (name.startsWith("*")) {
            name = name.substring(1).strip();
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
