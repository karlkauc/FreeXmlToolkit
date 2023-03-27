/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("edu.sc.seis.launch4j") version "2.5.4"
    id("com.github.ben-manes.versions") version "0.46.0"

    id("org.beryx.jlink") version "2.26.0"
}

application {
    // mainModule.set("org.fxt.freexmltoolkit")
    mainClass.set("org.fxt.freexmltoolkit.FxtGui")
}

group = "org.fxt"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
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
    version = "19.0.2.1"
    modules("javafx.controls", "javafx.fxml", "javafx.web")
}

run {
    // jvmArgs = [" -Xmx8g", "-Dfile.encoding=utf-8"]
}

dependencies {
    // XSLT
    implementation("net.sf.saxon:Saxon-HE:12.1")

    // Icons
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-bootstrapicons-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-win10-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.3.1")
    // https://kordamp.org/ikonli/cheat-sheet-bootstrapicons.html

    // Richtext
    implementation("org.fxmisc.richtext:richtextfx:0.11.0")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")

    // XSD Parser
    implementation("com.github.xmlet:xsdParser:1.2.4")

    //  xml signature
    implementation("org.apache.santuario:xmlsec:3.0.1")

    // Lemminx
    implementation("org.eclipse.lemminx:org.eclipse.lemminx:0.24.0")

    // FOP
    implementation("org.apache.xmlgraphics:fop:2.8")

    // SVG Graphic
    implementation("org.apache.xmlgraphics:batik-svggen:1.16")

    // Create Office Documents
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")

    // XSD Sample data generator
    // implementation("in.jlibs:jlibs-xsd:3.0.1")
    // implementation("javax.xml.bind:jaxb-api:2.3.1")
    // implementation("com.sun.xml.bind:jaxb-ri:2.3.3")

    // Misc
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.11.0")

    // CSS reload
    implementation("fr.brouillard.oss:cssfx:11.5.1")

    // HTML Template XSD Documentation
    implementation("org.thymeleaf:thymeleaf:3.1.1.RELEASE")

    // File Type detection
    // implementation("org.apache.tika:tika-core:2.7.0")
    // implementation("org.apache.tika:tika-parsers-standard-package:2.7.0")

    // Update
    implementation("org.update4j:update4j:1.5.9")
    implementation("jakarta.activation:jakarta.activation-api:2.1.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}


tasks.withType<edu.sc.seis.launch4j.tasks.DefaultLaunch4jTask> {
    outfile = "FreeXMLToolkit.exe"
    mainClassName = "org.fxt.freexmltoolkit.FxtGui" // SolvencyUI
    headerType = "gui" // gui / console
    icon = "${projectDir}/src/main/resources/img/logo.ico"
    maxHeapSize = 4096
    copyright = System.getProperty("user.name")

    // https://bell-sw.com/pages/libericajdk/
    bundledJrePath = "jdk"
    bundledJre64Bit = true
    jreMinVersion = "20"

    doLast {
        println("Copy resources...")
        copy {
            from(layout.buildDirectory.file("resources/log4j2.xml"))
            into(layout.buildDirectory.dir("launch4j"))
        }
        copy {
            from(layout.projectDirectory.dir("examples"))
            into(layout.buildDirectory.dir("launch4j/examples"))
        }
        println("Copy JDK...")
        copy {
            from(zipTree("jdk/jre-20-full.zip"))
            into(layout.buildDirectory.dir("launch4j/jdk"))
        }
    }
}

tasks.jar {
    exclude("**/*.txt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

jlink {
    launcher {
        name = "FreeXmlToolkit"
        forceMerge("log4j-api")
    }
}