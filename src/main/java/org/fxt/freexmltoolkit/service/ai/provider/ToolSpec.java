package org.fxt.freexmltoolkit.service.ai.provider;

import org.fxt.freexmltoolkit.service.ai.tool.AiTool;

import com.google.gson.JsonObject;

/** A tool offered to the model: name, description, JSON Schema of the arguments. */
public record ToolSpec(String name, String description, JsonObject inputSchema) {

    public static ToolSpec of(AiTool tool) {
        return new ToolSpec(tool.name(), tool.description(), tool.inputSchema());
    }
}
