package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the test isolation of the app's persisted state: the Gradle test tasks
 * redirect {@code FreeXmlToolkit.properties} via the {@code fxt.properties.file}
 * system property and {@code user.home} (→ {@code ~/.freeXmlToolkit}: favorites,
 * usage statistics, saved queries, templates, caches) to paths under
 * {@code build/}. Without this, test runs rewrite the developer's real
 * configuration (observed: a flipped {@code toolbar.show.labels}, test
 * identities, JUnit temp paths, test favorites in the real favorites list).
 */
class PropertiesFileIsolationTest {

    @Test
    @DisplayName("tests run with user.home redirected below build/ (protects ~/.freeXmlToolkit)")
    void testsRunWithARedirectedUserHome() {
        String home = System.getProperty("user.home");
        assertNotNull(home);
        assertTrue(home.replace('\\', '/').contains("/build/"),
                "the Gradle test tasks must redirect user.home below the build directory "
                        + "(see build.gradle.kts withType<Test>), got: " + home);
    }

    @Test
    @DisplayName("tests write the isolated properties file under build/, never the repo root")
    void testsWriteTheIsolatedPropertiesFile() {
        String override = System.getProperty("fxt.properties.file");
        assertNotNull(override,
                "the Gradle test tasks must set -Dfxt.properties.file (see build.gradle.kts withType<Test>)");
        assertTrue(override.replace('\\', '/').contains("/build/"),
                "the override must point below the build directory, got: " + override);

        // A save must land in the isolated file (a fresh test JVM may not have written it yet).
        PropertiesService service = PropertiesServiceImpl.getInstance();
        service.setToolbarShowLabels(service.isToolbarShowLabels());
        assertTrue(new File(override).isFile(),
                "saving a setting must create the isolated properties file");
    }
}
