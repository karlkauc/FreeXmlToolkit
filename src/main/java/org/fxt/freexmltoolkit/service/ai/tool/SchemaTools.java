package org.fxt.freexmltoolkit.service.ai.tool;

import java.nio.file.Files;
import java.nio.file.Path;

/** Argument handling shared by the schema tools. */
final class SchemaTools {

    private SchemaTools() {
    }

    /** The {@code xsd_path} argument as an existing file inside the roots. */
    static Path schemaFile(ToolArgs args, ToolContext context) throws ToolException {
        String path = args.requiredString("xsd_path");
        Path file = context.files().resolve(path);
        if (!Files.isRegularFile(file)) {
            throw new ToolException("'xsd_path': file not found: " + path);
        }
        if (file.toFile().length() > context.limits().maxInputBytes()) {
            throw new ToolException("'xsd_path': the schema is larger than " + context.limits().maxInputBytes() + " bytes");
        }
        return file;
    }

    static ToolResult notParsed(SchemaCache.Loaded loaded) {
        com.google.gson.JsonObject structured = new com.google.gson.JsonObject();
        structured.add("error", ToolJson.error(null, "input", -1, loaded.error()));
        return ToolResult.failed("The schema could not be read: " + loaded.error(), structured);
    }
}
