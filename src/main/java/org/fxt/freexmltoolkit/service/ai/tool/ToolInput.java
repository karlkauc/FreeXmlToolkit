package org.fxt.freexmltoolkit.service.ai.tool;

import java.nio.file.Path;
import java.util.Optional;

/**
 * An input given as the argument pair {@code <name>_path} / {@code <name>_content}: exactly one of them.
 * Inline content is what lets an agent check what it has just written without writing a file.
 *
 * @param text the input's text
 * @param file the file it was read from (real path inside a root), empty for inline content
 */
public record ToolInput(String text, Optional<Path> file) {

    /** The argument names of the pair {@code name}: {@code name_path}, {@code name_content}. */
    public static String pathArgument(String name) {
        return name + "_path";
    }

    public static String contentArgument(String name) {
        return name + "_content";
    }

    /**
     * @throws ToolException when both or neither argument is given, or the file cannot be read
     */
    public static ToolInput required(ToolArgs args, String name, ToolContext context) throws ToolException {
        return optional(args, name, context).orElseThrow(() -> new ToolException(
                "give either '" + pathArgument(name) + "' or '" + contentArgument(name) + "'"));
    }

    /** As {@link #required}, but neither argument means "no input". */
    public static Optional<ToolInput> optional(ToolArgs args, String name, ToolContext context)
            throws ToolException {
        String pathArg = pathArgument(name);
        String contentArg = contentArgument(name);
        boolean hasPath = args.has(pathArg);
        boolean hasContent = args.has(contentArg);
        if (hasPath && hasContent) {
            throw new ToolException("give either '" + pathArg + "' or '" + contentArg + "', not both");
        }
        if (hasContent) {
            String content = args.requiredString(contentArg);
            long bytes = content.length() * 3L; // upper bound of the UTF-8 size without encoding it
            if (bytes > context.limits().maxInputBytes()
                    && content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    > context.limits().maxInputBytes()) {
                throw new ToolException("'" + contentArg + "' is larger than " + context.limits().maxInputBytes()
                        + " bytes");
            }
            return Optional.of(new ToolInput(content, Optional.empty()));
        }
        if (hasPath) {
            Path file = context.files().resolve(args.requiredString(pathArg));
            return Optional.of(new ToolInput(context.files().read(file, context.limits().maxInputBytes()),
                    Optional.of(file)));
        }
        return Optional.empty();
    }
}
