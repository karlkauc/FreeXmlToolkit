package org.fxt.freexmltoolkit.service.yaml;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

/**
 * Finds the JSON Schema a YAML document declares, the two ways editors understand:
 * <ol>
 *   <li>a modeline comment {@code # yaml-language-server: $schema=<url or path>} (as written by the
 *       YAML language server, used by VS Code and others), anywhere in the text;</li>
 *   <li>a top-level {@code $schema} key ({@code $schema: ./schema.json}), like JSON's {@code "$schema"}.</li>
 * </ol>
 * The modeline wins. UI-free; the reference is returned as written (resolving it is the JSON
 * Schema binding's job).
 */
public final class YamlSchemaDeclaration {

    private static final Pattern MODELINE = Pattern.compile(
            "(?m)^[ \\t]*#[ \\t]*yaml-language-server:[ \\t]*\\$schema=(\\S+)");
    private static final Pattern SCHEMA_KEY = Pattern.compile(
            "(?m)^(?:\\$schema|\"\\$schema\"|'\\$schema')[ \\t]*:[ \\t]+(\\S.*?)[ \\t]*(?:[ \\t]#.*)?$");

    private YamlSchemaDeclaration() {
    }

    /**
     * @param text the YAML text
     * @return the declared schema reference, empty when none is declared
     */
    public static Optional<String> find(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        Matcher modeline = MODELINE.matcher(text);
        if (modeline.find()) {
            return Optional.of(modeline.group(1));
        }
        Matcher key = SCHEMA_KEY.matcher(text);
        if (key.find()) {
            String value = key.group(1).strip();
            if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                    && value.charAt(value.length() - 1) == value.charAt(0)) {
                value = value.substring(1, value.length() - 1);
            }
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        }
        return Optional.empty();
    }

    /**
     * The declaration as the JSON text the JSON Schema binding reads: {@code {"$schema": "…"}}, or
     * {@code {}} when the YAML declares nothing. Lets YAML documents share JSON's binding (project,
     * Schema Library / catalog, declared location).
     *
     * @param text the YAML text
     * @return a small JSON document
     */
    public static String asJsonStub(String text) {
        JsonObject stub = new JsonObject();
        find(text).ifPresent(reference -> stub.addProperty("$schema", reference));
        return stub.toString();
    }
}
