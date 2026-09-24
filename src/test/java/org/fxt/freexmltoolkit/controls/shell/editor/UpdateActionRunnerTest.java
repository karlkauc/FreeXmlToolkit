package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.domain.UpdateInfo;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link UpdateActionRunner#describe} (no UI / no network): turns an
 * {@link UpdateInfo} into a user-facing message.
 */
class UpdateActionRunnerTest {

    @Test
    void describesAnAvailableUpdate() {
        UpdateInfo info = new UpdateInfo("1.2.0", "1.3.0", "Release 1.3.0", "notes",
                "https://example/dl", "2026-05-31", true);
        String msg = UpdateActionRunner.describe(info);
        assertTrue(msg.toLowerCase().contains("available"), msg);
        assertTrue(msg.contains("1.3.0"), msg);
    }

    @Test
    void describesUpToDate() {
        UpdateInfo info = UpdateInfo.noUpdateAvailable("1.3.0");
        String msg = UpdateActionRunner.describe(info);
        assertTrue(msg.toLowerCase().contains("up to date") || msg.toLowerCase().contains("latest"), msg);
        assertTrue(msg.contains("1.3.0"), msg);
    }

    @Test
    void classifiesUsageResult() {
        UpdateInfo available = new UpdateInfo("1.2.0", "1.3.0", "Release 1.3.0", "notes",
                "https://example/dl", "2026-05-31", true);
        assertEquals("update_available", UpdateActionRunner.usageResult(available, null));
        assertEquals("up_to_date", UpdateActionRunner.usageResult(UpdateInfo.noUpdateAvailable("1.3.0"), null));
        assertEquals("error", UpdateActionRunner.usageResult(UpdateInfo.errorDuringCheck("1.3.0"), null));
        assertEquals("error", UpdateActionRunner.usageResult(null, null));
        assertEquals("error", UpdateActionRunner.usageResult(available, new RuntimeException("offline")));
    }
}
