package org.fxt.freexmltoolkit.controls.intellisense;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * File-based template repository implementation.
 * Stores templates in JSON format on the filesystem.
 */
public class FileTemplateRepository implements TemplateEngine.TemplateRepository {

    private final Path repositoryPath;
    private final String fileExtension;

    public FileTemplateRepository(Path repositoryPath) {
        this(repositoryPath, ".template");
    }

    public FileTemplateRepository(Path repositoryPath, String fileExtension) {
        this.repositoryPath = repositoryPath;
        this.fileExtension = fileExtension;

        try {
            Files.createDirectories(repositoryPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create repository directory: " + repositoryPath, e);
        }
    }

    /**
     * Create a FileTemplateRepository using the central templates directory
     */
    public static FileTemplateRepository createWithCentralDirectory() {
        Path templatesDir = getCentralTemplatesDirectory();
        return new FileTemplateRepository(templatesDir, ".template");
    }

    /**
     * Get the central templates directory path
     */
    private static Path getCentralTemplatesDirectory() {
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
                return path;
            }
        }

        // Fallback to user home if nothing else works
        Path fallback = Paths.get(System.getProperty("user.home")).resolve(".freexmltoolkit/templates");
        tryCreateDirectory(fallback);
        return fallback;
    }

    private static boolean tryCreateDirectory(Path path) {
        try {
            Files.createDirectories(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Collection<SnippetTemplate> loadTemplates() throws IOException {
        List<SnippetTemplate> templates = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(repositoryPath, "*" + fileExtension)) {
            for (Path templateFile : stream) {
                try {
                    SnippetTemplate template = loadTemplate(templateFile);
                    if (template != null) {
                        templates.add(template);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load template from " + templateFile + ": " + e.getMessage());
                }
            }
        }

        return templates;
    }

    @Override
    public void saveTemplate(SnippetTemplate template) throws IOException {
        Path templateFile = repositoryPath.resolve(template.getId() + fileExtension);

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(templateFile))) {
            writeTemplate(writer, template);
        }
    }

    @Override
    public boolean deleteTemplate(String templateId) throws IOException {
        Path templateFile = repositoryPath.resolve(templateId + fileExtension);
        return Files.deleteIfExists(templateFile);
    }

    /**
     * Load template from file
     */
    private SnippetTemplate loadTemplate(Path templateFile) throws IOException {
        Properties props = new Properties();

        try (BufferedReader reader = Files.newBufferedReader(templateFile)) {
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
            String content = contentBuilder.toString();
            if (content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
            }

            return buildTemplateFromProperties(props, content);
        }
    }

    /**
     * Build template from properties
     */
    private SnippetTemplate buildTemplateFromProperties(Properties props, String content) {
        String id = props.getProperty("id");
        String name = props.getProperty("name");

        if (id == null || name == null || content.isEmpty()) {
            throw new IllegalArgumentException("Template must have id, name, and content");
        }

        SnippetTemplate.Builder builder = new SnippetTemplate.Builder(id, name, content);

        // Optional properties
        if (props.containsKey("description")) {
            builder.description(props.getProperty("description"));
        }

        if (props.containsKey("category")) {
            String categoryStr = props.getProperty("category");
            try {
                SnippetTemplate.TemplateCategory category =
                        SnippetTemplate.TemplateCategory.valueOf(categoryStr.toUpperCase());
                builder.category(category);
            } catch (IllegalArgumentException e) {
                // Use default category if invalid
                builder.category(SnippetTemplate.TemplateCategory.CUSTOM);
            }
        }

        if (props.containsKey("tags")) {
            String[] tags = props.getProperty("tags").split(",");
            builder.tags(Arrays.stream(tags).map(String::trim).toArray(String[]::new));
        }

        if (props.containsKey("priority")) {
            try {
                int priority = Integer.parseInt(props.getProperty("priority"));
                builder.priority(priority);
            } catch (NumberFormatException e) {
                // Use default priority
            }
        }

        if (props.containsKey("contextSensitive")) {
            boolean contextSensitive = Boolean.parseBoolean(props.getProperty("contextSensitive"));
            builder.contextSensitive(contextSensitive);
        }

        if (props.containsKey("author")) {
            builder.author(props.getProperty("author"));
        }

        if (props.containsKey("version")) {
            builder.version(props.getProperty("version"));
        }

        // Default values
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("default.")) {
                String variable = key.substring("default.".length());
                String value = props.getProperty(key);
                builder.defaultValue(variable, value);
            }
        }

        // Required variables
        if (props.containsKey("requiredVariables")) {
            String[] required = props.getProperty("requiredVariables").split(",");
            builder.requiredVariables(Arrays.stream(required).map(String::trim).toArray(String[]::new));
        }

        return builder.build();
    }

    /**
     * Write template to file
     */
    private void writeTemplate(PrintWriter writer, SnippetTemplate template) {
        writer.println("# XML Template Definition");
        writer.println("# Generated at: " + java.time.LocalDateTime.now());
        writer.println();

        // Basic properties
        writer.println("id=" + template.getId());
        writer.println("name=" + template.getName());
        writer.println("description=" + template.getDescription());
        writer.println("category=" + template.getCategory().name());
        writer.println("priority=" + template.getPriority());
        writer.println("contextSensitive=" + template.isContextSensitive());

        // Metadata
        if (!template.getAuthor().isEmpty()) {
            writer.println("author=" + template.getAuthor());
        }
        writer.println("version=" + template.getVersion());

        // Tags
        if (!template.getTags().isEmpty()) {
            writer.println("tags=" + String.join(", ", template.getTags()));
        }

        // Required variables
        if (!template.getRequiredVariables().isEmpty()) {
            writer.println("requiredVariables=" + String.join(", ", template.getRequiredVariables()));
        }

        // Default values
        for (Map.Entry<String, String> entry : template.getDefaultValues().entrySet()) {
            writer.println("default." + entry.getKey() + "=" + entry.getValue());
        }

        writer.println();

        // Content
        writer.println("CONTENT_START");
        writer.print(template.getContent());
        writer.println();
        writer.println("CONTENT_END");
    }

    /**
     * Get repository path
     */
    public Path getRepositoryPath() {
        return repositoryPath;
    }

    /**
     * List all template files
     */
    public List<Path> listTemplateFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(repositoryPath, "*" + fileExtension)) {
            stream.forEach(files::add);
        }
        return files;
    }

    /**
     * Check if template exists
     */
    public boolean templateExists(String templateId) {
        Path templateFile = repositoryPath.resolve(templateId + fileExtension);
        return Files.exists(templateFile);
    }

    /**
     * Create repository with default templates
     */
    public static FileTemplateRepository createWithDefaults(Path repositoryPath) throws IOException {
        FileTemplateRepository repository = new FileTemplateRepository(repositoryPath);

        // Add some additional useful templates
        repository.saveTemplate(createSpringConfigTemplate());
        repository.saveTemplate(createMavenPomTemplate());
        repository.saveTemplate(createWsdlTemplate());
        repository.saveTemplate(createRestResponseTemplate());

        return repository;
    }

    /**
     * Create Spring configuration template
     */
    private static SnippetTemplate createSpringConfigTemplate() {
        return new SnippetTemplate.Builder(
                "spring-config", "Spring Configuration",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "       xsi:schemaLocation=\"http://www.springframework.org/schema/beans\n" +
                        "       http://www.springframework.org/schema/beans/spring-beans.xsd\">\n" +
                        "\n" +
                        "    <bean id=\"${beanId:myBean}\" class=\"${beanClass:com.example.MyClass}\">\n" +
                        "        <property name=\"${property:propertyName}\" value=\"${value:propertyValue}\"/>\n" +
                        "        $0\n" +
                        "    </bean>\n" +
                        "\n" +
                        "</beans>"
        )
                .description("Spring Framework configuration file")
                .category(SnippetTemplate.TemplateCategory.CONFIGURATION)
                .tags("spring", "configuration", "beans")
                .priority(130)
                .contextSensitive(true)
                .author("Template Engine")
                .build();
    }

    /**
     * Create Maven POM template
     */
    private static SnippetTemplate createMavenPomTemplate() {
        return new SnippetTemplate.Builder(
                "maven-pom", "Maven POM",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                        "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" +
                        "         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                        "    <modelVersion>4.0.0</modelVersion>\n" +
                        "\n" +
                        "    <groupId>${groupId:com.example}</groupId>\n" +
                        "    <artifactId>${artifactId:my-project}</artifactId>\n" +
                        "    <version>${version:1.0.0}</version>\n" +
                        "    <packaging>${packaging:jar}</packaging>\n" +
                        "\n" +
                        "    <name>${name:My Project}</name>\n" +
                        "    <description>${description:Project description}</description>\n" +
                        "\n" +
                        "    <properties>\n" +
                        "        <maven.compiler.source>${javaVersion:17}</maven.compiler.source>\n" +
                        "        <maven.compiler.target>${javaVersion:17}</maven.compiler.target>\n" +
                        "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                        "    </properties>\n" +
                        "\n" +
                        "    <dependencies>\n" +
                        "        $0\n" +
                        "    </dependencies>\n" +
                        "</project>"
        )
                .description("Maven Project Object Model file")
                .category(SnippetTemplate.TemplateCategory.CONFIGURATION)
                .tags("maven", "pom", "build")
                .priority(125)
                .author("Template Engine")
                .build();
    }

    /**
     * Create WSDL template
     */
    private static SnippetTemplate createWsdlTemplate() {
        return new SnippetTemplate.Builder(
                "wsdl-service", "WSDL Service",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
                        "             xmlns:tns=\"${targetNamespace:http://example.com/service}\"\n" +
                        "             xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"\n" +
                        "             targetNamespace=\"${targetNamespace:http://example.com/service}\">\n" +
                        "\n" +
                        "    <types>\n" +
                        "        <schema xmlns=\"http://www.w3.org/2001/XMLSchema\"\n" +
                        "                targetNamespace=\"${targetNamespace:http://example.com/service}\">\n" +
                        "            $0\n" +
                        "        </schema>\n" +
                        "    </types>\n" +
                        "\n" +
                        "    <message name=\"${messageName:MyMessage}\">\n" +
                        "        <part name=\"${partName:part}\" type=\"${partType:xsd:string}\"/>\n" +
                        "    </message>\n" +
                        "\n" +
                        "    <portType name=\"${portType:MyPortType}\">\n" +
                        "        <operation name=\"${operation:myOperation}\">\n" +
                        "            <input message=\"tns:${messageName:MyMessage}\"/>\n" +
                        "            <output message=\"tns:${messageName:MyMessage}\"/>\n" +
                        "        </operation>\n" +
                        "    </portType>\n" +
                        "\n" +
                        "    <binding name=\"${binding:MyBinding}\" type=\"tns:${portType:MyPortType}\">\n" +
                        "        <soap:binding transport=\"http://schemas.xmlsoap.org/soap/http\"/>\n" +
                        "        <operation name=\"${operation:myOperation}\">\n" +
                        "            <soap:operation soapAction=\"${soapAction:}\"/>\n" +
                        "            <input><soap:body use=\"literal\"/></input>\n" +
                        "            <output><soap:body use=\"literal\"/></output>\n" +
                        "        </operation>\n" +
                        "    </binding>\n" +
                        "\n" +
                        "    <service name=\"${serviceName:MyService}\">\n" +
                        "        <port name=\"${portName:MyPort}\" binding=\"tns:${binding:MyBinding}\">\n" +
                        "            <soap:address location=\"${serviceUrl:http://localhost:8080/service}\"/>\n" +
                        "        </port>\n" +
                        "    </service>\n" +
                        "</definitions>"
        )
                .description("WSDL web service definition")
                .category(SnippetTemplate.TemplateCategory.WEB_SERVICES)
                .tags("wsdl", "webservice", "soap")
                .priority(120)
                .contextSensitive(true)
                .author("Template Engine")
                .build();
    }

    /**
     * Create REST response template
     */
    private static SnippetTemplate createRestResponseTemplate() {
        return new SnippetTemplate.Builder(
                "rest-response", "REST API Response",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<response>\n" +
                        "    <status>${status:200}</status>\n" +
                        "    <message>${message:Success}</message>\n" +
                        "    <timestamp>${timestamp:}</timestamp>\n" +
                        "    <data>\n" +
                        "        ${data:}\n" +
                        "        $0\n" +
                        "    </data>\n" +
                        "</response>"
        )
                .description("REST API XML response structure")
                .category(SnippetTemplate.TemplateCategory.WEB_SERVICES)
                .tags("rest", "api", "response", "json-alternative")
                .priority(115)
                .defaultValue("timestamp", "${datetime}")
                .author("Template Engine")
                .build();
    }
}