package org.fxt.freexmltoolkit.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.statistics.DailyStatistics;
import org.fxt.freexmltoolkit.domain.statistics.FeatureTip;
import org.fxt.freexmltoolkit.domain.statistics.FeatureUsage;
import org.fxt.freexmltoolkit.domain.statistics.UsageStatistics;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Implementation of UsageTrackingService with JSON persistence.
 * Tracks all application usage metrics for the gamification dashboard.
 */
public class UsageTrackingServiceImpl implements UsageTrackingService {

    private static final Logger logger = LogManager.getLogger(UsageTrackingServiceImpl.class);
    private static UsageTrackingServiceImpl instance;

    private static final String STATISTICS_FILE = "usage-statistics.json";
    private static final String TRACKING_ENABLED_KEY = "tracking.enabled";
    private static final long SAVE_DEBOUNCE_SECONDS = 30;

    private final Path statisticsFile;
    private final Gson gson;
    private UsageStatistics statistics;
    private LocalDateTime sessionStart;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final ScheduledExecutorService saveScheduler;

    private UsageTrackingServiceImpl() {
        // Initialize statistics file in user's home directory
        String userHome = System.getProperty("user.home");
        Path configDir = Paths.get(userHome, ".freexmltoolkit");

        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }

        this.statisticsFile = configDir.resolve(STATISTICS_FILE);

        // Configure Gson with custom date adapters
        this.gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .setPrettyPrinting()
            .create();

        this.statistics = new UsageStatistics();

        // Initialize save scheduler for debounced saves
        this.saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "UsageTracking-SaveScheduler");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic save check
        saveScheduler.scheduleAtFixedRate(this::checkAndSave,
            SAVE_DEBOUNCE_SECONDS, SAVE_DEBOUNCE_SECONDS, TimeUnit.SECONDS);

        loadStatistics();
        initializeFeatureDefinitions();
    }

    public static synchronized UsageTrackingServiceImpl getInstance() {
        if (instance == null) {
            instance = new UsageTrackingServiceImpl();
        }
        return instance;
    }

    /**
     * Initialize all feature definitions if not already present
     */
    private void initializeFeatureDefinitions() {
        Map<String, FeatureUsage> featureMap = statistics.getFeatureUsage();

        // Core Features
        addFeatureIfMissing(featureMap, "xml_validation", "XML Validation", "Validation");
        addFeatureIfMissing(featureMap, "xml_formatting", "Format/Pretty Print", "Editing");
        addFeatureIfMissing(featureMap, "xsd_validation", "XSD Schema Validation", "Validation");
        addFeatureIfMissing(featureMap, "xsd_visualization", "XSD Visual Editor", "Editing");

        // Advanced Features
        addFeatureIfMissing(featureMap, "xpath_queries", "XPath Queries", "Query");
        addFeatureIfMissing(featureMap, "xquery_execution", "XQuery Execution", "Query");
        addFeatureIfMissing(featureMap, "xslt_transformation", "XSLT Transformation", "Transformation");
        addFeatureIfMissing(featureMap, "schematron_validation", "Schematron Rules", "Validation");

        // Specialized Features
        addFeatureIfMissing(featureMap, "schema_generation", "Schema Generation", "Tools");
        addFeatureIfMissing(featureMap, "digital_signature", "XML Digital Signatures", "Security");
        addFeatureIfMissing(featureMap, "pdf_generation", "PDF Generation (FOP)", "Export");
        addFeatureIfMissing(featureMap, "batch_validation", "Batch Validation", "Validation");

        // Power User Features
        addFeatureIfMissing(featureMap, "intellisense", "IntelliSense Auto-complete", "Editing");
        addFeatureIfMissing(featureMap, "xsd_documentation", "XSD Documentation Export", "Export");
        addFeatureIfMissing(featureMap, "favorites_system", "Favorites Management", "Organization");
    }

    private void addFeatureIfMissing(Map<String, FeatureUsage> featureMap,
                                     String id, String name, String category) {
        if (!featureMap.containsKey(id)) {
            featureMap.put(id, new FeatureUsage(id, name, category));
        }
    }

    // ================== Tracking Methods ==================

    @Override
    public void trackFileValidation(int errorCount) {
        if (!isTrackingEnabled()) return;

        statistics.incrementFilesValidated();
        statistics.addValidationErrors(errorCount);

        DailyStatistics today = statistics.getTodayStats();
        today.incrementFilesValidated();
        today.addErrorsFound(errorCount);

        trackFeatureUsed("xml_validation");
        markDirty();

        logger.debug("Tracked file validation with {} errors", errorCount);
    }

    @Override
    public void trackErrorCorrected(int count) {
        if (!isTrackingEnabled()) return;

        statistics.incrementErrorsCorrected(count);
        statistics.getTodayStats().addErrorsFixed(count);
        markDirty();

        logger.debug("Tracked {} errors corrected", count);
    }

    @Override
    public void trackTransformation() {
        if (!isTrackingEnabled()) return;

        statistics.incrementTransformations();
        statistics.getTodayStats().incrementTransformations();
        trackFeatureUsed("xslt_transformation");
        markDirty();

        logger.debug("Tracked XSLT transformation");
    }

    @Override
    public void trackFeatureUsed(String featureId) {
        if (!isTrackingEnabled() || featureId == null) return;

        FeatureUsage feature = statistics.getFeatureUsage().get(featureId);
        if (feature != null) {
            feature.incrementUseCount();
            feature.setLastUsed(LocalDateTime.now());
            if (!feature.isDiscovered()) {
                feature.setDiscovered(true);
                feature.setFirstUsed(LocalDateTime.now());
                logger.info("Feature discovered: {}", feature.getFeatureName());
            }
            markDirty();
        }
    }

    @Override
    public void trackXPathQuery() {
        if (!isTrackingEnabled()) return;

        statistics.incrementXpathQueries();
        statistics.getTodayStats().incrementXpathQueries();
        trackFeatureUsed("xpath_queries");
        markDirty();

        logger.debug("Tracked XPath query");
    }

    @Override
    public void trackXQueryExecution() {
        if (!isTrackingEnabled()) return;

        statistics.incrementXqueryExecutions();
        trackFeatureUsed("xquery_execution");
        markDirty();

        logger.debug("Tracked XQuery execution");
    }

    @Override
    public void trackSchematronValidation() {
        if (!isTrackingEnabled()) return;

        statistics.incrementSchematronValidations();
        statistics.getTodayStats().incrementSchematronValidations();
        trackFeatureUsed("schematron_validation");
        markDirty();

        logger.debug("Tracked Schematron validation");
    }

    @Override
    public void trackFormatting() {
        if (!isTrackingEnabled()) return;

        statistics.incrementDocumentsFormatted();
        statistics.getTodayStats().incrementFormattings();
        trackFeatureUsed("xml_formatting");
        markDirty();

        logger.debug("Tracked document formatting");
    }

    @Override
    public void trackSchemaGeneration() {
        if (!isTrackingEnabled()) return;

        statistics.incrementSchemasGenerated();
        trackFeatureUsed("schema_generation");
        markDirty();

        logger.debug("Tracked schema generation");
    }

    @Override
    public void trackSignatureOperation(boolean isSign) {
        if (!isTrackingEnabled()) return;

        if (isSign) {
            statistics.incrementSignaturesCreated();
        } else {
            statistics.incrementSignaturesVerified();
        }
        trackFeatureUsed("digital_signature");
        markDirty();

        logger.debug("Tracked signature operation: {}", isSign ? "sign" : "verify");
    }

    @Override
    public void trackPdfGeneration() {
        if (!isTrackingEnabled()) return;

        statistics.incrementPdfsGenerated();
        trackFeatureUsed("pdf_generation");
        markDirty();

        logger.debug("Tracked PDF generation");
    }

    @Override
    public void trackFileOpened() {
        if (!isTrackingEnabled()) return;

        statistics.incrementFilesOpened();
        markDirty();

        logger.debug("Tracked file opened");
    }

    // ================== Session Management ==================

    @Override
    public void startSession() {
        this.sessionStart = LocalDateTime.now();
        statistics.incrementLaunches();
        statistics.setLastLaunch(sessionStart);
        markDirty();

        logger.info("Usage tracking session started");
    }

    @Override
    public void endSession() {
        if (sessionStart != null) {
            long sessionSeconds = ChronoUnit.SECONDS.between(sessionStart, LocalDateTime.now());
            statistics.addUsageSeconds(sessionSeconds);

            int sessionMinutes = (int) (sessionSeconds / 60);
            statistics.getTodayStats().addUsageMinutes(sessionMinutes);

            logger.info("Usage tracking session ended. Duration: {} minutes", sessionMinutes);
        }

        // Force save on exit
        saveStatisticsInternal();

        // Cleanup old data
        statistics.cleanupOldDailyStats();
    }

    // ================== Statistics Retrieval ==================

    @Override
    public UsageStatistics getStatistics() {
        return statistics;
    }

    @Override
    public List<DailyStatistics> getDailyStats(int daysBack) {
        List<DailyStatistics> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < daysBack; i++) {
            LocalDate date = today.minusDays(i);
            DailyStatistics dayStats = statistics.getDailyStats().get(date);
            if (dayStats != null) {
                result.add(dayStats);
            } else {
                result.add(new DailyStatistics(date));
            }
        }

        return result;
    }

    @Override
    public int getProductivityScore() {
        // Activity score (max 30 points)
        int activityScore = Math.min(30,
            (statistics.getFilesValidated() / 10) +
            (statistics.getTransformationsPerformed() / 5));

        // Error fixing score (max 20 points)
        int errorScore = Math.min(20, statistics.getErrorsCorrected() * 2);

        // Feature breadth score (max 25 points)
        int featuresUsed = (int) statistics.getDiscoveredFeaturesCount();
        int featureScore = Math.min(25, featuresUsed * 5);

        // Consistency score (max 25 points)
        int activeDays = statistics.getActiveDaysLast7();
        int consistencyScore = Math.min(25, activeDays * 4);

        return activityScore + errorScore + featureScore + consistencyScore;
    }

    @Override
    public String getProductivityLevel() {
        int score = getProductivityScore();
        if (score >= 91) return "Expert";
        if (score >= 71) return "Professional";
        if (score >= 51) return "Advanced";
        if (score >= 31) return "Intermediate";
        return "Beginner";
    }

    @Override
    public List<FeatureUsage> getUnusedFeatures() {
        return statistics.getFeatureUsage().values().stream()
            .filter(f -> !f.isDiscovered())
            .collect(Collectors.toList());
    }

    @Override
    public List<FeatureUsage> getAllFeatures() {
        return new ArrayList<>(statistics.getFeatureUsage().values());
    }

    @Override
    public List<FeatureTip> getRelevantTips() {
        List<FeatureTip> tips = new ArrayList<>();
        List<FeatureUsage> unusedFeatures = getUnusedFeatures();

        // Generate tips for undiscovered features (max 3)
        int tipCount = 0;
        for (FeatureUsage feature : unusedFeatures) {
            if (tipCount >= 3) break;

            FeatureTip tip = createTipForFeature(feature);
            if (tip != null) {
                tips.add(tip);
                tipCount++;
            }
        }

        // Sort by priority
        tips.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return tips;
    }

    private FeatureTip createTipForFeature(FeatureUsage feature) {
        return switch (feature.getFeatureId()) {
            case "xpath_queries" -> new FeatureTip(
                feature.getFeatureId(),
                "Did you know? XPath queries help you quickly locate specific elements in large XML documents.",
                "xmlUltimate",
                10,
                "bi-search"
            );
            case "xslt_transformation" -> new FeatureTip(
                feature.getFeatureId(),
                "Transform your XML documents to HTML, text, or other formats using XSLT.",
                "xsltDeveloper",
                9,
                "bi-arrow-repeat"
            );
            case "schematron_validation" -> new FeatureTip(
                feature.getFeatureId(),
                "Schematron rules enable business rule validation beyond what XSD can express.",
                "schematron",
                8,
                "bi-shield-check"
            );
            case "xsd_visualization" -> new FeatureTip(
                feature.getFeatureId(),
                "Visualize complex XSD schemas with our graphical editor for better understanding.",
                "xsd",
                7,
                "bi-diagram-3"
            );
            case "digital_signature" -> new FeatureTip(
                feature.getFeatureId(),
                "Sign and verify XML documents for security and authenticity.",
                "signature",
                6,
                "bi-key"
            );
            case "pdf_generation" -> new FeatureTip(
                feature.getFeatureId(),
                "Generate professional PDF documents from XML using XSL-FO templates.",
                "fop",
                5,
                "bi-file-pdf"
            );
            case "schema_generation" -> new FeatureTip(
                feature.getFeatureId(),
                "Automatically generate XSD schemas from your XML sample documents.",
                "schemaGenerator",
                4,
                "bi-gear"
            );
            case "xquery_execution" -> new FeatureTip(
                feature.getFeatureId(),
                "Use XQuery for powerful querying and transforming of XML data.",
                "xmlUltimate",
                3,
                "bi-code"
            );
            default -> null;
        };
    }

    @Override
    public int getWeeklyChange(String metric) {
        LocalDate today = LocalDate.now();
        int thisWeek = 0;
        int lastWeek = 0;

        for (int i = 0; i < 7; i++) {
            DailyStatistics dayStats = statistics.getDailyStats().get(today.minusDays(i));
            if (dayStats != null) {
                thisWeek += getMetricFromDailyStats(dayStats, metric);
            }

            DailyStatistics lastWeekStats = statistics.getDailyStats().get(today.minusDays(i + 7));
            if (lastWeekStats != null) {
                lastWeek += getMetricFromDailyStats(lastWeekStats, metric);
            }
        }

        return thisWeek - lastWeek;
    }

    private int getMetricFromDailyStats(DailyStatistics stats, String metric) {
        return switch (metric) {
            case "validations" -> stats.getFilesValidated();
            case "transformations" -> stats.getTransformations();
            case "formattings" -> stats.getFormattings();
            case "errors_found" -> stats.getErrorsFound();
            case "errors_fixed" -> stats.getErrorsFixed();
            default -> 0;
        };
    }

    // ================== Settings ==================

    @Override
    public boolean isTrackingEnabled() {
        PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
        if (propertiesService == null) return true; // Default to enabled if service not available

        String value = propertiesService.get(TRACKING_ENABLED_KEY);
        return value == null || Boolean.parseBoolean(value);
    }

    @Override
    public void setTrackingEnabled(boolean enabled) {
        PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
        if (propertiesService != null) {
            propertiesService.set(TRACKING_ENABLED_KEY, String.valueOf(enabled));
        }
        logger.info("Usage tracking {}", enabled ? "enabled" : "disabled");
    }

    @Override
    public void clearStatistics() {
        statistics = new UsageStatistics();
        initializeFeatureDefinitions();
        saveStatisticsInternal();
        logger.info("Usage statistics cleared");
    }

    @Override
    public void saveStatistics() {
        saveStatisticsInternal();
    }

    // ================== Persistence ==================

    private void loadStatistics() {
        if (!Files.exists(statisticsFile)) {
            logger.info("No statistics file found, starting with fresh statistics");
            return;
        }

        try {
            String json = Files.readString(statisticsFile);
            statistics = gson.fromJson(json, UsageStatistics.class);

            if (statistics == null) {
                statistics = new UsageStatistics();
            }

            // Ensure maps are not null
            if (statistics.getDailyStats() == null) {
                statistics.setDailyStats(new HashMap<>());
            }
            if (statistics.getFeatureUsage() == null) {
                statistics.setFeatureUsage(new HashMap<>());
            }

            logger.info("Loaded usage statistics: {} launches, {} validations",
                statistics.getTotalLaunches(), statistics.getFilesValidated());

        } catch (Exception e) {
            logger.error("Failed to load usage statistics", e);
            statistics = new UsageStatistics();
        }
    }

    private void saveStatisticsInternal() {
        try {
            String json = gson.toJson(statistics);
            Files.writeString(statisticsFile, json);
            dirty.set(false);
            logger.debug("Saved usage statistics");
        } catch (IOException e) {
            logger.error("Failed to save usage statistics", e);
        }
    }

    private void markDirty() {
        dirty.set(true);
    }

    private void checkAndSave() {
        if (dirty.compareAndSet(true, false)) {
            saveStatisticsInternal();
        }
    }

    /**
     * Shutdown the save scheduler.
     * Should be called when the application exits.
     */
    public void shutdown() {
        saveScheduler.shutdown();
        try {
            if (!saveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                saveScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ================== Gson Adapters ==================

    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>,
            com.google.gson.JsonDeserializer<LocalDateTime> {

        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc,
                                     com.google.gson.JsonSerializationContext context) {
            return new JsonPrimitive(formatter.format(src));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT,
                                         com.google.gson.JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }

    private static class LocalDateAdapter implements com.google.gson.JsonSerializer<LocalDate>,
            com.google.gson.JsonDeserializer<LocalDate> {

        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        @Override
        public JsonElement serialize(LocalDate src, Type typeOfSrc,
                                     com.google.gson.JsonSerializationContext context) {
            return new JsonPrimitive(formatter.format(src));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT,
                                      com.google.gson.JsonDeserializationContext context) {
            return LocalDate.parse(json.getAsString(), formatter);
        }
    }
}
