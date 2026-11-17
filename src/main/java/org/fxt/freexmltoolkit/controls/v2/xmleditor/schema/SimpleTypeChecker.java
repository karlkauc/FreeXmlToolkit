/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.fxt.freexmltoolkit.controls.v2.xmleditor.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.dv.InvalidDatatypeValueException;
import org.apache.xerces.impl.dv.SchemaDVFactory;
import org.apache.xerces.impl.dv.ValidatedInfo;
import org.apache.xerces.impl.dv.XSFacets;
import org.apache.xerces.impl.dv.XSSimpleType;
import org.apache.xerces.impl.dv.xs.TypeValidatorHelper;
import org.apache.xerces.impl.validation.ValidationState;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.apache.xerces.xs.XSTypeDefinition;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.domain.GlobalDefinitionLookup;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Checks values against the simple type of an XSD element or attribute declaration with XSD semantics: built-in
 * lexical spaces, implicit bounds of derived built-ins (unsignedByte, positiveInteger, …), every facet of every
 * derivation step (XSD regular expressions, whiteSpace, total/fractionDigits, inclusive and exclusive bounds,
 * lengths, enumerations, explicitTimezone), lists, unions and simple content of complex types.
 *
 * <p>The type is built from the schema's DOM with the Xerces datatype library — the engine the validation uses —
 * so the editor's inputs agree with the validation. UI-free and thread-safe after construction.</p>
 */
public final class SimpleTypeChecker {

    private static final Logger logger = LogManager.getLogger(SimpleTypeChecker.class);

    /** The XML Schema namespace. */
    public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    /** How the values of the type are built. */
    public enum Variety { ATOMIC, LIST, UNION }

    private static final SchemaDVFactory FACTORY = SchemaDVFactory.getInstance(
            "org.apache.xerces.impl.dv.xs.Schema11DVFactoryImpl");

    private final XSSimpleType type;

    private SimpleTypeChecker(XSSimpleType type) {
        this.type = type;
    }

    /**
     * The checker of a built-in type.
     *
     * @param localName the built-in's local name, for example {@code decimal}
     * @return the checker, or empty for an unknown name and for {@code anySimpleType}/{@code anyAtomicType}, which
     *         accept every value
     */
    public static Optional<SimpleTypeChecker> builtin(String localName) {
        XSSimpleType t = builtinType(localName);
        return t == null ? Optional.empty() : Optional.of(new SimpleTypeChecker(t));
    }

    /**
     * The checker of the value of an {@code xs:element} or {@code xs:attribute} declaration.
     *
     * @param declaration the declaration (a {@code ref} is followed through {@code lookup})
     * @param lookup      finds global declarations by QName; may be {@code null} (then only built-in types and
     *                    inline types resolve)
     * @return the checker, or empty when the declaration has no simple value (element-only or mixed content,
     *         {@code anyType}) or its type cannot be built
     */
    public static Optional<SimpleTypeChecker> forDeclaration(Node declaration, GlobalDefinitionLookup lookup) {
        if (!(declaration instanceof Element)) {
            return Optional.empty();
        }
        try {
            XSSimpleType t = new Builder(lookup).declaration((Element) declaration);
            return t == null ? Optional.empty() : Optional.of(new SimpleTypeChecker(t));
        } catch (RuntimeException e) {
            logger.debug("Cannot build the simple type of {}: {}", declaration.getNodeName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Checks a value.
     *
     * @param value the value as entered (whitespace is normalised as the type demands)
     * @return valid, or an error result (not valid) carrying the reason
     */
    public ValidationResult check(String value) {
        try {
            type.validate(value == null ? "" : value, newContext(), new ValidatedInfo());
            return ValidationResult.valid();
        } catch (InvalidDatatypeValueException e) {
            return ValidationResult.error(e.getMessage());
        } catch (RuntimeException e) {
            // QName prefixes, ENTITY declarations, … need document context the editor does not have
            return ValidationResult.valid();
        }
    }

    /** @return {@code true} when {@code value} is valid for the type */
    public boolean isValid(String value) {
        return check(value).isValid();
    }

    /** @return how the values of the type are built */
    public Variety variety() {
        return switch (type.getVariety()) {
            case XSSimpleTypeDefinition.VARIETY_LIST -> Variety.LIST;
            case XSSimpleTypeDefinition.VARIETY_UNION -> Variety.UNION;
            default -> Variety.ATOMIC;
        };
    }

    /**
     * The local name of the nearest built-in type the type derives from ({@code unsignedByte} for a restriction
     * of it, {@code decimal} for FundsXML's {@code PercentageType}); for a list or union not derived from a
     * built-in, {@code null}.
     */
    public String builtinName() {
        XSTypeDefinition t = type;
        while (t != null) {
            if (XSD_NS.equals(t.getNamespace()) && t.getName() != null) {
                String name = t.getName();
                return "anySimpleType".equals(name) || "anyAtomicType".equals(name) ? null : name;
            }
            XSTypeDefinition base = t.getBaseType();
            if (base == t) {
                return null;
            }
            t = base;
        }
        return null;
    }

    /** @return the built-in the items of a list type derive from, or {@code null} */
    public String itemBuiltinName() {
        XSSimpleTypeDefinition item = type.getItemType();
        return item instanceof XSSimpleType st ? new SimpleTypeChecker(st).builtinName() : null;
    }

    /**
     * The values a user can pick from: the type's enumeration, or for a union whose members are all enumerated the
     * members' values in order.
     */
    public List<String> enumerations() {
        List<String> own = strings(type.getLexicalEnumeration());
        if (!own.isEmpty() || type.getVariety() != XSSimpleTypeDefinition.VARIETY_UNION) {
            return own;
        }
        Set<String> values = new LinkedHashSet<>();
        for (XSSimpleTypeDefinition member : members()) {
            values.addAll(strings(member.getLexicalEnumeration()));
        }
        return List.copyOf(values);
    }

    /** @return {@code true} when every allowed value is one of {@link #enumerations()} */
    public boolean isClosedEnumeration() {
        if (!strings(type.getLexicalEnumeration()).isEmpty()) {
            return true;
        }
        if (type.getVariety() != XSSimpleTypeDefinition.VARIETY_UNION) {
            return false;
        }
        List<XSSimpleTypeDefinition> members = members();
        return !members.isEmpty() && members.stream().allMatch(m -> m.getLexicalEnumeration().getLength() > 0);
    }

    /**
     * The effective facets (own and inherited) by XSD name: {@code length}, {@code minLength}, {@code maxLength},
     * {@code whiteSpace}, {@code minInclusive}, {@code maxInclusive}, {@code minExclusive}, {@code maxExclusive},
     * {@code totalDigits}, {@code fractionDigits}, {@code explicitTimezone}, {@code pattern} (the patterns of all
     * steps, one per line). Enumerations are in {@link #enumerations()}.
     */
    public Map<String, String> facets() {
        Map<String, String> facets = new LinkedHashMap<>();
        put(facets, "length", XSSimpleTypeDefinition.FACET_LENGTH);
        put(facets, "minLength", XSSimpleTypeDefinition.FACET_MINLENGTH);
        put(facets, "maxLength", XSSimpleTypeDefinition.FACET_MAXLENGTH);
        put(facets, "whiteSpace", XSSimpleTypeDefinition.FACET_WHITESPACE);
        put(facets, "minInclusive", XSSimpleTypeDefinition.FACET_MININCLUSIVE);
        put(facets, "maxInclusive", XSSimpleTypeDefinition.FACET_MAXINCLUSIVE);
        put(facets, "minExclusive", XSSimpleTypeDefinition.FACET_MINEXCLUSIVE);
        put(facets, "maxExclusive", XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE);
        put(facets, "totalDigits", XSSimpleTypeDefinition.FACET_TOTALDIGITS);
        put(facets, "fractionDigits", XSSimpleTypeDefinition.FACET_FRACTIONDIGITS);
        put(facets, "explicitTimezone", XSSimpleTypeDefinition.FACET_EXPLICITTIMEZONE);
        List<String> patterns = strings(type.getLexicalPattern());
        if (!patterns.isEmpty()) {
            facets.put("pattern", String.join("\n", patterns));
        }
        return facets;
    }

    private void put(Map<String, String> facets, String name, short facet) {
        if (type.isDefinedFacet(facet)) {
            String value = type.getLexicalFacetValue(facet);
            if (value != null) {
                facets.put(name, value);
            }
        }
    }

    private List<XSSimpleTypeDefinition> members() {
        XSObjectList list = type.getMemberTypes();
        List<XSSimpleTypeDefinition> members = new ArrayList<>();
        for (int i = 0; list != null && i < list.getLength(); i++) {
            if (list.item(i) instanceof XSSimpleTypeDefinition m) {
                members.add(m);
            }
        }
        return members;
    }

    private static List<String> strings(StringList list) {
        if (list == null || list.getLength() == 0) {
            return List.of();
        }
        List<String> values = new ArrayList<>(list.getLength());
        for (int i = 0; i < list.getLength(); i++) {
            values.add(list.item(i));
        }
        return Collections.unmodifiableList(values);
    }

    private static ValidationState newContext() {
        ValidationState context = new ValidationState();
        context.setExtraChecking(false);
        context.setFacetChecking(true);
        context.setNormalizationRequired(true);
        context.setUsingNamespaces(false);
        context.setTypeValidatorHelper(TypeValidatorHelper.getInstance(Constants.SCHEMA_VERSION_1_1));
        context.setLocale(Locale.ENGLISH);
        return context;
    }

    private static XSSimpleType builtinType(String localName) {
        if (localName == null || "anySimpleType".equals(localName) || "anyAtomicType".equals(localName)) {
            return null;
        }
        return FACTORY.getBuiltInType(localName);
    }

    // ==================== Building types from the schema DOM ====================

    /** Builds Xerces simple types from declarations; one instance per declaration, cycle-safe. */
    private static final class Builder {

        private final GlobalDefinitionLookup lookup;
        private final Map<Node, XSSimpleType> built = new IdentityHashMap<>();
        private final Set<Node> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

        Builder(GlobalDefinitionLookup lookup) {
            this.lookup = lookup;
        }

        XSSimpleType declaration(Element decl) {
            Element target = decl;
            String ref = attr(decl, "ref");
            if (ref != null) {
                Node global = find(decl.getLocalName(), ref, decl);
                if (!(global instanceof Element g)) {
                    return null;
                }
                target = g;
            }
            String typeName = attr(target, "type");
            if (typeName != null) {
                return byName(typeName, target);
            }
            Element simple = child(target, "simpleType");
            if (simple != null) {
                return simpleType(simple);
            }
            Element complex = child(target, "complexType");
            return complex != null ? complexType(complex) : null;
        }

        private XSSimpleType byName(String qName, Element context) {
            int colon = qName.indexOf(':');
            String prefix = colon > 0 ? qName.substring(0, colon) : null;
            String local = qName.substring(colon + 1);
            String ns = context.lookupNamespaceURI(prefix);
            if (XSD_NS.equals(ns)) {
                return builtinType(local);
            }
            Node simple = find("simpleType", qName, context);
            if (simple instanceof Element s) {
                return simpleType(s);
            }
            Node complex = find("complexType", qName, context);
            if (complex instanceof Element c) {
                return complexType(c);
            }
            // An unbound xs/xsd prefix in a fragment: still a built-in
            if (ns == null && ("xs".equals(prefix) || "xsd".equals(prefix))) {
                return builtinType(local);
            }
            return null;
        }

        private Node find(String kind, String qName, Element context) {
            return lookup == null ? null : lookup.find(kind, qName, context);
        }

        private XSSimpleType simpleType(Element node) {
            if (built.containsKey(node)) {
                return built.get(node);
            }
            if (!visiting.add(node)) {
                return null; // circular derivation: invalid schema
            }
            try {
                XSSimpleType result = buildSimpleType(node);
                built.put(node, result);
                return result;
            } finally {
                visiting.remove(node);
            }
        }

        private XSSimpleType buildSimpleType(Element node) {
            String name = attr(node, "name");
            String tns = targetNamespace(node);
            Element restriction = child(node, "restriction");
            if (restriction != null) {
                XSSimpleType base = restrictionBase(restriction);
                return base == null ? null : restrict(name, tns, base, restriction);
            }
            Element list = child(node, "list");
            if (list != null) {
                String itemType = attr(list, "itemType");
                Element inline = child(list, "simpleType");
                XSSimpleType item = itemType != null ? byName(itemType, list) : inline != null ? simpleType(inline) : null;
                return item == null ? null : FACTORY.createTypeList(name, tns, (short) 0, item, null);
            }
            Element union = child(node, "union");
            if (union != null) {
                List<XSSimpleType> members = new ArrayList<>();
                String memberTypes = attr(union, "memberTypes");
                if (memberTypes != null) {
                    for (String member : memberTypes.trim().split("\\s+")) {
                        members.add(byName(member, union));
                    }
                }
                for (Element inline : children(union, "simpleType")) {
                    members.add(simpleType(inline));
                }
                if (members.isEmpty() || members.contains(null)) {
                    return null;
                }
                return FACTORY.createTypeUnion(name, tns, (short) 0, members.toArray(new XSSimpleType[0]), null);
            }
            return null;
        }

        private XSSimpleType restrictionBase(Element restriction) {
            Element inline = child(restriction, "simpleType");
            if (inline != null) {
                return simpleType(inline);
            }
            String base = attr(restriction, "base");
            return base == null ? null : byName(base, restriction);
        }

        private XSSimpleType complexType(Element node) {
            if (built.containsKey(node)) {
                return built.get(node);
            }
            if (!visiting.add(node)) {
                return null;
            }
            try {
                XSSimpleType result = null;
                Element simpleContent = child(node, "simpleContent");
                if (simpleContent != null) {
                    Element extension = child(simpleContent, "extension");
                    Element restriction = child(simpleContent, "restriction");
                    if (extension != null && attr(extension, "base") != null) {
                        result = byName(attr(extension, "base"), extension);
                    } else if (restriction != null) {
                        XSSimpleType base = restrictionBase(restriction);
                        result = base == null ? null
                                : restrict(attr(node, "name"), targetNamespace(node), base, restriction);
                    }
                }
                built.put(node, result);
                return result;
            } finally {
                visiting.remove(node);
            }
        }

        /** A restriction of {@code base} with the facets declared in {@code restriction}. */
        private XSSimpleType restrict(String name, String tns, XSSimpleType base, Element restriction) {
            XSFacets facets = new XSFacets();
            int present = 0;
            StringBuilder pattern = null;
            Vector<String> enumeration = null;
            for (Element facet : children(restriction, null)) {
                String value = attr(facet, "value");
                if (value == null) {
                    continue;
                }
                try {
                    switch (facet.getLocalName()) {
                        case "length" -> {
                            facets.length = Integer.parseInt(value.trim());
                            present |= XSSimpleTypeDefinition.FACET_LENGTH;
                        }
                        case "minLength" -> {
                            facets.minLength = Integer.parseInt(value.trim());
                            present |= XSSimpleTypeDefinition.FACET_MINLENGTH;
                        }
                        case "maxLength" -> {
                            facets.maxLength = Integer.parseInt(value.trim());
                            present |= XSSimpleTypeDefinition.FACET_MAXLENGTH;
                        }
                        case "totalDigits" -> {
                            facets.totalDigits = Integer.parseInt(value.trim());
                            present |= XSSimpleTypeDefinition.FACET_TOTALDIGITS;
                        }
                        case "fractionDigits" -> {
                            facets.fractionDigits = Integer.parseInt(value.trim());
                            present |= XSSimpleTypeDefinition.FACET_FRACTIONDIGITS;
                        }
                        case "whiteSpace" -> {
                            facets.whiteSpace = switch (value.trim()) {
                                case "preserve" -> XSSimpleType.WS_PRESERVE;
                                case "replace" -> XSSimpleType.WS_REPLACE;
                                default -> XSSimpleType.WS_COLLAPSE;
                            };
                            present |= XSSimpleTypeDefinition.FACET_WHITESPACE;
                        }
                        case "explicitTimezone" -> {
                            facets.explicitTimezone = switch (value.trim()) {
                                case "required" -> XSSimpleType.ET_REQUIRED;
                                case "prohibited" -> XSSimpleType.ET_PROHIBITED;
                                default -> XSSimpleType.ET_OPTIONAL;
                            };
                            present |= XSSimpleTypeDefinition.FACET_EXPLICITTIMEZONE;
                        }
                        case "minInclusive" -> {
                            facets.minInclusive = value;
                            present |= XSSimpleTypeDefinition.FACET_MININCLUSIVE;
                        }
                        case "maxInclusive" -> {
                            facets.maxInclusive = value;
                            present |= XSSimpleTypeDefinition.FACET_MAXINCLUSIVE;
                        }
                        case "minExclusive" -> {
                            facets.minExclusive = value;
                            present |= XSSimpleTypeDefinition.FACET_MINEXCLUSIVE;
                        }
                        case "maxExclusive" -> {
                            facets.maxExclusive = value;
                            present |= XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE;
                        }
                        case "pattern" -> {
                            // The patterns of one step are alternatives (XSD 1.1 §4.3.4.3)
                            pattern = pattern == null ? new StringBuilder(value) : pattern.append('|').append(value);
                            present |= XSSimpleTypeDefinition.FACET_PATTERN;
                        }
                        case "enumeration" -> {
                            if (enumeration == null) {
                                enumeration = new Vector<>();
                            }
                            enumeration.add(value);
                            present |= XSSimpleTypeDefinition.FACET_ENUMERATION;
                        }
                        default -> {
                            // assertion and annotation: not checked here
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.debug("Ignoring facet {}='{}'", facet.getLocalName(), value);
                }
            }
            if (pattern != null) {
                facets.pattern = pattern.toString();
            }
            if (enumeration != null) {
                facets.enumeration = enumeration;
                Vector<Object> nsDecls = new Vector<>();
                for (int i = 0; i < enumeration.size(); i++) {
                    nsDecls.add(null);
                }
                facets.enumNSDecls = nsDecls;
            }
            XSSimpleType restricted = FACTORY.createTypeRestriction(name, tns, (short) 0, base, null);
            if (present == 0) {
                return restricted;
            }
            try {
                restricted.applyFacets(facets, present, 0, newContext());
                return restricted;
            } catch (Exception e) {
                // Facets the base does not allow or that contradict each other: check against the base only
                logger.debug("Facets of {} not applicable: {}", name, e.getMessage());
                return base;
            }
        }

        private static String targetNamespace(Node node) {
            Element root = node.getOwnerDocument() != null ? node.getOwnerDocument().getDocumentElement() : null;
            String tns = root != null ? root.getAttribute("targetNamespace") : null;
            return tns == null || tns.isEmpty() ? null : tns;
        }

        private static String attr(Element element, String name) {
            String value = element.getAttribute(name);
            return value == null || value.isEmpty() ? null : value;
        }

        private static Element child(Element parent, String localName) {
            List<Element> found = children(parent, localName);
            return found.isEmpty() ? null : found.getFirst();
        }

        /** The XSD child elements of {@code parent} with {@code localName} ({@code null} = all). */
        private static List<Element> children(Element parent, String localName) {
            List<Element> result = new ArrayList<>();
            for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && XSD_NS.equals(e.getNamespaceURI())
                        && (localName == null || localName.equals(e.getLocalName()))) {
                    result.add(e);
                }
            }
            return result;
        }
    }
}
