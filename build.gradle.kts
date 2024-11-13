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
    id("com.github.ben-manes.versions") version "0.51.0"
    // id("dev.hydraulic.conveyor") version "1.11"
    id("edu.sc.seis.launch4j") version "3.0.6"
}

application {
    mainClass.set("org.fxt.freexmltoolkit.GuiTest")
    applicationName = "FreeXmlToolkit"
}

configurations.all {
    // resolutionStrategy.failOnVersionConflict()
    resolutionStrategy {
        cacheChangingModulesFor(15, "MINUTES")
        cacheDynamicVersionsFor(15, "MINUTES")
    }
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
    version = "22"
    modules("javafx.controls", "javafx.fxml", "javafx.web")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

run {
    // jvmArgs = [" -Xmx8g", "-Dfile.encoding=utf-8"]
}

dependencies {
    // XSLT
    implementation("net.sf.saxon:Saxon-HE:12.5")

    // create sample XML files
    implementation("in.jlibs:jlibs-xsd:3.0.1")
    implementation("in.jlibs:jlibs-xml:3.0.1")

    // XML Bindings
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")

    // Icons
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-bootstrapicons-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-win10-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-coreui-pack:12.3.1")
    // https://kordamp.org/ikonli/cheat-sheet-bootstrapicons.html

    // Richtext
    implementation("org.fxmisc.richtext:richtextfx:0.11.3")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:3.0.0-beta2")
    implementation("org.apache.logging.log4j:log4j-core:3.0.0-beta2")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:3.0.0-beta2")

    // XSD Parser
    implementation("com.github.xmlet:xsdParser:1.2.17")

    //  xml signature
    implementation("org.apache.santuario:xmlsec:4.0.3")

    // Lemminx
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.23.1")
    implementation("org.eclipse.lemminx:org.eclipse.lemminx:0.28.0")
    implementation("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.37.0.M2")

    // FOP
    implementation("org.apache.xmlgraphics:fop:2.10")

    // SVG Graphic
    implementation("org.apache.xmlgraphics:batik-svggen:1.18")
    implementation("org.apache.xmlgraphics:batik-all:1.18")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.18")


    // Create Office Documents
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Misc
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-io:commons-io:2.17.0")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("commons-validator:commons-validator:1.9.0")

    // CSS reload
    implementation("fr.brouillard.oss:cssfx:11.5.1")

    // HTML Template XSD Documentation
    implementation("org.thymeleaf:thymeleaf:3.1.2.RELEASE")

    // markdown renderer
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")

    // Update
    implementation("jakarta.activation:jakarta.activation-api:2.1.3")

    implementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

tasks.jar {
    exclude("**/*.txt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxHeapSize = "16G"
}

/*
tasks.register<Exec>("convey") {
    val dir = layout.buildDirectory.dir("packages")
    outputs.dir(dir)
    commandLine("conveyor", "make", "--output-dir", dir.get(), "site")
    dependsOn("jar", "writeConveyorConfig")
}
*/
