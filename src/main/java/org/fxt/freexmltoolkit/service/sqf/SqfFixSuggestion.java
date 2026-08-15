package org.fxt.freexmltoolkit.service.sqf;

import java.io.File;

/**
 * A quick fix offered for one validation finding — the small, immutable, UI-facing
 * descriptor attached to a {@code ValidationProblem}. The heavyweight fix definition
 * stays in the {@link SqfModel.SqfCatalog}; {@code fixKey} resolves it there.
 *
 * @param fixKey         unique catalog key of the fix definition (stable within one catalog)
 * @param fixId          the author-visible fix id ({@code sqf:fix/@id})
 * @param title          the fix title ({@code sqf:title}), never {@code null}
 * @param description    the first descriptive paragraph ({@code sqf:p}), or {@code ""}
 * @param schematronFile the Schematron file the fix was defined in
 * @param svrlLocation   the SVRL location XPath of the finding this fix anchors to
 * @param needsUserInput {@code true} when the fix declares {@code sqf:user-entry} prompts
 */
public record SqfFixSuggestion(
        String fixKey,
        String fixId,
        String title,
        String description,
        File schematronFile,
        String svrlLocation,
        boolean needsUserInput) {
}
