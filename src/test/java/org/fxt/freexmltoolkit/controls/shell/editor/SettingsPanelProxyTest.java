package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.ProxySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Settings → HTTP Proxy: every proxy setting is shown, saved and read back. */
@ExtendWith(ApplicationExtension.class)
class SettingsPanelProxyTest {

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        ServiceRegistry.initialize();
    }

    private SettingsPanel showPanel() {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            SettingsPanel panel = new SettingsPanel();
            stage.setScene(new Scene(panel, 900, 700));
            stage.show();
            panel.applyCss();
            panel.layout();
            return panel;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T lookup(SettingsPanel panel, String id) {
        return (T) panel.lookup("#" + id);
    }

    @Test
    void allProxySettingsRoundTrip() {
        SettingsPanel panel = showPanel();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            ComboBox<String> mode = lookup(panel, "settings-proxy-mode");
            mode.setValue(SettingsPanel.PROXY_MODE_LABELS.get(ProxySettings.Mode.MANUAL));
            TextField host = lookup(panel, "settings-proxy-host");
            assertFalse(host.isDisabled(), "host is editable for a manual proxy");
            host.setText("proxy.corp");
            ((TextField) lookup(panel, "settings-proxy-port")).setText("3128");
            ((TextField) lookup(panel, "settings-proxy-bypass")).setText("*.corp, 10.*");
            ((TextField) lookup(panel, "settings-proxy-user")).setText("jdoe");
            ((TextField) lookup(panel, "settings-proxy-password")).setText("s3cret");
            ((TextField) lookup(panel, "settings-proxy-domain")).setText("CORP");
            panel.saveSettings();
            return null;
        });

        ProxySettings saved = ProxySettings.from(ServiceRegistry.get(PropertiesService.class).loadProperties());
        assertEquals(ProxySettings.Mode.MANUAL, saved.mode());
        assertEquals("proxy.corp", saved.host());
        assertEquals(3128, saved.port());
        assertEquals(List.of("*.corp", "10.*"), saved.nonProxyHosts());
        assertEquals("CORP\\jdoe", saved.authenticationUser());
        assertEquals("s3cret", saved.password());

        SettingsPanel reopened = showPanel();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            assertEquals(saved, reopened.proxySettingsFromForm(), "the form shows what was saved");
            ComboBox<String> mode = lookup(reopened, "settings-proxy-mode");
            mode.setValue(SettingsPanel.PROXY_MODE_LABELS.get(ProxySettings.Mode.SYSTEM));
            assertTrue(((TextField) lookup(reopened, "settings-proxy-host")).isDisabled(),
                    "host belongs to the manual proxy");
            reopened.saveSettings();
            return null;
        });
    }

    @Test
    void testResultsReadAsAVerdict() {
        assertEquals("Connected: HTTP 200 in 120 ms", SettingsPanel.describeTest(
                new ConnectionResult(URI.create("https://github.com"), 200, 120L, new String[0], "")));
        assertTrue(SettingsPanel.describeTest(
                new ConnectionResult(URI.create("https://github.com"), 407, 5L, new String[0], ""))
                .contains("authentication"));
        assertEquals("Failed: Connection refused", SettingsPanel.describeTest(
                new ConnectionResult(URI.create("https://github.com"), 0, 5L, new String[0], "Connection refused")));
    }
}
