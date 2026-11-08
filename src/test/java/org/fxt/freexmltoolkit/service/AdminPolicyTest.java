package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link AdminPolicy}: where the files are, locked keys, defaults, updates, system properties. */
@DisplayName("AdminPolicy")
class AdminPolicyTest {

    @TempDir
    Path dir;

    @AfterEach
    void cleanUp() {
        AdminPolicy.setCurrentForTests(null);
        System.clearProperty("fxt.test.policyLocked");
        System.clearProperty("fxt.test.policyDefault");
        System.clearProperty("fxt.test.policyGiven");
    }

    private AdminPolicy loadFrom(String policy, String defaults) throws Exception {
        if (policy != null) {
            Files.writeString(dir.resolve(AdminPolicy.POLICY_FILE_NAME), policy, StandardCharsets.UTF_8);
        }
        if (defaults != null) {
            Files.writeString(dir.resolve(AdminPolicy.DEFAULTS_FILE_NAME), defaults, StandardCharsets.UTF_8);
        }
        Properties system = new Properties();
        system.setProperty(AdminPolicy.PROP_FILE, dir.resolve(AdminPolicy.POLICY_FILE_NAME).toString());
        return AdminPolicy.load(Map.of(), system, "Linux");
    }

    @Test
    @DisplayName("the platform folders: ProgramData, /Library/Application Support, /etc")
    void folders() {
        assertEquals(Path.of("D:\\Data", "FreeXmlToolkit"),
                AdminPolicy.defaultFolder(Map.of("ProgramData", "D:\\Data"), "Windows 11"));
        assertEquals(Path.of("/Library/Application Support/FreeXmlToolkit"),
                AdminPolicy.defaultFolder(Map.of(), "Mac OS X"));
        assertEquals(Path.of("/etc/freexmltoolkit"), AdminPolicy.defaultFolder(Map.of(), "Linux"));
    }

    @Test
    @DisplayName("-Dfxt.policy.file wins over FXT_POLICY_FILE, which wins over the platform folder")
    void explicitFile() {
        Properties system = new Properties();
        assertEquals(Path.of("/etc/freexmltoolkit/policy.properties"),
                AdminPolicy.policyFile(Map.of(), system, "Linux"));
        assertEquals(Path.of("/srv/env.properties"),
                AdminPolicy.policyFile(Map.of(AdminPolicy.ENV_FILE, "/srv/env.properties"), system, "Linux"));
        system.setProperty(AdminPolicy.PROP_FILE, "/srv/prop.properties");
        assertEquals(Path.of("/srv/prop.properties"),
                AdminPolicy.policyFile(Map.of(AdminPolicy.ENV_FILE, "/srv/env.properties"), system, "Linux"));
    }

    @Test
    @DisplayName("no files: nothing is locked, updates are allowed")
    void none() throws Exception {
        AdminPolicy policy = loadFrom(null, null);
        assertFalse(policy.isActive());
        assertFalse(policy.isLocked("update.check.enabled"));
        assertTrue(policy.updatesAllowed());
    }

    @Test
    @DisplayName("locked keys override the user's value and cannot be set; defaults fill missing keys")
    void overlay() throws Exception {
        AdminPolicy policy = loadFrom("ssl.trustAllCerts = false \nhttp.proxy.host=proxy.corp\n",
                "xml.indent.spaces=2\nhttp.proxy.host=ignored.default\n");
        assertTrue(policy.isActive());
        Properties settings = policy.overlay();
        settings.setProperty("ssl.trustAllCerts", "true");          // the user tries to switch it on
        settings.put("http.proxy.host", "user.proxy");              // a stored user value
        assertEquals("false", settings.getProperty("ssl.trustAllCerts"), "locked value, trimmed");
        assertEquals("proxy.corp", settings.getProperty("http.proxy.host"));
        assertEquals("2", settings.getProperty("xml.indent.spaces"), "administrator default");
        assertEquals("x", settings.getProperty("missing", "x"));

        settings.setProperty("xml.indent.spaces", "8");
        assertEquals("8", settings.getProperty("xml.indent.spaces"), "the user's own value beats the default");
    }

    @Test
    @DisplayName("loadProperties/saveProperties round trip: locked values and untouched defaults stay out of the user's file")
    void savingKeepsUserValues() throws Exception {
        AdminPolicy policy = loadFrom("update.check.enabled=false\nhttp.proxy.host=proxy.corp\n",
                "xml.indent.spaces=2\njson.indent.spaces=4\n");
        Properties user = policy.overlay();
        user.put("update.check.enabled", "true");     // the user's file says true
        user.put("json.indent.spaces", "4");          // the user set the default value explicitly

        // like PropertiesServiceImpl.loadProperties(): a plain effective copy, which survives putAll
        Properties effective = policy.effective(user);
        Properties copied = new Properties();
        copied.putAll(effective);
        assertEquals("false", copied.getProperty("update.check.enabled"));
        assertEquals("proxy.corp", copied.getProperty("http.proxy.host"));
        assertEquals("2", copied.getProperty("xml.indent.spaces"));

        // a caller edits the copy and saves it
        copied.setProperty("update.check.enabled", "false");
        copied.setProperty("user.name", "Ada");
        Properties saved = policy.userValues(copied, user);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        saved.store(out, null);
        String file = out.toString(StandardCharsets.ISO_8859_1);
        assertTrue(file.contains("update.check.enabled=true"), "the user's own value stays: " + file);
        assertFalse(file.contains("http.proxy.host"), "a locked value is never written: " + file);
        assertFalse(file.contains("xml.indent.spaces"), "an untouched default is not written: " + file);
        assertTrue(file.contains("json.indent.spaces=4"), "a value the user had stays: " + file);
        assertTrue(file.contains("user.name=Ada"), file);
        assertEquals("false", saved.getProperty("update.check.enabled"), "and still reads as locked");
    }

    @Test
    @DisplayName("update.allowed=false switches the update check off and locks it")
    void updatesNotAllowed() throws Exception {
        AdminPolicy policy = loadFrom("update.allowed=false\n", null);
        assertFalse(policy.updatesAllowed());
        assertTrue(policy.isLocked("update.check.enabled"));
        assertEquals("false", policy.overlay().getProperty("update.check.enabled"));
    }

    @Test
    @DisplayName("fxt.* keys become system properties: locked always, defaults only when not given")
    void systemProperties() throws Exception {
        System.setProperty("fxt.test.policyGiven", "command-line");
        AdminPolicy policy = loadFrom("fxt.test.policyLocked=locked\n",
                "fxt.test.policyDefault=default\nfxt.test.policyGiven=default\n");
        policy.applySystemProperties();
        assertEquals("locked", System.getProperty("fxt.test.policyLocked"));
        assertEquals("default", System.getProperty("fxt.test.policyDefault"));
        assertEquals("command-line", System.getProperty("fxt.test.policyGiven"));
    }

    @Test
    @DisplayName("an update check is refused without a request when updates are not allowed")
    void updateServiceRefuses() throws Exception {
        AdminPolicy.setCurrentForTests(loadFrom("update.allowed=false\n", null));
        UpdateCheckService service = UpdateCheckServiceImpl.getInstance();
        assertFalse(service.isUpdateCheckEnabled());
        var info = service.checkForUpdates().get();
        assertEquals(UpdateCheckServiceImpl.FAILURE_DISABLED_BY_POLICY, info.failureReason());
        assertFalse(info.updateAvailable());
        var result = AutoUpdateServiceImpl.getInstance().downloadAndApplyUpdate(info, p -> { }).get();
        assertFalse(result.success());
    }

    @Test
    @DisplayName("unknown keys have no locked value or default")
    void lockedValueOfUnknownKey() throws Exception {
        AdminPolicy policy = loadFrom("a=b\n", null);
        assertNull(policy.lockedValue("c"));
        assertNull(policy.defaultValue(null));
    }
}
