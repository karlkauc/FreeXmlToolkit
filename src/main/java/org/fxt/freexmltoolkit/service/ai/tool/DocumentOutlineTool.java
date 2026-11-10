package org.fxt.freexmltoolkit.service.ai.tool;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.fxt.freexmltoolkit.service.ai.context.XmlOutline;

import com.google.gson.JsonObject;

/** {@code document_outline}: the shape of an XML document in one streaming pass ({@link XmlOutline}). */
public final class DocumentOutlineTool implements AiTool {

    private static final Set<String> ARGUMENTS = Set.of("xml_path", "xml_content", "max_paths", "samples");

    @Override
    public String name() {
        return "document_outline";
    }

    @Override
    public String description() {
        return "Outline an XML document without reading it whole: root element, namespace declarations, declared "
                + "schema, size, element count, depth, and every distinct element path (/a/b, no positions) with how "
                + "often it occurs, its attribute names and one sample value. Works on files of any size (streamed). "
                + "Use it first on an unknown or large document, then xpath for details.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .input("xml", "XML document")
                .integer("max_paths", "Distinct paths returned at most.", 200, 1, 5000)
                .bool("samples", "Include one sample value per path.", true)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        int maxPaths = args.optionalInt("max_paths", 200, 1, 5000);
        boolean samples = args.optionalBoolean("samples", true);
        boolean hasPath = args.has(ToolInput.pathArgument("xml"));
        boolean hasContent = args.has(ToolInput.contentArgument("xml"));
        if (hasPath == hasContent) {
            throw new ToolException("give either 'xml_path' or 'xml_content'" + (hasPath ? ", not both" : ""));
        }
        XmlOutline.Outline outline;
        try {
            if (hasPath) {
                // streamed from disk: no size limit, unlike the other tools
                Path file = context.files().resolve(args.requiredString("xml_path"));
                if (!Files.isRegularFile(file)) {
                    throw new ToolException("'xml_path': file not found: " + args.requiredString("xml_path"));
                }
                outline = XmlOutline.of(file, maxPaths, samples);
            } else {
                String content = ToolInput.required(args, "xml", context).text();
                outline = XmlOutline.of(new StringReader(content), content.getBytes(StandardCharsets.UTF_8).length,
                        maxPaths, samples);
            }
        } catch (XMLStreamException e) {
            int line = e.getLocation() == null ? -1 : e.getLocation().getLineNumber();
            JsonObject structured = new JsonObject();
            structured.add("error", ToolJson.error(null, "input", line, e.getMessage()));
            return ToolResult.failed(ToolJson.describe(null, "input", line, e.getMessage()), structured);
        } catch (java.io.IOException e) {
            throw new ToolException("cannot read the document: " + e.getMessage(), e);
        }
        return ToolResult.ok(context.limits().truncate(outline.toText()), outline.toJson());
    }
}
