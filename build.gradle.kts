/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

plugins {
    java
    idea
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.ben-manes.versions") version "0.52.0"
    id("edu.sc.seis.launch4j") version "3.0.6"
    id("org.beryx.jlink") version "3.0.1"
}

application {
    mainClass.set("org.fxt.freexmltoolkit.FxtGui")
    // mainClass.set("org.fxt.freexmltoolkit.GuiTest")
    applicationName = "FreeXmlToolkit"
    applicationDefaultJvmArgs = listOf("-Djavafx.css.dump.lookup.errors=true", "--enable-native-access=javafx.graphics")
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(15, "MINUTES")
        cacheDynamicVersionsFor(15, "MINUTES")
    }
    exclude(group = "xml-apis", module = "xml-apis")
}

group = "org.fxt"
version = "1.0.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("https://repo.eclipse.org/content/groups/releases/")
    }
    maven {
        url = uri("https://repo.eclipse.org/content/repositories/lemminx-releases/")
    }
    maven {
        url = uri("https://maven.bestsolution.at/efxclipse-releases/")
    }
}

javafx {
    version = "24.0.1"
    modules("javafx.controls", "javafx.fxml", "javafx.web", "javafx.swing")
}

dependencies {
    // XSLT
    implementation("net.sf.saxon:Saxon-HE:12.7")

    // XML Bindings
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")

    // Serialization
    implementation("com.google.code.gson:gson:2.13.1")

    // Icons
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-bootstrapicons-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-win10-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-coreui-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-fontawesome-pack:12.4.0")
    // https://kordamp.org/ikonli/cheat-sheet-bootstrapicons.html

    // Style
    implementation("io.github.mkpaz:atlantafx-base:2.0.1")

    // Richtext
    implementation("org.fxmisc.richtext:richtextfx:0.11.5")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:3.0.0-beta2")
    implementation("org.apache.logging.log4j:log4j-core:3.0.0-beta2")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:3.0.0-beta2")

    //  Certificate & XML Signature
    implementation("org.apache.santuario:xmlsec:4.0.4")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")

    // Lemminx
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    implementation("org.eclipse.lemminx:org.eclipse.lemminx:0.31.0")
    implementation("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.40.0.M0")
    implementation("org.controlsfx:controlsfx:11.2.2")

    // FOP & PDF Anzeige
    implementation("org.apache.xmlgraphics:fop:2.11")
    implementation("org.apache.pdfbox:pdfbox:3.0.5")

    // SVG Graphic
    implementation("org.apache.xmlgraphics:batik-svggen:1.19")
    implementation("org.apache.xmlgraphics:batik-all:1.19")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")

    // Create Office Documents
    implementation("org.apache.poi:poi:5.4.1")
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    // Misc
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-io:commons-io:2.19.0")
    implementation("org.apache.commons:commons-text:1.13.1")
    implementation("commons-validator:commons-validator:1.9.0")

    // CSS reload
    implementation("fr.brouillard.oss:cssfx:11.5.1")

    // HTML Template XSD Documentation
    implementation("org.thymeleaf:thymeleaf:3.1.3.RELEASE")

    // markdown renderer
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")

    // Update
    implementation("jakarta.activation:jakarta.activation-api:2.1.3")

    // debugging
    // implementation("io.github.palexdev:scenicview:17.0.2")

    // http connection with NTLM Proxy Auth
    implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.3.4")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")

    // pattern for example data
    implementation("com.github.mifmif:generex:1.0.2")
    implementation("com.github.curious-odd-man:rgxgen:3.0")

    testImplementation(platform("org.junit:junit-bom:5.13.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

tasks.jar {
    exclude("**/*.txt")
}


tasks.test {
    useJUnitPlatform()
    maxHeapSize = "16G"
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<edu.sc.seis.launch4j.tasks.DefaultLaunch4jTask> {
    dependsOn("clean")

    outfile = "FreeXMLToolkit.exe"
    mainClassName = "org.fxt.freexmltoolkit.FxtGui"
    headerType = "gui" // gui / console
    icon = "${projectDir}/release/logo.ico"
    // maxHeapSize = 2048
    copyright = System.getProperty("user.name")

    outputDir = "FreeXMLToolkit"

    bundledJrePath = "jre"
    requires64Bit = true
    jreMinVersion = "23"

    doLast {
        println("Copy JDK...")
        copy {
            from(zipTree("release/jdk/jre-23-full.zip"))
            into(layout.buildDirectory.dir("/FreeXMLToolkit/jre"))
        }
        println("Copy additional files...")
        copy {
            from(projectDir.path + "/release/examples")
            into(layout.buildDirectory.dir("/FreeXMLToolkit/examples"))
        }
        copy {
            from(projectDir.path + "/release/log4j2.xml")
            from(projectDir.path + "/release/FreeXMLToolkit.properties")
            into(layout.buildDirectory.dir("FreeXMLToolkit"))
        }
    }
}

tasks.register<Zip>("packageDistribution") {
    dependsOn("createAllExecutables")

    archiveFileName.set("FreeXMLToolkit.zip")
    destinationDirectory.set(layout.buildDirectory.get())

    val tree: ConfigurableFileTree = fileTree(layout.buildDirectory.get())
    tree.include("FreeXMLToolkit/**")
    from(tree)
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

jlink {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "FreeXmlToolkit"
    }
    jarExclude("", "**/LICENSE*", "**/NOTICE*", "**/README*")

    jpackage {
        // Allgemeine Optionen für alle Betriebssysteme
        vendor = "Karl Kauc"
        appVersion = "1.0.0"

        // Betriebssystem-spezifische Konfiguration
        if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            installerType = "exe"
            installerOptions
            installerOptions = listOf("--win-menu", "--win-shortcut")
            // Installiert standardmäßig in das Benutzerverzeichnis %LOCALAPPDATA%\<Anwendungsname>
        } else if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            installerType = "dmg"
            // Installiert in /Applications, was auf macOS oft ohne Admin-Rechte möglich ist
            // Für eine rein benutzerbezogene Installation:
            // installDir = '~/Applications'
        } else if (org.gradle.internal.os.OperatingSystem.current().isLinux) {
            installerType = "deb" // oder 'rpm'
            // Installiert standardmäßig in /opt, was Admin-Rechte erfordert.
            // Daher setzen wir das Installationsverzeichnis auf ein Benutzerverzeichnis:
            // installDir = "home/${System.getProperty('user.name')}/.local/share/IhreAnwendung"
        }
    }
}
