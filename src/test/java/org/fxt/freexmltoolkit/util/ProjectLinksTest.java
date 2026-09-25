package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Sanity checks for the central project link constants. */
class ProjectLinksTest {

    @Test
    void allLinksAreAbsoluteHttpsUrls() {
        for (String url : List.of(ProjectLinks.GITHUB_URL, ProjectLinks.DOCS_URL,
                ProjectLinks.ISSUES_URL, ProjectLinks.SPONSORS_URL)) {
            URI uri = URI.create(url);
            assertEquals("https", uri.getScheme(), url);
            assertNotNull(uri.getHost(), url);
        }
    }

    @Test
    void sponsorsLinkPointsToGitHubSponsors() {
        assertEquals("https://github.com/sponsors/karlkauc", ProjectLinks.SPONSORS_URL);
    }
}
