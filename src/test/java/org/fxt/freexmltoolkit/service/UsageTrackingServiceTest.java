package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.statistics.DailyStatistics;
import org.fxt.freexmltoolkit.domain.statistics.FeatureUsage;
import org.fxt.freexmltoolkit.domain.statistics.UsageStatistics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UsageTrackingService Tests")
public class UsageTrackingServiceTest {

    private UsageTrackingServiceImpl service;
    private String originalUserHome;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        
        // Reset Singleton
        Field instanceField = UsageTrackingServiceImpl.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
        
        service = UsageTrackingServiceImpl.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    @DisplayName("Sollte Singleton bereitstellen")
    void testSingleton() {
        UsageTrackingServiceImpl instance2 = UsageTrackingServiceImpl.getInstance();
        assertSame(service, instance2);
    }

    @Test
    @DisplayName("Sollte Datei-Validierungen tracken")
    void testTrackFileValidation() {
        service.trackFileValidation(5);
        service.trackFileValidation(3);
        
        UsageStatistics stats = service.getStatistics();
        assertEquals(2, stats.getFilesValidated());
        assertEquals(8, stats.getValidationErrors());
        
        DailyStatistics today = stats.getTodayStats();
        assertEquals(2, today.getFilesValidated());
        assertEquals(8, today.getErrorsFound());
    }

    @Test
    @DisplayName("Sollte Transformationen tracken")
    void testTrackTransformation() {
        service.trackTransformation();
        
        UsageStatistics stats = service.getStatistics();
        assertEquals(1, stats.getTransformationsPerformed());
        assertTrue(stats.getFeatureUsage().get("xslt_transformation").isDiscovered());
    }

    @Test
    @DisplayName("Sollte Feature-Nutzung tracken")
    void testTrackFeatureUsed() {
        String featureId = "pdf_generation";
        service.trackFeatureUsed(featureId);
        
        FeatureUsage feature = service.getStatistics().getFeatureUsage().get(featureId);
        assertNotNull(feature);
        assertEquals(1, feature.getUseCount());
        assertTrue(feature.isDiscovered());
    }

    @Test
    @DisplayName("Sollte den Produktivitäts-Score berechnen")
    void testProductivityScore() {
        // Initial score should be low
        int initialScore = service.getProductivityScore();
        
        // Perform some actions
        for (int i = 0; i < 10; i++) service.trackFileValidation(0);
        for (int i = 0; i < 5; i++) service.trackTransformation();
        service.trackFeatureUsed("xpath_queries");
        
        int updatedScore = service.getProductivityScore();
        assertTrue(updatedScore > initialScore);
        assertNotNull(service.getProductivityLevel());
    }

    @Test
    @DisplayName("Sollte tägliche Statistiken abrufen")
    void testGetDailyStats() {
        service.trackFileValidation(1);
        
        List<DailyStatistics> dailyStats = service.getDailyStats(7);
        assertEquals(7, dailyStats.size());
        assertEquals(LocalDate.now(), dailyStats.get(0).getDate());
        assertEquals(1, dailyStats.get(0).getFilesValidated());
    }

    @Test
    @DisplayName("Sollte Statistiken persistieren")
    void testPersistence() throws Exception {
        service.trackXPathQuery();
        service.saveStatistics();
        
        // Shutdown current service
        service.shutdown();
        
        // Reset Singleton
        Field instanceField = UsageTrackingServiceImpl.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
        
        UsageTrackingServiceImpl newService = UsageTrackingServiceImpl.getInstance();
        assertEquals(1, newService.getStatistics().getXpathQueriesExecuted());
    }

    @Test
    @DisplayName("Sollte Statistiken löschen können")
    void testClearStatistics() {
        service.trackFileOpened();
        service.clearStatistics();
        
        assertEquals(0, service.getStatistics().getFilesOpened());
        // Core features should still be initialized
        assertFalse(service.getStatistics().getFeatureUsage().isEmpty());
    }
}
