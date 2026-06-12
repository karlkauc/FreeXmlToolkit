package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * The "Sign XML Document" card shown in the main editor area (Figma mockup
 * "Redesign · Unified — Signature", node 50:2): document row with Browse,
 * keystore alias + password (shared bidirectionally with the Signature side
 * panel's KEYSTORE section), the signature type (only enveloped XML-DSig is
 * implemented) and the fixed algorithm, and a primary "Sign Document" button.
 * Signing itself is delegated to the {@link SignaturePanel}, whose status the
 * card mirrors.
 */
public class SignDocumentView extends ScrollPane {

    private final Label docName = new Label("none");
    private final Label docInfo = new Label("no document open");
    private File xmlFile;

    SignDocumentView(SignaturePanel panel, EditorHost editorHost) {
        getStyleClass().add("fxt-sign-view");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);

        // --- card header -------------------------------------------------------
        IconifyIcon headIcon = new IconifyIcon("bi-pencil");
        headIcon.setIconSize(20);
        StackPane iconTile = new StackPane(headIcon);
        iconTile.getStyleClass().add("fxt-sign-icon-tile");
        Label title = new Label("Sign XML Document");
        title.getStyleClass().add("fxt-sign-card-title");
        Label subtitle = new Label("XML Digital Signature · XML-DSig (enveloped)");
        subtitle.getStyleClass().add("fxt-sign-card-sub");
        HBox head = new HBox(14, iconTile, new VBox(2, title, subtitle));
        head.setAlignment(Pos.CENTER_LEFT);

        // --- DOCUMENT ------------------------------------------------------------
        docName.setId("sign-doc-name");
        docName.getStyleClass().add("fxt-sign-doc-name");
        docInfo.getStyleClass().add("fxt-sign-doc-info");
        Region docSpacer = new Region();
        HBox.setHgrow(docSpacer, Priority.ALWAYS);
        Hyperlink browse = new Hyperlink("Browse");
        browse.getStyleClass().add("fxt-vp-change");
        browse.setOnAction(e -> chooseDocument());
        IconifyIcon docIcon = new IconifyIcon("bi-code-slash");
        docIcon.setIconSize(18);
        HBox docRow = new HBox(12, docIcon, new VBox(2, docName, docInfo), docSpacer, browse);
        docRow.getStyleClass().add("fxt-sign-doc-row");
        docRow.setAlignment(Pos.CENTER_LEFT);

        // --- keystore credentials (shared with the side panel) ---------------------
        TextField alias = new TextField();
        alias.setId("sign-card-alias");
        alias.setPromptText("alias");
        alias.textProperty().bindBidirectional(panel.aliasField().textProperty());
        PasswordField password = new PasswordField();
        password.setId("sign-card-password");
        password.setPromptText("keystore password");
        password.textProperty().bindBidirectional(panel.keystorePasswordField().textProperty());
        VBox aliasBox = fieldBox("Keystore alias", alias);
        VBox passwordBox = fieldBox("Keystore password", password);
        HBox.setHgrow(aliasBox, Priority.ALWAYS);
        HBox.setHgrow(passwordBox, Priority.ALWAYS);
        HBox credentials = new HBox(16, aliasBox, passwordBox);

        // --- SIGNATURE OPTIONS ------------------------------------------------------
        Label optionsLabel = sectionLabel("SIGNATURE OPTIONS");
        Label typeLabel = new Label("Signature type");
        typeLabel.getStyleClass().add("fxt-sig-field-label");
        ToggleGroup typeGroup = new ToggleGroup();
        ToggleButton enveloped = segment("Enveloped", typeGroup);
        enveloped.setSelected(true);
        ToggleButton enveloping = segment("Enveloping", typeGroup);
        enveloping.setDisable(true);
        enveloping.setTooltip(new Tooltip("Not supported yet"));
        ToggleButton detached = segment("Detached", typeGroup);
        detached.setDisable(true);
        detached.setTooltip(new Tooltip("Not supported yet"));
        typeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                typeGroup.selectToggle(oldV);
            }
        });
        HBox typeSeg = new HBox(2, enveloped, enveloping, detached);
        typeSeg.getStyleClass().add("fxt-seg-group");

        Label algoLabel = new Label("Algorithm");
        algoLabel.getStyleClass().add("fxt-sig-field-label");
        Region algoSpacer = new Region();
        HBox.setHgrow(algoSpacer, Priority.ALWAYS);
        Label algoValue = new Label("RSA-SHA256 · C14N exclusive");
        algoValue.getStyleClass().add("fxt-sign-algo");
        HBox algoRow = new HBox(algoLabel, algoSpacer, algoValue);
        algoRow.setAlignment(Pos.CENTER_LEFT);

        // --- Sign + status ------------------------------------------------------------
        Button sign = new Button("Sign Document", icon("bi-shield-lock", 15));
        sign.setId("sign-card-run");
        sign.getStyleClass().add("fxt-primary-button");
        sign.setMaxWidth(Double.MAX_VALUE);
        sign.setOnAction(e -> panel.signFile(xmlFile));
        Label status = new Label();
        status.getStyleClass().add("fxt-vp-status");
        status.setWrapText(true);
        status.textProperty().bind(panel.statusTextProperty());

        VBox card = new VBox(16, head,
                sectionLabel("DOCUMENT"), docRow,
                credentials,
                optionsLabel, typeLabel, typeSeg, algoRow,
                sign, status);
        card.getStyleClass().add("fxt-sign-card");
        card.setMaxWidth(560);

        StackPane wrap = new StackPane(card);
        wrap.getStyleClass().add("fxt-sign-view-wrap");
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        setContent(wrap);

        setDocument(editorHost.getActiveDocument()
                .map(OpenDocument::getPath).map(p -> p != null ? p.toFile() : null).orElse(null));
    }

    /** Sets the document to sign and refreshes the DOCUMENT row. */
    void setDocument(File file) {
        this.xmlFile = file;
        if (file != null && file.exists()) {
            docName.setText(file.getName());
            docInfo.setText(String.format("%d KB · %s", Math.max(1, file.length() / 1024),
                    file.getParentFile() != null ? file.getParentFile().getName() : ""));
        } else {
            docName.setText("none");
            docInfo.setText("no document open");
        }
    }

    private void chooseDocument() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XML document");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setDocument(file);
        }
    }

    private static VBox fieldBox(String label, Region field) {
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("fxt-sig-field-label");
        return new VBox(4, fieldLabel, field);
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-sign-section-label");
        return label;
    }

    private static ToggleButton segment(String text, ToggleGroup group) {
        ToggleButton toggle = new ToggleButton(text);
        toggle.setToggleGroup(group);
        toggle.getStyleClass().add("fxt-seg");
        toggle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(toggle, Priority.ALWAYS);
        return toggle;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }
}
