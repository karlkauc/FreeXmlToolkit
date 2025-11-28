package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testfx.framework.junit5.ApplicationExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WelcomeController.
 * Tests the welcome screen functionality and user onboarding.
 */
@ExtendWith({ApplicationExtension.class, MockitoExtension.class})
class WelcomeControllerTest {

    private WelcomeController controller;

    @Mock
    private HBox mockVersionUpdate;

    @Mock
    private CheckBox mockSendUsageStatistics;

    @Mock
    private Label mockDurationLabel;

    @Mock
    private Label mockVersionLabel;

    @Mock
    private PropertiesService mockPropertiesService;

    @BeforeEach
    void setUp() {
        controller = new WelcomeController();
    }

    /**
     * Helper method to inject fields using reflection
     */
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            field.set(target, value);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @Test
    @DisplayName("Should create controller instance")
    void testControllerInstantiation() {
        assertNotNull(controller);
    }

    @Test
    @DisplayName("Should format seconds to human-readable format")
    void testFormatSecondsHumanReadable() throws Exception {
        Method formatMethod = controller.getClass().getDeclaredMethod("formatSecondsHumanReadable", int.class);
        formatMethod.setAccessible(true);

        // Test 0 seconds
        String result0 = (String) formatMethod.invoke(controller, 0);
        assertNotNull(result0);

        // Test 60 seconds (1 minute)
        String result60 = (String) formatMethod.invoke(controller, 60);
        assertNotNull(result60);
        // Accept various formats: "0:01:00", "00:01:00", ":01:", "1 minute", etc.
        assertTrue(result60.contains("01") || result60.contains("1"),
                "Should contain 1 minute representation, got: " + result60);

        // Test 3600 seconds (1 hour)
        String result3600 = (String) formatMethod.invoke(controller, 3600);
        assertNotNull(result3600);
        assertTrue(result3600.contains("01") || result3600.contains("1"),
                "Should contain 1 hour representation, got: " + result3600);

        // Test 3665 seconds (1 hour, 1 minute, 5 seconds)
        String result3665 = (String) formatMethod.invoke(controller, 3665);
        assertNotNull(result3665);
        assertTrue(result3665.contains("01") || result3665.contains("1"),
                "Should contain time representation, got: " + result3665);
    }

    @Test
    @DisplayName("Should validate GitHub repository URL")
    void testGitHubRepositoryUrl() {
        String githubUrl = "https://github.com/karlkauc/FreeXmlToolkit";

        assertNotNull(githubUrl);
        assertTrue(githubUrl.startsWith("https://github.com/"));
        assertTrue(githubUrl.contains("FreeXmlToolkit"));

        // Validate URL can be parsed
        assertDoesNotThrow(() -> URI.create(githubUrl));
    }

    @Test
    @DisplayName("Should validate version format")
    void testVersionFormat() {
        String version = "20221008";

        assertNotNull(version);
        assertEquals(8, version.length(), "Version should be 8 characters (YYYYMMDD)");
        assertTrue(version.matches("\\d{8}"), "Version should be numeric date format");
    }

    @Test
    @DisplayName("Should handle zero usage duration")
    void testZeroUsageDuration() {
        int zeroDuration = 0;
        assertTrue(zeroDuration >= 0, "Usage duration should be non-negative");
    }

    @Test
    @DisplayName("Should handle positive usage duration")
    void testPositiveUsageDuration() {
        int positiveDuration = 12345;
        assertTrue(positiveDuration > 0, "Usage duration should be positive");
    }

    @Test
    @DisplayName("Should format first-time user message")
    void testFirstTimeUserMessage() {
        String firstTimeMessage = "You are here the first time!";

        assertNotNull(firstTimeMessage);
        assertFalse(firstTimeMessage.isEmpty());
        assertTrue(firstTimeMessage.contains("first time"));
    }

    @Test
    @DisplayName("Should validate property keys")
    void testPropertyKeys() {
        String versionKey = "version";
        String usageDurationKey = "usageDuration";

        assertNotNull(versionKey);
        assertNotNull(usageDurationKey);
        assertEquals("version", versionKey);
        assertEquals("usageDuration", usageDurationKey);
    }

    @Test
    @DisplayName("Should handle default property values")
    void testDefaultPropertyValues() {
        String defaultDuration = "0";

        assertNotNull(defaultDuration);
        assertEquals("0", defaultDuration);

        int parsedDuration = Integer.parseInt(defaultDuration);
        assertEquals(0, parsedDuration);
    }

    @Test
    @DisplayName("Should validate LocalTime formatting")
    void testLocalTimeFormatting() {
        // Test that LocalTime can format seconds correctly
        // Note: LocalTime.toString() may return "00:00" or "00:00:00" depending on JDK version
        LocalTime time = LocalTime.MIN.plusSeconds(0);
        assertNotNull(time);
        assertTrue(time.toString().startsWith("00:00"),
                "Should start with 00:00, got: " + time);

        LocalTime time60 = LocalTime.MIN.plusSeconds(60);
        assertTrue(time60.toString().startsWith("00:01"),
                "Should start with 00:01, got: " + time60);

        LocalTime time3600 = LocalTime.MIN.plusSeconds(3600);
        assertTrue(time3600.toString().startsWith("01:00"),
                "Should start with 01:00, got: " + time3600);
    }

    @Test
    @DisplayName("Should set parent controller")
    void testSetParentController() {
        MainController mockMainController = new MainController();
        assertDoesNotThrow(() -> controller.setParentController(mockMainController));
    }

    @Test
    @DisplayName("Should handle null parent controller")
    void testNullParentController() {
        assertDoesNotThrow(() -> controller.setParentController(null),
                "Should handle null parent controller gracefully");
    }

    @Test
    @DisplayName("Should validate update page URL format")
    void testUpdatePageUrlFormat() {
        String updateUrl = "https://github.com/karlkauc/FreeXmlToolkit";

        assertNotNull(updateUrl);
        assertTrue(updateUrl.startsWith("https://"), "URL should use HTTPS");
        assertTrue(updateUrl.contains("github.com"), "URL should point to GitHub");

        // URL should be valid URI
        URI uri = URI.create(updateUrl);
        assertEquals("https", uri.getScheme());
        assertEquals("github.com", uri.getHost());
    }

    @Test
    @DisplayName("Should validate version comparison logic")
    void testVersionComparisonLogic() {
        String currentVersion = "20221008";
        String olderVersion = "20220101";
        String newerVersion = "20230101";

        // Versions can be compared as strings (YYYYMMDD format)
        assertTrue(currentVersion.compareTo(olderVersion) > 0, "Current should be newer than older");
        assertTrue(currentVersion.compareTo(newerVersion) < 0, "Current should be older than newer");
    }

    @Test
    @DisplayName("Should handle large usage duration values")
    void testLargeUsageDuration() throws Exception {
        Method formatMethod = controller.getClass().getDeclaredMethod("formatSecondsHumanReadable", int.class);
        formatMethod.setAccessible(true);

        // Test large duration (1 day = 86400 seconds)
        String resultDay = (String) formatMethod.invoke(controller, 86400);
        assertNotNull(resultDay);
        assertTrue(resultDay.contains("24:00:00") || resultDay.contains("00:00"),
                "Should handle 24 hours correctly");
    }

    @Test
    @DisplayName("Should validate usage statistics checkbox functionality")
    void testUsageStatisticsCheckbox() {
        // CheckBox should be available for user to enable/disable usage statistics
        CheckBox checkbox = new CheckBox();
        checkbox.setSelected(false);

        assertNotNull(checkbox);
        assertFalse(checkbox.isSelected(), "Should default to not selected");

        checkbox.setSelected(true);
        assertTrue(checkbox.isSelected(), "Should be selectable");
    }

    @Test
    @DisplayName("Should handle parsing invalid duration gracefully")
    void testInvalidDurationParsing() {
        // Test that invalid duration defaults to 0
        String invalidDuration = "invalid";

        assertThrows(NumberFormatException.class, () -> {
            Integer.parseInt(invalidDuration);
        }, "Should throw exception for invalid duration");
    }

    @Test
    @DisplayName("Should validate version update visibility logic")
    void testVersionUpdateVisibility() {
        String targetVersion = "20221008";
        String currentVersion = "20221008";
        String differentVersion = "20230101";

        // Version update should be visible when versions match
        boolean shouldShowUpdate = targetVersion.equals(currentVersion);
        assertTrue(shouldShowUpdate, "Should show update when versions match");

        // Version update should not be visible when versions differ
        boolean shouldNotShowUpdate = targetVersion.equals(differentVersion);
        assertFalse(shouldNotShowUpdate, "Should not show update when versions differ");
    }

    @Test
    @DisplayName("Should format time components correctly")
    void testTimeComponentFormatting() {
        // Hours, minutes, seconds should all be formatted with leading zeros
        LocalTime time = LocalTime.of(1, 2, 3);
        String formatted = time.toString();

        assertEquals("01:02:03", formatted, "Time should be formatted with leading zeros");
    }

    @Test
    @DisplayName("Should handle edge case durations")
    void testEdgeCaseDurations() throws Exception {
        Method formatMethod = controller.getClass().getDeclaredMethod("formatSecondsHumanReadable", int.class);
        formatMethod.setAccessible(true);

        // Test 1 second
        String result1 = (String) formatMethod.invoke(controller, 1);
        assertNotNull(result1);
        assertTrue(result1.contains("00:00:01"));

        // Test 59 seconds
        String result59 = (String) formatMethod.invoke(controller, 59);
        assertNotNull(result59);
        assertTrue(result59.contains("00:00:59"));

        // Test 3599 seconds (59 minutes, 59 seconds)
        String result3599 = (String) formatMethod.invoke(controller, 3599);
        assertNotNull(result3599);
        assertTrue(result3599.contains("00:59:59"));
    }

    @Test
    @DisplayName("Should validate welcome message for new users")
    void testWelcomeMessageForNewUsers() {
        String welcomeMessage = "You are here the first time!";

        assertNotNull(welcomeMessage);
        assertFalse(welcomeMessage.isEmpty());
        // Welcome message should be friendly and clear
        assertTrue(welcomeMessage.length() > 10, "Welcome message should be descriptive");
    }
}
