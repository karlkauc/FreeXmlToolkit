# Third-Party Libraries and Licenses

FreeXmlToolkit uses several third-party libraries. The following is a comprehensive list of these libraries and the licenses under which they are provided.

## Software Bill of Materials (SBOM)

From version 2.11 on, every [GitHub release](https://github.com/karlkauc/FreeXmlToolkit/releases) carries a
machine-readable SBOM in the [CycloneDX](https://cyclonedx.org/) 1.6 format:
`FreeXmlToolkit-<version>-sbom.cdx.json` and `FreeXmlToolkit-<version>-sbom.cdx.xml`. It lists every library
the application ships (the runtime classpath, not test or build tools) with its version, license, package URL
and hashes, and it is covered by the release's `SHA256SUMS.txt`. Security and compliance tools such as
Dependency-Track, Trivy or Grype read it directly.

To create it from the sources: `./gradlew cyclonedxDirectBom` writes `build/reports/cyclonedx-direct/bom.json`
and `bom.xml`.

## Core Dependencies

| Library | Version | License |
|---------|---------|---------|
| **Saxon-HE** (net.sf.saxon:Saxon-HE) | 12.10 | [Mozilla Public License 2.0](https://www.mozilla.org/en-US/MPL/2.0/) |
| **XML Calabash** (com.xmlcalabash:xmlcalabash) | 3.0.54 | [MIT License](https://opensource.org/licenses/MIT) |
| **Jakarta XML Bind API** (jakarta.xml.bind:jakarta.xml.bind-api) | 4.0.5 | [Eclipse Distribution License 1.0](https://www.eclipse.org/org/documents/edl-v10.php) |
| **Gson** (com.google.code.gson:gson) | 2.14.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

## JSON Processing

| Library | Version | License |
|---------|---------|---------|
| **JSONPath** (com.jayway.jsonpath:json-path) | 3.0.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **JSON Schema Validator** (com.networknt:json-schema-validator) | 3.0.8 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **JSON5 Java** (de.marhali:json5-java) | 3.0.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

## Schematron Validation

| Library | Version | License |
|---------|---------|---------|
| **ph-schematron** (com.helger.schematron:ph-schematron-pure / -xslt / -schxslt / -api) | 10.2.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **JAXB Runtime** (org.glassfish.jaxb:jaxb-runtime, SVRL marshalling) | 4.0.9 | [Eclipse Distribution License 1.0](https://www.eclipse.org/org/documents/edl-v10.php) |
| **ph-commons** (com.helger.commons:ph-commons / ph-xml / ph-io) | 12.x | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

## XML Validation (XSD 1.1 Support)

| Library | Version | License |
|---------|---------|---------|
| **Apache Xerces** (org.exist-db.thirdparty.xerces:xercesImpl) | 2.12.2 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **XPath 2.0 Processor** (org.exist-db.thirdparty.org.eclipse.wst.xml:xpath2) | 1.2.0 | [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/) |
| **Java CUP Runtime** (edu.princeton.cup:java-cup) | 10k | [CUP License](http://www2.cs.tum.edu/projects/cup/) |

## UI Framework and Components

| Library | Version | License |
|---------|---------|---------|
| **JavaFX** (Liberica Full JDK) | 25 | [GPL v2 with Classpath Exception](https://openjdk.java.net/legal/gplv2+ce.html) |
| **RichTextFX** (org.fxmisc.richtext:richtextfx) | 0.11.7 | [BSD-2-Clause License](https://opensource.org/licenses/BSD-2-Clause) |
| **ControlsFX** (org.controlsfx:controlsfx) | 11.2.6 | [BSD 3-Clause License](https://opensource.org/licenses/BSD-3-Clause) |
| **CSSFX** (fr.brouillard.oss:cssfx) | 11.5.1 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **NSMenuFX** (de.jangassen:nsmenufx, macOS menu bar) | 3.1.0 | [BSD 3-Clause License](https://opensource.org/licenses/BSD-3-Clause) |

## Icons and Graphics

Icons are rendered by the in-house `IconifyIcon` control from bundled
[Iconify](https://iconify.design/) SVG icon data (no external icon library dependency):

| Icon Data | Version | License |
|---------|---------|---------|
| **Bootstrap Icons** (bundled Iconify SVG data, `icons/iconify/bi.json`) | - | [MIT License](https://github.com/twbs/icons/blob/main/LICENSE) |

## PDF and Document Processing

| Library | Version | License |
|---------|---------|---------|
| **Apache FOP** (org.apache.xmlgraphics:fop) | 2.12 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache PDFBox** (org.apache.pdfbox:pdfbox) | 3.0.8 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache POI** (org.apache.poi:poi) | 6.0.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache POI OOXML** (org.apache.poi:poi-ooxml) | 6.0.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

## SVG and Graphics Processing

| Library | Version | License |
|---------|---------|---------|
| **Apache Batik SVG Generator** (org.apache.xmlgraphics:batik-svggen) | 1.20 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache Batik All** (org.apache.xmlgraphics:batik-all) | 1.20 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache Batik Transcoder** (org.apache.xmlgraphics:batik-transcoder) | 1.20 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

## Security and Cryptography

| Library | Version | License |
|---------|---------|---------|
| **Apache Santuario** (org.apache.santuario:xmlsec) | 4.0.4 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Bouncy Castle** (org.bouncycastle:bcpkix-jdk18on) | 1.86 | [MIT License](https://www.bouncycastle.org/licence.html) |

## Utilities and Commons

| Library | Version | License |
|---------|---------|---------|
| **Apache Commons Lang3** (org.apache.commons:commons-lang3) | 3.21.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache Commons IO** (commons-io:commons-io) | 2.22.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache HttpClient 5** (org.apache.httpcomponents.client5:httpclient5) | 5.6.5 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache HttpCore 5 H2** (org.apache.httpcomponents.core5:httpcore5-h2) | 5.4.4 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **java-diff-utils** (io.github.java-diff-utils:java-diff-utils) | 4.18 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache Commons Text** (org.apache.commons:commons-text) | 1.15.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Apache Commons Validator** (commons-validator:commons-validator) | 1.11.0 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **picocli** (info.picocli:picocli), for `fxt-cli` | 4.7.7 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

## Templating and Markdown

| Library | Version | License |
|---------|---------|---------|
| **Thymeleaf** (org.thymeleaf:thymeleaf) | 3.1.5.RELEASE | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **FlexMark** (com.vladsch.flexmark:flexmark-all) | 0.64.8 | [BSD 2-Clause License](https://opensource.org/licenses/BSD-2-Clause) |

## Regular Expressions and Generation

| Library | Version | License |
|---------|---------|---------|
| **Generex** (com.github.mifmif:generex) | 1.0.2 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **RgxGen** (com.github.curious-odd-man:rgxgen) | 3.1 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

## Logging

| Library | Version | License |
|---------|---------|---------|
| **Log4j API** (org.apache.logging.log4j:log4j-api) | 2.26.1 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Log4j Core** (org.apache.logging.log4j:log4j-core) | 2.26.1 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Log4j SLF4J2 Implementation** (org.apache.logging.log4j:log4j-slf4j2-impl) | 2.26.1 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

## Additional APIs

| Library | Version | License |
|---------|---------|---------|
| **Jakarta Activation API** (jakarta.activation:jakarta.activation-api) | 2.1.4 | [Eclipse Distribution License 1.0](https://www.eclipse.org/org/documents/edl-v10.php) |

## Testing Dependencies

| Library | Version | License |
|---------|---------|---------|
| **JUnit Jupiter** (org.junit.jupiter:junit-jupiter, via org.junit:junit-bom) | 6.1.3 | [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/) |
| **Mockito** (org.mockito:mockito-core) | 5.24.0 | [MIT License](https://opensource.org/licenses/MIT) |
| **Mockito JUnit Jupiter** (org.mockito:mockito-junit-jupiter) | 5.24.0 | [MIT License](https://opensource.org/licenses/MIT) |
| **Hamcrest** (org.hamcrest:hamcrest) | 3.0 | [BSD 3-Clause License](https://opensource.org/licenses/BSD-3-Clause) |
| **TestFX Core** (org.testfx:testfx-core) | 4.0.18 | [EUPL-1.1](https://joinup.ec.europa.eu/software/page/eupl) |
| **TestFX JUnit5** (org.testfx:testfx-junit5) | 4.0.18 | [EUPL-1.1](https://joinup.ec.europa.eu/software/page/eupl) |
| **OpenJFX Monocle** (org.testfx:openjfx-monocle) | 21.0.2 | [GPL v2 with Classpath Exception](https://openjdk.java.net/legal/gplv2+ce.html) |

---

## License Summary

The majority of libraries used in FreeXmlToolkit are licensed under permissive open-source licenses:

| License Type | Count |
|--------------|-------|
| Apache License 2.0 | 29 |
| MIT License | 5 |
| BSD (2/3-Clause) | 5 |
| Eclipse Public / Distribution License | 5 |
| EUPL-1.1 | 2 |
| GPL v2 with Classpath Exception | 2 |
| Mozilla Public License 2.0 | 1 |
| CUP License | 1 |

All licenses are compatible with the Apache License 2.0 under which FreeXmlToolkit is distributed.

