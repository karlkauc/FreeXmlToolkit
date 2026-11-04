package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.control.Tab;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaEdit;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaEditTarget;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.SchemaUsage;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.XmlSchemaDeclaration;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost.XmlSchemaDetails;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Schema lookups and navigation of an {@link EditorHost}: the XSD-derived facts the Inspector
 * shows for the active XML element ({@code resolveActiveXml*}), the bound XSD declaration
 * behind an XML element (resolve, edit through the schema's own tab, reveal), schema usages,
 * and Go to Definition / reveal of schema components in the Tree and Graphic views.
 */
final class EditorSchemaLookup {

    private final EditorHost host;

    EditorSchemaLookup(EditorHost host) {
        this.host = host;
    }

    /** Reveals a named type in the Tree view (switching to the XSD tab + Tree if needed). */
    public void revealTypeByName(String typeName) {
        if (typeName == null) {
            return;
        }
        EditorTab et = host.resolveStructuredEditorTab();
        if (et == null) {
            return;
        }
        // A type-editor tool tab may be focused — bring the XSD document tab to the front first.
        if (host.tabPane.getSelectionModel().getSelectedItem() != et) {
            host.tabPane.getSelectionModel().select(et);
        }
        host.setActiveViewMode(ViewMode.TREE);
        host.getActiveSchemaRoot().ifPresent(root -> {
            for (XsdNode child : root.getChildren()) {
                // Named types and global element declarations (the Type Library lists both).
                boolean revealable =
                        child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.SIMPLE_TYPE
                        || child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.COMPLEX_TYPE
                        || child.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.ELEMENT;
                if (revealable && typeName.equals(child.getName())) {
                    selectNodeInActiveTree(child);
                    return;
                }
            }
        });
    }

    /**
     * Opens an XSD file in the shell and, once it has loaded and parsed, switches to the
     * Graphic view and selects the named element (preferred) or type. Bridge replacing the
     * retired legacy XSD editor's {@code navigateToElementInGraphView}, used by the XML
     * editor's "go to schema definition". Navigation is deferred until the async load
     * completes; if {@code elementName} is blank the file is just opened.
     */
    public void openXsdAndReveal(java.nio.file.Path path, String elementName) {
        if (path == null) {
            return;
        }
        host.openFile(path);
        if (elementName != null && !elementName.isBlank()) {
            revealInGraphAfterLoad(path, elementName, 80);
        }
    }

    private void revealInGraphAfterLoad(java.nio.file.Path path, String name, int attemptsLeft) {
        var doc = host.getActiveDocument();
        boolean loaded = doc.isPresent() && path.equals(doc.get().getPath())
                && host.getActiveText().map(t -> !t.isBlank()).orElse(false);
        if (loaded) {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            XsdNode node = findNodeByName(host.getActiveSchemaRoot().orElse(null), name);
            if (node != null) {
                selectNodeInActiveTree(node);
                return;
            }
        }
        if (attemptsLeft > 0) {
            // Space out retries so the async file load + model parse can complete
            // (a tight runLater chain would exhaust all attempts in milliseconds).
            javafx.animation.PauseTransition pause =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));
            pause.setOnFinished(e -> revealInGraphAfterLoad(path, name, attemptsLeft - 1));
            pause.play();
        }
    }

    /** Depth-first search for a named node, preferring elements over types; cycle-safe. */
    private static XsdNode findNodeByName(XsdNode root, String name) {
        if (root == null || name == null) {
            return null;
        }
        XsdNode fallback = null;
        java.util.Set<XsdNode> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Deque<XsdNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            XsdNode n = stack.pop();
            if (!visited.add(n)) {
                continue;
            }
            if (name.equals(n.getName())) {
                if (n.getNodeType() == org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType.ELEMENT) {
                    return n;
                }
                if (fallback == null) {
                    fallback = n;
                }
            }
            for (XsdNode c : n.getChildren()) {
                stack.push(c);
            }
        }
        return fallback;
    }

    /**
     * Reveals, in the active Graphic view, the card at an instance path (the model particle of
     * every step from the global root element down, see {@code XsdInstanceLocator.Located#particlePath()}),
     * expanding the diagram along that path; in the Tree view the last particle is revealed.
     */
    public void revealPathInActiveGraph(java.util.List<XsdNode> particlePath) {
        if (particlePath == null || particlePath.isEmpty()) {
            return;
        }
        host.withActive(et -> {
            if (et.viewMode == ViewMode.GRAPHIC && et.xsdGraphView != null) {
                et.xsdGraphView.revealModelPath(particlePath);
            } else if (et.viewMode == ViewMode.TREE && et.treeView != null) {
                et.treeView.revealNode(particlePath.get(particlePath.size() - 1));
            }
        });
    }

    /** Selects (reveals) the given node in the active structured view (Tree or Graphic). */
    public void selectNodeInActiveTree(XsdNode node) {
        host.withActive(et -> {
            if (et.viewMode == ViewMode.TREE && et.treeView != null) {
                et.treeView.revealNode(node);
            } else if (et.viewMode == ViewMode.GRAPHIC && et.xsdGraphView != null) {
                et.xsdGraphView.selectModelNode(node);
            }
        });
    }

    /**
     * Reveals, in the Tree view, the live-model node whose schema XPath
     * ({@link XsdNode#getXPath()}, e.g. {@code /xs:schema/xs:complexType[@name='T']/xs:sequence})
     * equals {@code xpath}. Used by reports that analyzed a <em>fresh</em> parse of the
     * document and therefore cannot navigate by node identity. Unnamed siblings (two
     * {@code xs:sequence} under one parent) share an XPath; the first match wins.
     *
     * @return true if a node was found and selected
     */
    public boolean revealSchemaNodeByXPath(String xpath) {
        if (xpath == null || xpath.isBlank()) {
            return false;
        }
        EditorTab et = host.resolveStructuredEditorTab();
        if (et == null) {
            return false;
        }
        if (host.tabPane.getSelectionModel().getSelectedItem() != et) {
            host.tabPane.getSelectionModel().select(et);
        }
        host.setActiveViewMode(ViewMode.TREE);
        XsdNode root = host.getActiveSchemaRoot().orElse(null);
        XsdNode match = findNodeByXPath(root, xpath);
        if (match == null) {
            return false;
        }
        selectNodeInActiveTree(match);
        return true;
    }

    /** Depth-first search for the first node whose {@code getXPath()} equals {@code xpath}; cycle-safe. */
    private static XsdNode findNodeByXPath(XsdNode root, String xpath) {
        if (root == null) {
            return null;
        }
        java.util.Set<XsdNode> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Deque<XsdNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            XsdNode n = stack.pop();
            if (!visited.add(n)) {
                continue;
            }
            if (xpath.equals(n.getXPath())) {
                return n;
            }
            // Children are pushed in reverse so document order is searched first.
            java.util.List<XsdNode> children = n.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        return null;
    }

    /**
     * @return XSD-derived type/documentation for an XML-instance element at the given XPath, if
     * a schema is bound to the active document; otherwise empty.
     */
    public java.util.Optional<
            org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ElementTypeInfo>
            resolveActiveXmlElementInfo(String elementXPath) {
        Tab tab = host.tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.xmlSchemaProvider != null && elementXPath != null) {
            return et.xmlSchemaProvider.getElementTypeInfo(elementXPath);
        }
        return java.util.Optional.empty();
    }

    /** @return the schema-allowed child element (local) names for the element at the given XPath. */
    public java.util.List<String> resolveValidChildren(String elementXPath) {
        var map = activeXsdElementMap();
        var element = activeXsdElement(elementXPath);
        if (map == null || element == null) {
            return java.util.List.of();
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        collectChildElementNames(element, map, out, new java.util.HashSet<>());
        return out.stream().distinct().toList();
    }

    /** Collects real child element names, descending through compositor nodes (sequence/choice/all). */
    private void collectChildElementNames(org.fxt.freexmltoolkit.domain.XsdExtendedElement element,
            java.util.Map<String, org.fxt.freexmltoolkit.domain.XsdExtendedElement> map,
            java.util.List<String> out, java.util.Set<String> visited) {
        if (element.getChildren() == null) {
            return;
        }
        for (String childXPath : element.getChildren()) {
            if (childXPath == null || childXPath.contains("@") || !visited.add(childXPath)) {
                continue; // attributes / cycles
            }
            String local = localXmlName(childXPath);
            if (isCompositorName(local)) {
                var child = map.get(childXPath);
                if (child != null) {
                    collectChildElementNames(child, map, out, visited);
                }
            } else if (local != null && !local.isBlank()) {
                out.add(local);
            }
        }
    }

    /** @return true for the synthetic compositor node names the documentation model inserts. */
    private static boolean isCompositorName(String name) {
        return name != null && name.matches("(?i)(SEQUENCE|CHOICE|ALL|GROUP)(_\\d+)?");
    }

    /**
     * @return the per-language schema documentation entries for the element at the given XPath
     * (lang is lowercase, {@code "default"} for entries without {@code xml:lang}); empty when
     * no schema is bound or the path cannot be resolved.
     */
    public java.util.List<org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo>
            resolveActiveXmlElementDocs(String elementXPath) {
        var element = activeXsdElement(elementXPath);
        if (element == null || element.getDocumentations() == null) {
            return java.util.List.of();
        }
        return java.util.List.copyOf(element.getDocumentations());
    }

    /** @return example/allowed values for the element at the given XPath (xs:example, else enumeration). */
    public java.util.List<String> resolveExampleValues(String elementXPath) {
        var element = activeXsdElement(elementXPath);
        java.util.List<String> values = new java.util.ArrayList<>();
        if (element != null && element.getExampleValues() != null) {
            values.addAll(element.getExampleValues());
        }
        if (values.isEmpty()) {
            resolveActiveXmlElementInfo(elementXPath).ifPresent(info -> {
                if (info.enumerationValues() != null) {
                    values.addAll(info.enumerationValues());
                }
            });
        }
        return values.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }

    /**
     * @return base type and derivation ({@code restriction} / {@code extension}) of the schema type
     * of the XML element at the given XPath; empty when no schema is bound or the type is not derived
     */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation.Derivation>
            resolveActiveXmlElementDerivation(String elementXPath) {
        var element = activeXsdElement(elementXPath);
        if (element == null) {
            return java.util.Optional.empty();
        }
        var restriction = element.getRestrictionInfo();
        if (restriction != null && restriction.base() != null && !restriction.base().isBlank()) {
            return java.util.Optional.of(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation
                    .Derivation(restriction.base(), "restriction"));
        }
        String extensionBase = element.getSimpleContentExtensionBase();
        if (extensionBase != null && !extensionBase.isBlank()) {
            return java.util.Optional.of(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation
                    .Derivation(extensionBase, "extension"));
        }
        return java.util.Optional.empty();
    }

    /**
     * @return the position of the XML element's declaration in its parent compositor
     * ({@code "2 of 6 · sequence"}); empty when no schema is bound or the parent is no compositor
     */
    public java.util.Optional<String> resolveActiveXmlElementOrder(String elementXPath) {
        var map = activeXsdElementMap();
        var element = activeXsdElement(elementXPath);
        if (map == null || element == null || element.getParentXpath() == null) {
            return java.util.Optional.empty();
        }
        var parent = map.get(element.getParentXpath());
        String compositor = parent == null ? null : localXmlName(parent.getCurrentXpath());
        if (parent == null || !isCompositorName(compositor) || parent.getChildren() == null) {
            return java.util.Optional.empty();
        }
        java.util.List<String> particles = parent.getChildren().stream()
                .filter(x -> x != null && !x.contains("@")).toList();
        int index = particles.indexOf(element.getCurrentXpath());
        if (index < 0) {
            return java.util.Optional.empty();
        }
        String kind = compositor.replaceAll("_\\d+$", "").toLowerCase(java.util.Locale.ROOT);
        return java.util.Optional.of(org.fxt.freexmltoolkit.controls.shell.schema.SchemaDerivation
                .format(index + 1, particles.size(), kind));
    }

    /**
     * Checks a value against the schema facets/enumeration of the XML element at the given XPath.
     *
     * @return the validator's message ({@code null} when the value fits); empty when no schema is
     * bound to the active document
     */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult>
            validateActiveXmlElementValue(String elementXPath, String value) {
        Tab tab = host.tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.xmlSchemaProvider != null && elementXPath != null) {
            return java.util.Optional.of(et.xmlSchemaProvider.validateElementValue(elementXPath, value));
        }
        return java.util.Optional.empty();
    }

    /** @return the extra schema facts of the XML element at the given XPath; empty without a schema */
    public java.util.Optional<XmlSchemaDetails> resolveActiveXmlElementDetails(String elementXPath) {
        var element = activeXsdElement(elementXPath);
        if (element == null) {
            return java.util.Optional.empty();
        }
        boolean nillable = element.getCurrentNode() instanceof org.w3c.dom.Element dom
                && "true".equals(dom.getAttribute("nillable").trim());
        var docInfo = element.getXsdDocInfo();
        java.util.List<org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo> rules =
                new java.util.ArrayList<>();
        if (element.getIdentityConstraints() != null) {
            for (var ic : element.getIdentityConstraints()) {
                String detail = "selector " + ic.getSelector() + " · field " + String.join(", ", ic.getFields())
                        + (ic.getRefer() != null && !ic.getRefer().isBlank() ? " · refer " + ic.getRefer() : "");
                rules.add(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo(
                        ic.getTypeDisplayName(), ic.getName(), detail, null));
            }
        }
        if (element.getAssertions() != null) {
            for (var as : element.getAssertions()) {
                rules.add(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo(
                        "Assertion", "", as.getTest(), null));
            }
        }
        if (element.getTypeAlternatives() != null) {
            for (var alt : element.getTypeAlternatives()) {
                rules.add(new org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo(
                        "Alternative", alt.getType(), alt.getDisplayCondition(), null));
            }
        }
        return java.util.Optional.of(new XmlSchemaDetails(nillable,
                docInfo == null ? null : docInfo.getSince(),
                docInfo == null ? null : docInfo.getDeprecated(),
                element.getListItemType(),
                element.getUnionMemberTypes() == null ? java.util.List.of() : java.util.List.copyOf(element.getUnionMemberTypes()),
                element.getGenericAppInfos() == null ? java.util.List.of() : java.util.List.copyOf(element.getGenericAppInfos()),
                rules));
    }

    /** @return schema facts of an attribute of the XML element at the given XPath; empty without a schema */
    public java.util.Optional<org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.AttributeTypeInfo>
            resolveActiveXmlAttributeInfo(String elementXPath, String attributeName) {
        Tab tab = host.tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.xmlSchemaProvider != null && elementXPath != null
                && attributeName != null) {
            try {
                return et.xmlSchemaProvider.getAttributeTypeInfo(elementXPath, attributeName);
            } catch (RuntimeException e) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }

    /** @return the attribute names the schema declares for the XML element at the given XPath */
    public java.util.List<String> resolveActiveXmlValidAttributes(String elementXPath) {
        Tab tab = host.tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et && et.xmlSchemaProvider != null && elementXPath != null) {
            try {
                return et.xmlSchemaProvider.getValidAttributes(elementXPath);
            } catch (RuntimeException e) {
                return java.util.List.of();
            }
        }
        return java.util.List.of();
    }

    /**
     * @return value types for the XML → Excel export of the active XML document, from its bound
     * schema (the open XSD tab's current model, else the model parsed at binding); empty without one
     */
    public java.util.Optional<org.fxt.freexmltoolkit.service.SpreadsheetTypeResolver> activeSpreadsheetTypeResolver() {
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)
                || et.document.getFileType() != EditorFileType.XML || et.schemaFile == null) {
            return java.util.Optional.empty();
        }
        org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = activeBoundSchema(et);
        return schema == null ? java.util.Optional.empty()
                : java.util.Optional.of(new org.fxt.freexmltoolkit.controls.shell.schema.XsdSpreadsheetTypeResolver(schema));
    }

    /** @return the schema model bound to the XML tab: the open XSD tab's current model first */
    private org.fxt.freexmltoolkit.controls.v2.model.XsdSchema activeBoundSchema(EditorTab et) {
        EditorTab schemaTab = findTabFor(et.schemaFile.toPath());
        org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = et.boundSchemaModel;
        if (schemaTab != null && !schemaTab.loading) {
            schemaTab.ensureModelParsed();
            schema = schemaTab.editorContext != null ? schemaTab.editorContext.getSchema() : null;
        }
        return schema;
    }

    /**
     * @return the XSD declaration of the element at {@code elementXPath} (or of its attribute
     * {@code attributeName}) in the active XML document's bound schema; empty without a schema or
     * when the path cannot be mapped unambiguously
     */
    public java.util.Optional<XmlSchemaDeclaration> resolveActiveXmlSchemaDeclaration(String elementXPath,
            String attributeName) {
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)
                || et.document.getFileType() != EditorFileType.XML || et.schemaFile == null || elementXPath == null) {
            return java.util.Optional.empty();
        }
        org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = activeBoundSchema(et);
        if (schema == null) {
            return java.util.Optional.empty();
        }
        var located = org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.locate(schema,
                org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.instancePath(elementXPath),
                attributeName);
        if (located.isEmpty()) {
            return java.util.Optional.empty();
        }
        String reason = located.get().foreign()
                ? "Read-only: declared in an included or imported schema."
                : schemaReadOnlyReason(et.schemaFile);
        return java.util.Optional.of(new XmlSchemaDeclaration(located.get(), schema, et.schemaFile.getName(),
                reason == null, reason));
    }

    /**
     * @return why a bound XSD must not be edited from an XML document, or {@code null}: a copy the
     * application manages (schema cache / library under {@code ~/.freeXmlToolkit}) would be
     * overwritten by the next download, and a read-only file cannot be saved
     */
    static String schemaReadOnlyReason(File xsd) {
        if (xsd == null) {
            return "No schema file.";
        }
        Path file = xsd.toPath().toAbsolutePath().normalize();
        Path appDir = Path.of(System.getProperty("user.home"), ".freeXmlToolkit").toAbsolutePath().normalize();
        if (file.startsWith(appDir)) {
            return "Read-only: a cached copy from the Schema Library.";
        }
        if (Files.exists(file) && !Files.isWritable(file)) {
            return "The schema file is read-only.";
        }
        return null;
    }

    /**
     * Applies {@code edit} to the bound-XSD declaration of the active XML document's element at
     * {@code elementXPath} (or its attribute). The schema opens in a background tab first when it
     * is not open yet, so the change lands on its undo stack and shows as unsaved there; the XML
     * tab keeps the focus. Asynchronous when the schema still has to be loaded.
     *
     * @param onDone receives whether the edit was applied (on the FX thread); may be {@code null}
     */
    public void editActiveXmlSchemaDeclaration(String elementXPath, String attributeName, SchemaEdit edit,
            java.util.function.Consumer<Boolean> onDone) {
        java.util.function.Consumer<Boolean> done = onDone != null ? onDone : ok -> { };
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab xmlTab)
                || xmlTab.schemaFile == null || edit == null) {
            done.accept(false);
            return;
        }
        File xsd = xmlTab.schemaFile;
        withSchemaTab(xsd.toPath(), schemaTab -> {
            schemaTab.ensureModelParsed();
            if (schemaTab.editorContext == null) {
                done.accept(false);
                return;
            }
            var located = org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.locate(
                    schemaTab.editorContext.getSchema(),
                    org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.instancePath(elementXPath),
                    attributeName);
            if (located.isEmpty() || located.get().foreign() || schemaReadOnlyReason(xsd) != null) {
                done.accept(false);
                return;
            }
            boolean ok = edit.apply(new SchemaEditTarget(schemaTab, located.get()));
            if (ok) {
                // The inspector re-reads the declaration from the schema tab's live model.
                host.xmlSchemaProviderRevision.set(host.xmlSchemaProviderRevision.get() + 1);
            }
            done.accept(ok);
        });
    }

    /**
     * Opens the bound XSD of the active XML document in front, in the Graphic view, expanded along
     * the instance path down to the card of the element at {@code elementXPath} (or its
     * attribute), and selects it — by structure, not by name, so same-named local declarations and
     * the several occurrences of a named type's children are told apart.
     */
    public void revealActiveXmlSchemaDeclaration(String elementXPath, String attributeName) {
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab xmlTab)
                || xmlTab.schemaFile == null) {
            return;
        }
        java.util.List<String> path =
                org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.instancePath(elementXPath);
        revealSchemaDeclaration(xmlTab.schemaFile.toPath(), path, attributeName, null);
    }

    /** @return the open editor tab of {@code path}, or {@code null} */
    private EditorTab findTabFor(Path path) {
        Path wanted = path.toAbsolutePath().normalize();
        for (Tab tab : host.tabPane.getTabs()) {
            if (tab instanceof EditorTab et && et.document.getPath() != null
                    && wanted.equals(et.document.getPath().toAbsolutePath().normalize())) {
                return et;
            }
        }
        return null;
    }

    /** Runs {@code action} with the loaded tab of {@code path}, opening it behind the active tab if needed. */
    private void withSchemaTab(Path path, java.util.function.Consumer<EditorTab> action) {
        EditorTab open = findTabFor(path);
        if (open != null) {
            if (open.loading) {
                Runnable previous = open.afterLoad;
                open.afterLoad = () -> {
                    if (previous != null) {
                        previous.run();
                    }
                    action.accept(open);
                };
            } else {
                action.accept(open);
            }
            return;
        }
        EditorHost.rememberRecentFile(path);
        EditorTab tab = new EditorTab(OpenDocument.forPath(path), host::refreshSelectedNode, host.nodeClipboard,
                host::openNamedTypeEditor, this::goToXmlDefinition);
        host.addTab(tab, false);
        tab.view.setGoToDefinitionHandler(this::goToDefinition);
        tab.afterLoad = () -> action.accept(tab);
        host.fileSync.loadAsync(tab, path);
    }

    /**
     * Where the given XSD node is used in the active schema. For a global component (named type,
     * global element/attribute, group) these are the places that reference it; for a local
     * declaration it is the global component that contains it. Builds a reference graph — call it
     * off the FX thread.
     */
    public java.util.List<SchemaUsage> resolveSchemaUsages(org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema,
            XsdNode node) {
        if (schema == null || node == null) {
            return java.util.List.of();
        }
        var key = org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.keyOf(node);
        boolean global = key != null && node.getParent() instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
        java.util.List<SchemaUsage> out = new java.util.ArrayList<>();
        if (global) {
            var graph = org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.build(schema);
            for (var ref : graph.referencesTo(key)) {
                if (ref.isSelfReference() || ref.referrer() == null) {
                    continue;
                }
                XsdNode owner = ref.owner() != null && graph.component(ref.owner()) != null
                        ? graph.component(ref.owner()).node() : null;
                String name = ref.referrer().getName() != null && !ref.referrer().getName().isBlank()
                        ? ref.referrer().getName()
                        : owner != null ? owner.getName() : ref.referrer().getNodeType().name();
                out.add(new SchemaUsage(ref.referrer(), name, usageDetail(ref.referrer(), owner)));
            }
        } else {
            for (XsdNode n = node.getParent(); n != null; n = n.getParent()) {
                if (n.getParent() instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSchema
                        && n.getName() != null && !n.getName().isBlank()) {
                    out.add(new SchemaUsage(n, n.getName(), org.fxt.freexmltoolkit.controls.shell.schema
                            .XsdNodeLabels.kindLabel(n.getNodeType())));
                    break;
                }
            }
        }
        return out;
    }

    private static String usageDetail(XsdNode referrer, XsdNode owner) {
        String kind = org.fxt.freexmltoolkit.controls.shell.schema.XsdNodeLabels.kindLabel(referrer.getNodeType());
        return owner != null && owner != referrer && owner.getName() != null
                ? kind + " in " + owner.getName() : kind;
    }

    /** @return the active document's XSD element map (xpath → extended element), or {@code null}. */
    private java.util.Map<String, org.fxt.freexmltoolkit.domain.XsdExtendedElement> activeXsdElementMap() {
        Tab tab = host.tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et
                && et.xmlSchemaProvider
                        instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter adapter
                && adapter.getXsdDocumentationData() != null) {
            return adapter.getXsdDocumentationData().getExtendedXsdElementMap();
        }
        return null;
    }

    private org.fxt.freexmltoolkit.domain.XsdExtendedElement activeXsdElement(String xpath) {
        var map = activeXsdElementMap();
        if (map == null || xpath == null) {
            return null;
        }
        // Full-path resolution lives in the adapter: predicate-free exact match, then a
        // step-wise walk that tolerates the synthetic compositor segments (SEQUENCE_n/
        // CHOICE_n/…) the documentation model inserts into map keys, and an
        // unambiguous-only name fallback. Never guess among same-named declarations —
        // two distinct elements (e.g. "Account" under AssetDetails vs. under Position)
        // must NOT be conflated.
        Tab tab = host.tabPane.getSelectionModel().getSelectedItem();
        if (tab instanceof EditorTab et
                && et.xmlSchemaProvider
                        instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter adapter) {
            return adapter.resolveElement(xpath);
        }
        return null;
    }

    /**
     * Navigates a selected XML-instance element to its definition in the active document's bound
     * XSD: resolves the element's XPath against the schema, then opens/reveals it in the XSD editor.
     * Shared by the Graphic and Tree context menus (parallels the Text view's "Go to Definition").
     * No-op when no schema is bound or the node is not an element.
     */
    void goToXmlDefinition(org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode node) {
        if (!(node instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement element)) {
            return;
        }
        if (!(host.tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) || et.schemaFile == null) {
            return;
        }
        String xpath = org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath.positional(node);
        revealSchemaDeclaration(et.schemaFile.toPath(),
                org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.instancePath(xpath),
                null, element.getName());
    }

    /**
     * The Text view's "Go to Definition": by the element's instance path when the request carries
     * one (the very declaration, expanded and selected in the Graphic view), else by name.
     */
    void goToDefinition(org.fxt.freexmltoolkit.controls.v2.editor.core.NavigationRequest req) {
        if (req.instanceXPath() != null && !req.instanceXPath().isBlank()) {
            revealSchemaDeclaration(req.xsdFile().toPath(),
                    org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.instancePath(req.instanceXPath()),
                    null, req.elementName());
        } else {
            openXsdAndReveal(req.xsdFile().toPath(), req.elementName());
        }
    }

    /**
     * Opens {@code xsd} in front, in the Graphic view, expanded along {@code instancePath} down to
     * the card of that declaration (or its attribute), and selects it. Found by structure, so
     * same-named local declarations and the occurrences of a named type's children are told apart.
     * Waits for the parse and the diagram of a freshly opened schema; when the path is not in the
     * schema (wildcards, substitution groups), the first declaration named {@code fallbackName} is
     * selected instead.
     *
     * @param xsd           the schema file
     * @param instancePath  element local names from the document root
     * @param attributeName an attribute of the last element, or null
     * @param fallbackName  the name to look for when the path finds nothing (nullable)
     */
    void revealSchemaDeclaration(Path xsd, java.util.List<String> instancePath, String attributeName,
                                 String fallbackName) {
        withSchemaTab(xsd, schemaTab -> {
            host.tabPane.getSelectionModel().select(schemaTab);
            host.setActiveViewMode(ViewMode.GRAPHIC);
            revealWhenGraphReady(schemaTab, instancePath, attributeName, fallbackName, REVEAL_ATTEMPTS);
        });
    }

    /** Retries of {@link #revealWhenGraphReady} 50 ms apart: the parse and the diagram are built asynchronously. */
    private static final int REVEAL_ATTEMPTS = 100;

    private void revealWhenGraphReady(EditorTab schemaTab, java.util.List<String> instancePath,
                                      String attributeName, String fallbackName, int attemptsLeft) {
        if (EditorTab.isAbandoned(schemaTab) || !schemaTab.isSelected()) {
            return; // closed, or the user moved on to another tab meanwhile
        }
        var graph = schemaTab.xsdGraphView;
        var context = schemaTab.editorContext;
        boolean ready = schemaTab.viewMode == ViewMode.GRAPHIC && context != null && graph != null
                && graph.getEditorContext() == context;
        if (!ready) {
            if (attemptsLeft > 0) {
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));
                pause.setOnFinished(e -> revealWhenGraphReady(
                        schemaTab, instancePath, attributeName, fallbackName, attemptsLeft - 1));
                pause.play();
            }
            return;
        }
        var located = instancePath == null || instancePath.isEmpty() ? java.util.Optional
                .<org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.Located>empty()
                : org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.locate(
                        context.getSchema(), instancePath, attributeName);
        if (located.isPresent() && graph.revealModelPath(located.get().particlePath())) {
            return;
        }
        XsdNode byName = located.isPresent() ? null : findNodeByName(context.getSchema(), fallbackName);
        if (byName != null) {
            selectNodeInActiveTree(byName);
        }
    }

    /** @return the local element name from an XPath/child reference (last segment, no prefix/predicate). */
    private static String localXmlName(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String last = ref.substring(ref.lastIndexOf('/') + 1);
        int bracket = last.indexOf('[');
        if (bracket >= 0) {
            last = last.substring(0, bracket);
        }
        int colon = last.indexOf(':');
        return colon >= 0 ? last.substring(colon + 1) : last;
    }
}
