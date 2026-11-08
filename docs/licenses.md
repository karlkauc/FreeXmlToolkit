# Third-Party Libraries and Licenses

FreeXmlToolkit itself is licensed under the [Apache License 2.0](https://github.com/karlkauc/FreeXmlToolkit/blob/main/LICENSE.md).
It is built on third-party libraries and ships with its own Java runtime. The tables below list the libraries the
project uses directly; the [SBOM](#software-bill-of-materials-sbom) of each release lists every library in the
installation, including the ones these pull in. Two parts need more than a table row: the
[Xerces build for XSD 1.1](#xml-validation-xsd-11-support) and the
[bundled Java runtime with JavaFX](#bundled-java-runtime-and-javafx). This page explains the license
situation; it is not legal advice.

## License Texts in the Installation

Every library jar carries its own license and notice files (`META-INF/`), in
the installation's `app` folder. The Java runtime keeps its license texts in a `legal` folder, one subfolder per
module:

| Installation | Java runtime license texts |
|---|---|
| Windows | `runtime\legal\` in the installation folder |
| macOS | `FreeXmlToolkit.app/Contents/runtime/Contents/Home/legal/` |
| Linux `.deb` / `.rpm` | `/opt/freexmltoolkit/lib/runtime/legal/` |
| Linux ZIP | `lib/runtime/legal/` in the extracted folder |

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
| **Apache Xerces**, XSD 1.1 build (org.exist-db.thirdparty.xerces:xercesImpl, classifier `xml-schema-1.1`) | 2.12.2 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **Eclipse WST XPath 2.0 Processor** "PsychoPath" (org.exist-db.thirdparty.org.eclipse.wst.xml:xpath2) | 1.2.0 | [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html) |
| **Java CUP Runtime** (edu.princeton.cup:java-cup) | 10k | [CUP License](http://www2.cs.tum.edu/projects/cup/) (permissive, similar to the Standard ML of New Jersey license) |
| **XML Commons External** (xml-apis:xml-apis, xml-apis:xml-apis-ext) | 1.4.01, 1.3.04 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0); contains the SAX (public domain) and W3C DOM interfaces ([W3C Software License](https://www.w3.org/copyright/software-license/)) |

**Why a Xerces from eXist-db.** XSD 1.1 validation (assertions, conditional types, open content) comes from
Apache Xerces-J. The Apache project builds Xerces-J 2.12.2 in two variants: the XSD 1.0 build, which is the one on
Maven Central as `xerces:xercesImpl`, and the XSD 1.1 build, which Apache only offers as a download on
[xerces.apache.org](https://xerces.apache.org/mirrors.cgi). The [eXist-db](https://exist-db.org) project publishes
the XSD 1.1 build to Maven Central under its own group id `org.exist-db.thirdparty.xerces`, so that build tools can
use it. FreeXmlToolkit uses that artifact:

- It is **Apache code under the Apache License 2.0**, not an eXist-db product under eXist-db's license (LGPL). The
  jar reports itself as `Xerces-J 2.12.2-xml-schema-1.1` from the Apache Software Foundation and contains Apache's
  `LICENSE` and `NOTICE`. "Fork" only means a different place of publication.
- The matching **sources** are on Maven Central next to the jar (`xercesImpl-2.12.2-xml-schema-1.1-sources.jar`);
  the Apache sources are the release tag `Xerces-J_2_12_2` in the Apache Xerces repository.
- For assertions, Xerces needs the **XPath 2.0 processor** from the Eclipse Web Tools Platform and the **CUP**
  parser runtime. eXist-db publishes both in the same way. The XPath 2.0 processor is under the Eclipse Public
  License **1.0** (a weak copyleft for the library itself, without effect on FreeXmlToolkit's code); it is
  distributed unchanged, and its sources are on Maven Central (`xpath2-1.2.0-sources.jar`).
- Xerces depends on the old **XML Commons** interface jars (`xml-apis`), and the Xerces jar contains copies of
  them. They are inert on Java 25: the classes of the `javax.xml`, `org.w3c.dom` and `org.xml.sax` packages always
  come from the Java runtime's own `java.xml` module.
- The XSD 1.1 build is used for XSD 1.0 schemas as well unless Settings → Schemas & Validation → **XML parser** is set to Saxon; XSD 1.1 schemas always use it.

## UI Framework and Components

| Library | Version | License |
|---------|---------|---------|
| **Java runtime with JavaFX** (BellSoft Liberica Full JDK, see [below](#bundled-java-runtime-and-javafx)) | 25 | [GPL v2 with Classpath Exception](https://openjdk.org/legal/gplv2+ce.html) |
| **RichTextFX** (org.fxmisc.richtext:richtextfx) | 0.11.7 | [BSD-2-Clause License](https://opensource.org/licenses/BSD-2-Clause) |
| **ControlsFX** (org.controlsfx:controlsfx) | 11.2.6 | [BSD 3-Clause License](https://opensource.org/licenses/BSD-3-Clause) |
| **CSSFX** (fr.brouillard.oss:cssfx) | 11.5.1 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| **NSMenuFX** (de.jangassen:nsmenufx, macOS menu bar) | 3.1.0 | [BSD 3-Clause License](https://opensource.org/licenses/BSD-3-Clause) |

## Bundled Java Runtime and JavaFX

FreeXmlToolkit does not need an installed Java. Each installer and ZIP contains a Java 25 runtime, made with
`jlink` from the [BellSoft Liberica Full JDK](https://bell-sw.com/libericajdk/). "Full" means the JDK includes
[OpenJFX](https://openjfx.io) (JavaFX), the UI toolkit; FreeXmlToolkit does not download or ship JavaFX jars of
its own.

- **What is in it.** Only the modules the application needs (`java.base`, `java.desktop`, `java.xml`,
  `java.net.http`, …, and `javafx.base`, `javafx.controls`, `javafx.fxml`, `javafx.graphics`, `javafx.media`,
  `javafx.swing`, `javafx.web`). The modules are not modified; `jlink` only leaves out the other modules, debug
  information and the command line tools. The `release` file in the runtime folder names the exact version.
- **License.** OpenJDK and OpenJFX are under the **GNU General Public License version 2 with the Classpath
  Exception**. The Classpath Exception allows an application to run on and link with the runtime under its own
  license; FreeXmlToolkit's code stays under the Apache License 2.0, and the runtime stays under GPLv2+CE.
  Some native parts carry their own licenses: WebKit in `javafx.web` (used for the HTML preview and the help
  pages) and GStreamer/GLib in `javafx.media` are mostly under the LGPL 2.1, and libraries such as ICU, libxml2,
  libxslt, FreeType, HarfBuzz, libjpeg and libpng under permissive licenses. Every one of them is listed with its
  full license text in the module's `legal` subfolder (see [License Texts in the Installation](#license-texts-in-the-installation)).
- **Sources.** The runtime is the unmodified Liberica build. Its sources are published by BellSoft
  ([Liberica source packages](https://bell-sw.com/pages/downloads/), [github.com/bell-sw](https://github.com/bell-sw))
  and by the OpenJDK projects ([openjdk/jdk](https://github.com/openjdk/jdk), [openjdk/jfx](https://github.com/openjdk/jfx)).
- **Updates.** The runtime is replaced with every FreeXmlToolkit release, which picks up the latest Liberica 25
  update at build time. Java security fixes therefore arrive with FreeXmlToolkit updates, not through a separately
  installed Java.
- **The TestFX/Monocle** jar in the test dependencies below is only used to run the tests headless; it is not part
  of the installation.

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
| Apache License 2.0 | 30 |
| MIT License | 5 |
| BSD (2/3-Clause) | 5 |
| Eclipse Public / Distribution License (1.0 / 2.0) | 5 |
| EUPL-1.1 | 2 |
| GPL v2 with Classpath Exception | 2 |
| Mozilla Public License 2.0 | 1 |
| CUP License | 1 |

The libraries on the application's class path are under permissive or weak-copyleft licenses (Apache, MIT, BSD,
MPL 2.0, EPL, EDL) that allow their use in an application under the Apache License 2.0. The GPL-with-Classpath-
Exception parts are the Java runtime and JavaFX, which the application runs on (see
[Bundled Java Runtime and JavaFX](#bundled-java-runtime-and-javafx)); EUPL and GPLv2+CE in the testing section
concern test tools that are not distributed.

