package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XmlTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Service for loading and saving XML templates from/to the file system.
 * Templates are stored as simple text files with properties format.
 */
public class TemplateFileService {

    private static final Logger logger = LogManager.getLogger(TemplateFileService.class);

    private static TemplateFileService instance;
    private final Path templatesDirectory;

    private TemplateFileService() {
        // Use release/examples/templates as the templates directory
        this.templatesDirectory = getTemplatesDirectory();

        try {
            Files.createDirectories(templatesDirectory);
            logger.info("Templates directory initialized: {}", templatesDirectory);
        } catch (IOException e) {
            logger.error("Failed to create templates directory: {}", templatesDirectory, e);
        }
    }

    public static synchronized TemplateFileService getInstance() {
        if (instance == null) {
            instance = new TemplateFileService();
        }
        return instance;
    }

    /**
     * Get the templates directory path
     */
    private Path getTemplatesDirectory() {
        // Try to find the project root and use release/examples/templates
        Path currentDir = Paths.get(System.getProperty("user.dir"));

        // Look for the templates directory in various possible locations
        Path[] possiblePaths = {
                currentDir.resolve("release/examples/templates"),
                currentDir.resolve("../release/examples/templates"),
                currentDir.resolve("templates"),
                Paths.get(System.getProperty("user.home")).resolve(".freexmltoolkit/templates")
        };

        for (Path path : possiblePaths) {
            if (Files.exists(path) || tryCreateDirectory(path)) {
                logger.debug("Using templates directory: {}", path);
                return path;
            }
        }

        // Fallback to user home if nothing else works
        Path fallback = Paths.get(System.getProperty("user.home")).resolve(".freexmltoolkit/templates");
        tryCreateDirectory(fallback);
        logger.warn("Using fallback templates directory: {}", fallback);
        return fallback;
    }

    private boolean tryCreateDirectory(Path path) {
        try {
            Files.createDirectories(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Load all templates from the templates directory
     */
    public List<XmlTemplate> loadTemplatesFromDirectory() {
        List<XmlTemplate> templates = new ArrayList<>();

        if (!Files.exists(templatesDirectory)) {
            logger.warn("Templates directory does not exist: {}", templatesDirectory);
            return templates;
        }

        try (Stream<Path> files = Files.list(templatesDirectory)) {
            files.filter(path -> path.toString().endsWith(".template"))
                    .forEach(path -> {
                        try {
                            XmlTemplate template = loadTemplateFromFile(path);
                            if (template != null) {
                                templates.add(template);
                                logger.debug("Loaded template: {} from {}", template.getName(), path.getFileName());
                            }
                        } catch (Exception e) {
                            logger.error("Failed to load template from {}: {}", path.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.error("Failed to list templates directory: {}", templatesDirectory, e);
        }

        logger.info("Loaded {} templates from directory: {}", templates.size(), templatesDirectory);
        return templates;
    }

    /**
     * Load a single template from a properties file
     */
    public XmlTemplate loadTemplateFromFile(Path filePath) throws IOException {
        Properties props = new Properties();
        String content = "";

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            StringBuilder contentBuilder = new StringBuilder();
            boolean inContent = false;

            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("CONTENT_START")) {
                    inContent = true;
                    continue;
                } else if (line.trim().equals("CONTENT_END")) {
                    inContent = false;
                    continue;
                }

                if (inContent) {
                    contentBuilder.append(line).append("\n");
                } else if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        props.setProperty(parts[0].trim(), parts[1].trim());
                    }
                }
            }

            // Remove trailing newline from content
            content = contentBuilder.toString();
            if (content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
            }
        }

        // Build template from properties
        String id = props.getProperty("id");
        String name = props.getProperty("name");
        String category = props.getProperty("category", "Custom");

        if (id == null || name == null || content.isEmpty()) {
            throw new IllegalArgumentException("Template must have id, name, and content");
        }

        XmlTemplate template = new XmlTemplate(id, content, category);
        template.setName(name);
        template.setDescription(props.getProperty("description", ""));
        template.setBuiltIn(false);

        // Parse tags
        String tagsStr = props.getProperty("tags");
        if (tagsStr != null && !tagsStr.trim().isEmpty()) {
            List<String> tags = new ArrayList<>();
            for (String tag : tagsStr.split(",")) {
                tags.add(tag.trim());
            }
            template.setTags(tags);
        }

        return template;
    }

    /**
     * Save a template to the templates directory
     */
    public void saveTemplateToDirectory(XmlTemplate template) throws IOException {
        String fileName = sanitizeFileName(template.getId()) + ".template";
        Path filePath = templatesDirectory.resolve(fileName);

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
            writer.println("# XML Template Definition");
            writer.println("# Generated at: " + java.time.LocalDateTime.now());
            writer.println();

            // Basic properties
            writer.println("id=" + template.getId());
            writer.println("name=" + template.getName());
            writer.println("description=" + (template.getDescription() != null ? template.getDescription() : ""));
            writer.println("category=" + (template.getCategory() != null ? template.getCategory() : "Custom"));

            // Tags
            if (template.getTags() != null && !template.getTags().isEmpty()) {
                writer.println("tags=" + String.join(", ", template.getTags()));
            }

            writer.println();

            // Content
            writer.println("CONTENT_START");
            writer.print(template.getContent());
            writer.println();
            writer.println("CONTENT_END");
        }

        logger.info("Saved template '{}' to {}", template.getName(), filePath.getFileName());
    }

    /**
     * Delete a template from the templates directory
     */
    public boolean deleteTemplateFromDirectory(String templateId) {
        String fileName = sanitizeFileName(templateId) + ".template";
        Path filePath = templatesDirectory.resolve(fileName);

        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                logger.info("Deleted template file: {}", filePath.getFileName());
            }
            return deleted;
        } catch (IOException e) {
            logger.error("Failed to delete template file: {}", filePath.getFileName(), e);
            return false;
        }
    }

    /**
     * Check if a template file exists
     */
    public boolean templateFileExists(String templateId) {
        String fileName = sanitizeFileName(templateId) + ".template";
        Path filePath = templatesDirectory.resolve(fileName);
        return Files.exists(filePath);
    }

    /**
     * Get the templates directory path
     */
    public Path getTemplatesDirectoryPath() {
        return templatesDirectory;
    }

    /**
     * List all template files in the directory
     */
    public List<Path> listTemplateFiles() throws IOException {
        List<Path> files = new ArrayList<>();

        if (!Files.exists(templatesDirectory)) {
            return files;
        }

        try (Stream<Path> stream = Files.list(templatesDirectory)) {
            stream.filter(path -> path.toString().endsWith(".template"))
                    .forEach(files::add);
        }

        return files;
    }

    /**
     * Create some default templates in the directory if it's empty
     */
    public void createDefaultTemplatesIfEmpty() {
        try {
            List<Path> existingFiles = listTemplateFiles();
            if (existingFiles.isEmpty()) {
                logger.info("Templates directory is empty, creating default templates");
                createDefaultTemplates();
            }
        } catch (IOException e) {
            logger.error("Failed to check templates directory", e);
        }
    }

    /**
     * Create some default templates
     */
    private void createDefaultTemplates() {
        try {
            // Create a simple element template
            XmlTemplate elementTemplate = createSimpleElementTemplate();
            saveTemplateToDirectory(elementTemplate);

            // Create a SOAP envelope template  
            XmlTemplate soapTemplate = createSoapEnvelopeTemplate();
            saveTemplateToDirectory(soapTemplate);

            // Create a REST response template
            XmlTemplate restTemplate = createRestResponseTemplate();
            saveTemplateToDirectory(restTemplate);

            logger.info("Created default templates in directory");

        } catch (IOException e) {
            logger.error("Failed to create default templates", e);
        }
    }

    private XmlTemplate createSimpleElementTemplate() {
        XmlTemplate template = new XmlTemplate("file-simple-element",
                "<${elementName}>${content}</${elementName}>",
                "Basic");
        template.setDescription("Simple XML element template from file");

        List<String> tags = new ArrayList<>();
        tags.add("basic");
        tags.add("element");
        template.setTags(tags);

        return template;
    }

    private XmlTemplate createSoapEnvelopeTemplate() {
        XmlTemplate template = new XmlTemplate("file-soap-envelope",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                        "    <soap:Header>\n" +
                        "        <!-- Header elements -->\n" +
                        "    </soap:Header>\n" +
                        "    <soap:Body>\n" +
                        "        <${operationName}>\n" +
                        "            ${parameters}\n" +
                        "        </${operationName}>\n" +
                        "    </soap:Body>\n" +
                        "</soap:Envelope>",
                "Web Services");
        template.setDescription("SOAP envelope template from file");

        List<String> tags = new ArrayList<>();
        tags.add("soap");
        tags.add("webservice");
        template.setTags(tags);

        return template;
    }

    private XmlTemplate createRestResponseTemplate() {
        XmlTemplate template = new XmlTemplate("file-rest-response",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<response>\n" +
                        "    <status>${status}</status>\n" +
                        "    <message>${message}</message>\n" +
                        "    <data>\n" +
                        "        ${data}\n" +
                        "    </data>\n" +
                        "    <timestamp>${timestamp}</timestamp>\n" +
                        "</response>",
                "Web Services");
        template.setDescription("REST API response template from file");

        List<String> tags = new ArrayList<>();
        tags.add("rest");
        tags.add("api");
        template.setTags(tags);

        return template;
    }

    /**
     * Sanitize a filename to remove invalid characters
     */
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}