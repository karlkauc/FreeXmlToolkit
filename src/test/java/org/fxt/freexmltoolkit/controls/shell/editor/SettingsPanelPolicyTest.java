package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Properties;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.AdminPolicyTestAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Settings the administrator locked ({@link AdminPolicy}) are disabled on the Settings page, also
 * those whose enabled state otherwise follows another control, and a notice names the policy file.
 */
@ExtendWith(ApplicationExtension.class)
class SettingsPanelPolicyTest {

    private SettingsPanel settings;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        Properties locked = new Properties();
        locked.setProperty("ssl.trustAllCerts", "false");
        locked.setProperty("http.proxy.host", "proxy.corp");   // its disabled state is normally bound
        locked.setProperty(AdminPolicy.KEY_UPDATES_ALLOWED, "false");
        AdminPolicyTestAccess.setCurrent(Path.of("/etc/freexmltoolkit/policy.properties"), locked, new Properties());
        settings = new SettingsPanel();
        stage.setScene(new Scene(settings, 900, 600));
        stage.show();
    }

    @AfterEach
    void resetPolicy() {
        AdminPolicyTestAccess.reset();
    }

    @Test
    void lockedSettingsAreDisabledAndExplained() {
        WaitForAsyncUtils.waitForFxEvents();
        var proxyHost = settings.lookup("#settings-proxy-host");
        var indent = settings.policyKeys().keySet().stream()
                .filter(n -> n instanceof javafx.scene.control.Spinner<?>).findFirst().orElseThrow();
        assertTrue(settings.isPolicyLocked(proxyHost), "proxy host is locked");
        assertTrue(proxyHost.isDisabled(), "a locked field stays disabled whatever the proxy mode");
        assertTrue(settings.policyKeys().keySet().stream()
                .filter(settings::isPolicyLocked).allMatch(javafx.scene.Node::isDisabled));
        assertTrue(settings.policyKeys().keySet().stream().filter(settings::isPolicyLocked).count() >= 3,
                "trust-all, proxy host and the update check (update.allowed=false) are locked");
        assertFalse(settings.isPolicyLocked(indent), "settings the policy does not name stay editable");

        Label notice = (Label) settings.lookup("#settings-policy-notice");
        assertTrue(notice.isVisible());
        assertTrue(notice.getText().contains("managed by your administrator"), notice.getText());
        assertTrue(notice.getText().contains("policy.properties"), notice.getText());
    }

    @Test
    void resetToDefaultsKeepsLockedValues() {
        WaitForAsyncUtils.waitForFxEvents();
        var proxyHost = (javafx.scene.control.TextField) settings.lookup("#settings-proxy-host");
        String before = WaitForAsyncUtils.waitForAsyncFx(2000, () -> proxyHost.getText());
        WaitForAsyncUtils.asyncFx(settings::resetToDefaults);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(before.equals(proxyHost.getText()), "a locked field keeps its value on reset");
    }
}
