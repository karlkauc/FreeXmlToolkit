# Model Layer MVP - Implementation Guide

> **Status**: MVP Implemented
> **Date**: 2025-11-07
> **Version**: 1.0

---

## Overview

This document describes the MVP (Minimum Viable Product) Model Layer implementation and provides a complete roadmap for achieving full production-ready Model integration.

---

## ✅ What Has Been Implemented (MVP)

### 1. Core Model Classes

**Location**: `src/main/java/org/fxt/freexmltoolkit/controls/v2/model/`

The following model classes have been created with PropertyChangeSupport:

#### XsdNode (Base Class)
- **File**: `XsdNode.java`
- **Features**:
  - Unique ID generation (UUID)
  - Name management with property change events
  - Parent-child relationships
  - Cardinality (minOccurs/maxOccurs with UNBOUNDED support)
  - Documentation and AppInfo annotations
  - PropertyChangeListener support
  - Descendant checking (circular reference prevention)

#### XsdNodeType (Enum)
- **File**: `XsdNodeType.java`
- **Types**: SCHEMA, ELEMENT, ATTRIBUTE, COMPLEX_TYPE, SIMPLE_TYPE, SEQUENCE, CHOICE, ALL, GROUP, ATTRIBUTE_GROUP, ANNOTATION, DOCUMENTATION, APPINFO

#### XsdElement
- **File**: `XsdElement.java`
- **Features**:
  - Type reference
  - Nillable flag
  - Abstract flag
  - Fixed value
  - Default value
  - Substitution group

#### XsdAttribute
- **File**: `XsdAttribute.java`
- **Features**:
  - Type reference
  - Use (required/optional/prohibited)
  - Fixed value
  - Default value
  - Form (qualified/unqualified)

#### XsdComplexType
- **File**: `XsdComplexType.java`
- **Features**:
  - Mixed content flag
  - Abstract flag

#### XsdSequence / XsdChoice
- **Files**: `XsdSequence.java`, `XsdChoice.java`
- **Features**: Compositor nodes for ordering elements

#### XsdSchema
- **File**: `XsdSchema.java`
- **Features**:
  - Target namespace
  - elementFormDefault / attributeFormDefault
  - Namespace prefix management

---

## 🔴 What Is NOT Implemented (Requires Full Implementation)

### 1. Missing Model Classes

The following XSD constructs do not yet have model classes:

#### Simple Types & Restrictions
- **XsdSimpleType**: Base class for simple types
- **XsdRestriction**: Restrictions with facets
- **XsdFacet**: Individual facet (minLength, maxLength, pattern, enumeration, etc.)
- **XsdList**: List type
- **XsdUnion**: Union type

#### Complex Type Extensions
- **XsdSimpleContent**: Simple content with attributes
- **XsdComplexContent**: Complex content with extensions/restrictions
- **XsdExtension**: Type extension
- **XsdRestriction** (for complex types)

#### Groups
- **XsdGroup**: Named group of elements
- **XsdAttributeGroup**: Named group of attributes
- **XsdAll**: All compositor

#### Identity Constraints
- **XsdKey**: Key constraint
- **XsdKeyRef**: Key reference
- **XsdUnique**: Uniqueness constraint
- **XsdSelector**: XPath selector for constraints
- **XsdField**: XPath field for constraints

#### XSD 1.1 Features
- **XsdAssert**: Assertion (xs:assert)
- **XsdAlternative**: Type alternative (xs:alternative)
- **XsdOpenContent**: Open content model
- **XsdOverride**: Schema override

#### Import/Include
- **XsdImport**: xs:import
- **XsdInclude**: xs:include
- **XsdRedefine**: xs:redefine (deprecated in 1.1)

---

### 2. Model-View Synchronization

**Problem**: Currently VisualNode and Model are separate.
**Required**:

#### VisualNode Adapter Pattern
```java
public class VisualNode {
    private XsdNode modelNode; // Link to model

    public VisualNode(XsdNode modelNode) {
        this.modelNode = modelNode;

        // Listen to model changes
        modelNode.addPropertyChangeListener(evt -> {
            // Update visual representation
            updateVisualsFromModel();
        });
    }

    public XsdNode getModelNode() {
        return modelNode;
    }

    private void updateVisualsFromModel() {
        // Sync visual properties from model
        this.label = modelNode.getName();
        this.minOccurs = modelNode.getMinOccurs();
        // ... etc
    }
}
```

#### Model Factory
Create factory to convert DOM → Model:
```java
public class XsdModelFactory {
    public XsdSchema createFromDom(Document doc) {
        // Parse DOM and create Model tree
    }

    public XsdSchema createFromFile(Path xsdFile) {
        // Load XSD and create Model tree
    }
}
```

---

### 3. Command Integration with Model

**Problem**: All commands currently work with VisualNode.
**Required**: Refactor all commands to operate on XsdNode.

#### Example Refactoring (AddElementCommand)

**Current**:
```java
public class AddElementCommand implements XsdCommand {
    private final VisualNode parent;
    private final VisualNode newElement;
    // ...
}
```

**Target**:
```java
public class AddElementCommand implements XsdCommand {
    private final XsdNode parent;        // Model node
    private final XsdElement newElement;  // Model node

    @Override
    public boolean execute() {
        parent.addChild(newElement);  // Model operation
        return true;  // VisualNode updates automatically via PropertyChangeListener
    }

    @Override
    public boolean undo() {
        parent.removeChild(newElement);
        return true;
    }
}
```

#### Commands to Refactor
1. **AddElementCommand** → operates on XsdNode
2. **DeleteNodeCommand** → operates on XsdNode
3. **RenameNodeCommand** → operates on XsdNode
4. **DuplicateNodeCommand** → deep copy XsdNode with all properties
5. **MoveNodeCommand** → operates on XsdNode
6. **ChangeTypeCommand** → operates on XsdElement/XsdAttribute
7. **ChangeCardinalityCommand** → operates on XsdNode

---

### 4. Enhanced Serialization

**Problem**: Current XsdSerializer only handles basic structures.
**Required**: Full Model-to-XSD XML serialization.

#### Architecture

```java
public class XsdModelSerializer {
    private final DocumentBuilder docBuilder;
    private final Transformer transformer;

    public String serialize(XsdSchema schema) {
        Document doc = docBuilder.newDocument();
        Element schemaElement = createSchemaElement(doc, schema);

        // Recursively serialize all children
        for (XsdNode child : schema.getChildren()) {
            Element childElement = serializeNode(doc, child);
            schemaElement.appendChild(childElement);
        }

        // Pretty-print to string
        return transformToString(doc);
    }

    private Element serializeNode(Document doc, XsdNode node) {
        return switch (node.getNodeType()) {
            case ELEMENT -> serializeElement(doc, (XsdElement) node);
            case ATTRIBUTE -> serializeAttribute(doc, (XsdAttribute) node);
            case COMPLEX_TYPE -> serializeComplexType(doc, (XsdComplexType) node);
            case SEQUENCE -> serializeSequence(doc, (XsdSequence) node);
            case CHOICE -> serializeChoice(doc, (XsdChoice) node);
            // ... all other types
        };
    }

    private Element serializeElement(Document doc, XsdElement element) {
        Element elem = doc.createElement("xs:element");
        elem.setAttribute("name", element.getName());

        if (element.getType() != null) {
            elem.setAttribute("type", element.getType());
        }

        if (element.getMinOccurs() != 1) {
            elem.setAttribute("minOccurs", String.valueOf(element.getMinOccurs()));
        }

        if (element.getMaxOccurs() == XsdNode.UNBOUNDED) {
            elem.setAttribute("maxOccurs", "unbounded");
        } else if (element.getMaxOccurs() != 1) {
            elem.setAttribute("maxOccurs", String.valueOf(element.getMaxOccurs()));
        }

        if (element.isNillable()) {
            elem.setAttribute("nillable", "true");
        }

        if (element.isAbstract()) {
            elem.setAttribute("abstract", "true");
        }

        // Documentation/AppInfo
        if (element.getDocumentation() != null || element.getAppinfo() != null) {
            Element annotation = serializeAnnotation(doc, element);
            elem.appendChild(annotation);
        }

        // Serialize children (inline complex type)
        for (XsdNode child : element.getChildren()) {
            elem.appendChild(serializeNode(doc, child));
        }

        return elem;
    }

    // Similar methods for all other node types...
}
```

#### Features to Implement
- ✅ **Basic structure** (Element, Attribute, ComplexType, Sequence, Choice)
- [ ] **SimpleType with restrictions**
- [ ] **Facets** (pattern, length, enumeration, etc.)
- [ ] **List and Union types**
- [ ] **Extensions and Restrictions**
- [ ] **Groups and AttributeGroups**
- [ ] **Identity Constraints**
- [ ] **Namespace prefix management**
- [ ] **Annotation preservation** (documentation, appinfo)
- [ ] **XSD 1.1 features** (assert, alternative, openContent)
- [ ] **Pretty-print with configurable indentation**
- [ ] **Comment preservation** (if possible)

---

### 5. Properties Panel Full Integration

**Problem**: Only General section works, others are placeholders.
**Required**: Implement model-backed editors for all sections.

#### Documentation Section

```java
private void setupDocumentationListeners() {
    documentationArea.textProperty().addListener((obs, oldVal, newVal) -> {
        if (!updating && currentModelNode != null) {
            ChangeDocumentationCommand cmd = new ChangeDocumentationCommand(
                currentModelNode, newVal
            );
            editorContext.getCommandManager().executeCommand(cmd);
        }
    });

    appinfoArea.textProperty().addListener((obs, oldVal, newVal) -> {
        if (!updating && currentModelNode != null) {
            ChangeAppinfoCommand cmd = new ChangeAppinfoCommand(
                currentModelNode, newVal
            );
            editorContext.getCommandManager().executeCommand(cmd);
        }
    });
}
```

#### Constraints Section

```java
private void setupConstraintsListeners() {
    nillableCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
        if (!updating && currentModelNode instanceof XsdElement element) {
            ChangeNillableCommand cmd = new ChangeNillableCommand(element, newVal);
            editorContext.getCommandManager().executeCommand(cmd);
        }
    });

    // Similar for abstract, fixed...
}
```

#### Advanced Section

```java
private void setupAdvancedListeners() {
    formComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
        if (!updating && currentModelNode != null) {
            ChangeFormCommand cmd = new ChangeFormCommand(currentModelNode, newVal);
            editorContext.getCommandManager().executeCommand(cmd);
        }
    });

    // Similar for use, substitutionGroup...
}
```

#### New Commands Required
- **ChangeDocumentationCommand**
- **ChangeAppinfoCommand**
- **ChangeNillableCommand**
- **ChangeAbstractCommand**
- **ChangeFixedCommand**
- **ChangeFormCommand**
- **ChangeUseCommand**
- **ChangeSubstitutionGroupCommand**

---

## 📋 Complete Implementation Roadmap

### Phase 1: Core Model Completion ✅ **COMPLETED** (2025-11-07)

**Week 1: Simple Types & Restrictions** ✅
- [x] Create XsdSimpleType class
- [x] Create XsdRestriction class
- [x] Create XsdFacet class with all facet types (XsdFacetType enum)
- [x] Create XsdList class
- [x] Create XsdUnion class
- [ ] Unit tests for simple type model

**Week 2: Complex Type Extensions** ✅
- [x] Create XsdSimpleContent class
- [x] Create XsdComplexContent class
- [x] Create XsdExtension class
- [x] XsdRestriction already handles complex types
- [ ] Unit tests for complex content model

**Week 3: Groups & Constraints** ✅
- [x] Create XsdGroup class
- [x] Create XsdAttributeGroup class
- [x] Create XsdAll class
- [x] Create identity constraint classes (XsdKey, XsdKeyRef, XsdUnique, XsdIdentityConstraint base class)
- [x] Create XsdSelector and XsdField classes
- [ ] Unit tests for groups and constraints

**Build Status**: ✅ BUILD SUCCESSFUL - All 16 new model classes compile without errors

---

### Phase 2: Model-View Integration (2-3 weeks) ✅ **IN PROGRESS**

**Week 1: Model Factory & Parser** ✅ **COMPLETED** (2025-11-07)
- [x] Create XsdNodeFactory (~1000 lines) - `XsdNodeFactory.java:1-1050`
- [x] Implement DOM → Model parsing (all XSD constructs supported)
- [x] Implement File → Model loading (fromString/fromFile methods)
- [x] Handle all XSD constructs in parser (elements, attributes, types, compositors, etc.)
- [x] Unit tests for model factory (8 tests in XsdNodeFactoryTest.java - all passing)
- [x] Create XsdSchemaAdapter to convert XsdSchema → XsdSchemaModel - `XsdSchemaAdapter.java:1-329`
- [x] Integration with XsdController - `XsdController.java:1262-1268`
- [x] **Edit Mode activated** - `XsdController.java:1280-1283`

**Week 2: VisualNode Adapter** ✅ **COMPLETED** (2025-11-07 afternoon)
- [x] Refactor VisualNode to hold XsdNode reference - `XsdNodeRenderer.java:418-469`
- [x] Implement PropertyChangeListener in VisualNode - registers listener for XsdNode objects
- [x] Create XsdVisualTreeBuilder for direct XsdSchema → VisualNode mapping - `XsdVisualTreeBuilder.java:1-205`
- [x] Add dual-model support to XsdGraphView - `XsdGraphView.java:66-138`
- [x] Maintain backward compatibility with XsdSchemaModel
- [ ] Integration tests for Model ↔ View sync (deferred to Phase 2.3)

**Week 3: XsdNodeRenderer Integration & Model-View Sync** ✅ **COMPLETED** (2025-11-07 evening)
- [x] Implement PropertyChangeListener redraw callback in VisualNode - `XsdNodeRenderer.java:450-474`
- [x] Update XsdVisualTreeBuilder to pass redraw callback to all VisualNodes - `XsdVisualTreeBuilder.java:24-209`
- [x] Update XsdGraphView to provide redraw callback for automatic view updates - `XsdGraphView.java:320`
- [x] Use Platform.runLater to ensure callbacks execute on JavaFX Application Thread
- [x] Ensure all node types render correctly (elements, attributes, compositors)
- [x] Integration tests for Model ↔ View synchronization - `XsdModelViewSyncTest.java:1-198`
- [ ] Performance testing with large schemas (deferred)

---

### Phase 3: Command Refactoring (1-2 weeks)

**Week 1: Basic Commands**
- [ ] Refactor AddElementCommand to use XsdNode
- [ ] Refactor DeleteNodeCommand to use XsdNode
- [ ] Refactor RenameNodeCommand to use XsdNode
- [ ] Refactor DuplicateNodeCommand with deep copy
- [ ] Refactor MoveNodeCommand to use XsdNode
- [ ] Unit tests for refactored commands

**Week 2: Advanced Commands**
- [ ] Refactor ChangeTypeCommand to use Model
- [ ] Refactor ChangeCardinalityCommand to use Model
- [ ] Create all new Commands for Properties Panel
- [ ] Integration tests for all commands with Model

---

### Phase 4: Full Serialization (2-3 weeks)

**Week 1: Core Serialization**
- [ ] Create XsdModelSerializer class
- [ ] Implement schema, element, attribute serialization
- [ ] Implement complexType, sequence, choice serialization
- [ ] Implement namespace management
- [ ] Unit tests for core serialization

**Week 2: Advanced Features**
- [ ] Implement simpleType & restriction serialization
- [ ] Implement facet serialization
- [ ] Implement extension/restriction serialization
- [ ] Implement group & attributeGroup serialization
- [ ] Unit tests for advanced features

**Week 3: XSD 1.1 & Polish**
- [ ] Implement assertion serialization
- [ ] Implement alternative serialization
- [ ] Implement openContent serialization
- [ ] Pretty-print with configurable indentation
- [ ] Comment preservation (if possible)
- [ ] Round-trip tests (load → modify → save → load)

---

### Phase 5: Properties Panel Completion (1 week)

- [ ] Implement Documentation/AppInfo integration
- [ ] Implement Constraints integration
- [ ] Implement Advanced properties integration
- [ ] Create all required Commands
- [ ] Integration tests for Properties Panel

---

### Phase 6: Testing & Polish (1-2 weeks)

- [ ] Comprehensive unit test suite
- [ ] Integration test suite
- [ ] Performance testing with large schemas
- [ ] Memory leak testing
- [ ] Bug fixing
- [ ] Documentation

---

## 🎯 Total Estimated Effort

**Minimum**: 9 weeks (full-time development)
**Realistic**: 12-14 weeks (with testing and polish)
**With interruptions**: 16-20 weeks

---

## 🔧 Current MVP Status

### What Works Now

1. ✅ **All model classes compile** successfully (24 classes total)
2. ✅ **PropertyChangeSupport** is integrated in all classes
3. ✅ **Basic model hierarchy** (parent-child relationships)
4. ✅ **Cardinality management** (minOccurs/maxOccurs with UNBOUNDED)
5. ✅ **Documentation/AppInfo storage**
6. ✅ **Simple Type model** (XsdSimpleType, XsdRestriction, XsdFacet, XsdList, XsdUnion)
7. ✅ **Complex Type Extensions** (XsdSimpleContent, XsdComplexContent, XsdExtension)
8. ✅ **Groups** (XsdGroup, XsdAttributeGroup, XsdAll)
9. ✅ **Identity Constraints** (XsdKey, XsdKeyRef, XsdUnique, XsdSelector, XsdField)
10. ✅ **Facet Types** (14 facet types including XSD 1.1)

### What Is Missing

1. ❌ **Model Factory** (DOM → Model parsing)
2. ❌ **VisualNode-Model integration**
3. ❌ **Commands using Model**
4. ❌ **Full serialization** (Model → XSD XML)
5. ❌ **Properties Panel model integration**
6. ❌ **Unit tests** for all model classes

---

## 📝 Recommended Next Steps

### For Immediate Production Use

**Option 1: Hybrid Approach** (Recommended for short-term)
- Keep current VisualNode-based system
- Use Model classes only for serialization
- Gradually migrate commands one-by-one
- Low risk, incremental improvement

**Option 2: Full Migration** (Recommended for long-term)
- Follow complete roadmap above
- Replace all VisualNode logic with Model
- Achieve full XSD feature support
- Higher initial effort, better long-term maintainability

### For MVP Continuation

1. **Create Model Factory** (Week 1)
   - Parser that creates Model from existing DOM
   - Allows gradual integration

2. **Refactor SaveCommand** (Week 2)
   - Use new XsdModelSerializer
   - Keep VisualNode → Model conversion temporarily

3. **Migrate one Command at a time** (Weeks 3-6)
   - Start with simplest (RenameNodeCommand)
   - Test thoroughly before moving to next
   - Reduces risk of breaking existing functionality

---

## 📚 References

- **XSD 1.0 Specification**: https://www.w3.org/TR/xmlschema-0/
- **XSD 1.1 Specification**: https://www.w3.org/TR/xmlschema11-1/
- **PropertyChangeSupport**: Java Beans specification
- **Command Pattern**: Gang of Four Design Patterns

---

## Change Log

- **2025-11-07 (Morning)**: MVP Model Layer created (XsdNode, XsdElement, XsdAttribute, XsdComplexType, XsdSequence, XsdChoice, XsdSchema)
- **2025-11-07 (Morning)**: Implementation roadmap documented
- **2025-11-07 (Morning)**: **Phase 1 COMPLETED** - All core model classes implemented:
  - Simple Types: XsdSimpleType, XsdRestriction, XsdFacet, XsdFacetType (enum), XsdList, XsdUnion
  - Complex Types: XsdSimpleContent, XsdComplexContent, XsdExtension
  - Groups: XsdGroup, XsdAttributeGroup, XsdAll
  - Identity Constraints: XsdIdentityConstraint (base), XsdKey, XsdKeyRef, XsdUnique, XsdSelector, XsdField
  - **Total: 24 model classes, all compiling successfully (BUILD SUCCESSFUL)**
- **2025-11-07 (Afternoon)**: **Phase 2.1 COMPLETED** - Model Factory & Parser:
  - Created XsdNodeFactory (~1000 lines) with DOM → Model parsing
  - Implemented fromString/fromFile methods for loading XSD schemas
  - Created XsdSchemaAdapter to convert XsdSchema → XsdSchemaModel
  - Integrated with XsdController for loading XSD files
  - Created 8 unit tests (XsdNodeFactoryTest.java) - all passing
  - Fixed expand/collapse bug (XsdNodeFactory.java:203-205)
  - **Edit Mode activated** in XsdController (XsdController.java:1280-1283)
  - Application running successfully with new model integration
