package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Adds, edits or removes a namespace declaration ({@code xmlns:prefix="uri"}) on the schema.
 * <ul>
 *   <li>add: {@code oldPrefix == null}</li>
 *   <li>remove: {@code newPrefix == null}</li>
 *   <li>edit (rename prefix and/or change URI): both set</li>
 * </ul>
 * The empty prefix stands for the default namespace. The prefix bound to the XSD namespace
 * cannot be removed or renamed (the editor writes its tags with it), and a prefix that is
 * still used in the schema cannot be removed or renamed.
 *
 * @since 2.6
 */
public class ChangeNamespaceDeclarationCommand implements XsdCommand {

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    private static final Pattern ATTRIBUTE_VALUE = Pattern.compile("=\"([^\"]*)\"");

    private final XsdSchema schema;
    private final String oldPrefix;
    private final String newPrefix;
    private final String uri;
    private final Map<String, String> before;

    public ChangeNamespaceDeclarationCommand(XsdSchema schema, String oldPrefix, String newPrefix, String uri) {
        if (schema == null || (oldPrefix == null && newPrefix == null)) {
            throw new IllegalArgumentException("Schema and a prefix are required");
        }
        this.schema = schema;
        this.oldPrefix = oldPrefix;
        this.newPrefix = newPrefix == null ? null : newPrefix.trim();
        this.uri = uri == null ? null : uri.trim();
        this.before = schema.getNamespaces();
    }

    /** @return the rule check of this change */
    public EditCheck check() {
        if (oldPrefix != null && !before.containsKey(oldPrefix)) {
            return EditCheck.blocked("The prefix '" + oldPrefix + "' is not declared.");
        }
        boolean renames = oldPrefix != null && !oldPrefix.equals(newPrefix);
        if (oldPrefix != null && XSD_NS.equals(before.get(oldPrefix))
                && (renames || !XSD_NS.equals(uri)) && XsdEditRules.xsdPrefix(schema).equals(oldPrefix)) {
            return EditCheck.blocked("The prefix '" + oldPrefix + "' is bound to the XML Schema namespace "
                    + "and used for all schema tags; it cannot be changed.");
        }
        if (newPrefix != null) {
            if (!newPrefix.isEmpty() && !XsdEditRules.isNcName(newPrefix)) {
                return EditCheck.blocked("'" + newPrefix + "' is not a valid prefix.");
            }
            if (newPrefix.equals("xml") || newPrefix.startsWith("xmlns")) {
                return EditCheck.blocked("The prefix '" + newPrefix + "' is reserved.");
            }
            if (!newPrefix.equals(oldPrefix) && before.containsKey(newPrefix)) {
                return EditCheck.blocked("The prefix '" + (newPrefix.isEmpty() ? "(default)" : newPrefix)
                        + "' is already declared.");
            }
            if (uri == null || uri.isEmpty()) {
                return EditCheck.blocked("Enter the namespace URI.");
            }
        }
        if (renames && !oldPrefix.isEmpty() && isPrefixUsed(schema, oldPrefix)) {
            return EditCheck.blocked("The prefix '" + oldPrefix + "' is still used in the schema. "
                    + "Change those references first.");
        }
        return EditCheck.allowed();
    }

    /** @return true when a QName or XPath in an attribute value of the schema uses {@code prefix} */
    public static boolean isPrefixUsed(XsdSchema schema, String prefix) {
        String text = new XsdSerializer().serialize(schema,
                org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSortOrder.PRESERVE);
        Pattern use = Pattern.compile("(^|[\\s/(\\[@,|=*!<>+-])" + Pattern.quote(prefix) + ":[\\p{L}_*]");
        Matcher values = ATTRIBUTE_VALUE.matcher(text);
        while (values.find()) {
            int start = values.start();
            // skip the namespace declarations themselves
            int nameStart = text.lastIndexOf(' ', start) + 1;
            if (text.startsWith("xmlns", nameStart)) {
                continue;
            }
            if (use.matcher(values.group(1)).find()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean execute() {
        if (!check().isAllowed()) {
            return false;
        }
        Map<String, String> after = new LinkedHashMap<>();
        boolean placed = false;
        for (Map.Entry<String, String> entry : before.entrySet()) {
            if (Objects.equals(entry.getKey(), oldPrefix)) {
                if (newPrefix != null) {
                    after.put(newPrefix, uri);
                    placed = true;
                }
            } else {
                after.put(entry.getKey(), entry.getValue());
            }
        }
        if (!placed && newPrefix != null && oldPrefix == null) {
            after.put(newPrefix, uri);
        }
        schema.replaceNamespaces(after);
        return true;
    }

    @Override
    public boolean undo() {
        schema.replaceNamespaces(before);
        return true;
    }

    @Override
    public String getDescription() {
        if (oldPrefix == null) {
            return "Add namespace prefix '" + newPrefix + "'";
        }
        return newPrefix == null ? "Remove namespace prefix '" + oldPrefix + "'"
                : "Edit namespace prefix '" + oldPrefix + "'";
    }
}
