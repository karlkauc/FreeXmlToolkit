package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the test isolation of the app's properties file: the Gradle test tasks
 * redirect {@code FreeXmlToolkit.properties} via the {@code fxt.properties.file}
 * system property to a path under {@code build/}. Without this, test runs rewrite
 * the developer's real configuration in the repo root (observed: a flipped
 * {@code toolbar.show.labels}, test identities, JUnit temp paths).
 */
class PropertiesFileIsolationTest {

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
