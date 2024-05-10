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
    id("dev.hydraulic.conveyor") version "1.9"
}

application {
    mainClass.set("org.fxt.freexmltoolkit.FxtGui")
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
    version = "21.0.1"
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
    implementation("net.sf.saxon:Saxon-HE:latest.release")

    // create sample XML files
    // implementation(files("lib/jlibs-xsd-3.0.1.jar"))
    // implementation(files("lib/jlibs-core-3.0.1.jar"))
    // implementation(files("lib/jlibs-xml-3.0.1.jar"))
    implementation("in.jlibs:jlibs-xsd:3.0.1")
    implementation("in.jlibs:jlibs-xml:3.0.1")
    // implementation("in.jlibs:jlibs-core:3.0.1")

    // XML Bindings
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")

    // Icons
    implementation("org.kordamp.ikonli:ikonli-javafx:latest.release")
    implementation("org.kordamp.ikonli:ikonli-bootstrapicons-pack:latest.release")
    implementation("org.kordamp.ikonli:ikonli-win10-pack:latest.release")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:latest.release")
    implementation("org.kordamp.ikonli:ikonli-coreui-pack:latest.release")
    // https://kordamp.org/ikonli/cheat-sheet-bootstrapicons.html

    // Richtext
    implementation("org.fxmisc.richtext:richtextfx:latest.release")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:latest.release")
    implementation("org.apache.logging.log4j:log4j-core:latest.release")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:latest.release")

    // XSD Parser
    implementation("com.github.xmlet:xsdParser:latest.release")

    //  xml signature
    implementation("org.apache.santuario:xmlsec:latest.release")

    // Lemminx
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")
    implementation("org.eclipse.lemminx:org.eclipse.lemminx:latest.release")
    implementation("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.34.0")

    // FOP
    implementation("org.apache.xmlgraphics:fop:latest.release")

    // SVG Graphic
    implementation("org.apache.xmlgraphics:batik-svggen:latest.release")
    implementation("org.apache.xmlgraphics:batik-all:1.17")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.17")


    // Create Office Documents
    implementation("org.apache.poi:poi:latest.release")
    implementation("org.apache.poi:poi-ooxml:latest.release")

    // Misc
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-io:commons-io:2.15.1")
    implementation("org.apache.commons:commons-text:latest.release")

    // CSS reload
    implementation("fr.brouillard.oss:cssfx:latest.release")

    // HTML Template XSD Documentation
    implementation("org.thymeleaf:thymeleaf:latest.release")

    // markdown renderer
    implementation("com.vladsch.flexmark:flexmark-all:latest.release")

    // Update
    implementation("jakarta.activation:jakarta.activation-api:latest.release")

    implementation("org.junit.jupiter:junit-jupiter:latest.release")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
}

tasks.jar {
    exclude("**/*.txt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxHeapSize = "16G"
}

tasks.register<Exec>("convey") {
    val dir = layout.buildDirectory.dir("packages")
    outputs.dir(dir)
    commandLine("conveyor", "make", "--output-dir", dir.get(), "site")
    dependsOn("jar", "writeConveyorConfig")
}
