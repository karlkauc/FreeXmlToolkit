/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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
    id("edu.sc.seis.launch4j") version "3.0.5"
    id("com.github.ben-manes.versions") version "0.50.0"

    id("org.beryx.jlink") version "3.0.1"
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
    version = "20.0.1"
    modules("javafx.controls", "javafx.fxml", "javafx.web")
}

run {
    // jvmArgs = [" -Xmx8g", "-Dfile.encoding=utf-8"]
}

dependencies {
    // XSLT
    implementation("net.sf.saxon:Saxon-HE:latest.release")

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
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:latest.release")

    // XSD Parser
    implementation("com.github.xmlet:xsdParser:latest.release")

    //  xml signature
    implementation("org.apache.santuario:xmlsec:latest.release")

    // Lemminx
    implementation("org.eclipse.lemminx:org.eclipse.lemminx:latest.release")

    // FOP
    implementation("org.apache.xmlgraphics:fop:latest.release")

    // SVG Graphic
    implementation("org.apache.xmlgraphics:batik-svggen:latest.release")

    // Create Office Documents
    implementation("org.apache.poi:poi:latest.release")
    implementation("org.apache.poi:poi-ooxml:latest.release")

    // Misc
    implementation("org.apache.commons:commons-lang3:latest.release")
    implementation("commons-io:commons-io:latest.release")
    implementation("org.apache.commons:commons-text:latest.release")

    // CSS reload
    implementation("fr.brouillard.oss:cssfx:latest.release")

    // HTML Template XSD Documentation
    implementation("org.thymeleaf:thymeleaf:latest.release")

    // File Type detection
    // implementation("org.apache.tika:tika-core:2.7.0")
    // implementation("org.apache.tika:tika-parsers-standard-package:2.7.0")

    // html manipulation
    // wird im moment gar nicht gebraucht
    // implementation("org.jsoup:jsoup:latest.release")

    // markdown renderer
    implementation("com.vladsch.flexmark:flexmark-all:latest.release")


    // Update
    implementation("org.update4j:update4j:latest.release")
    implementation("jakarta.activation:jakarta.activation-api:latest.release")

    implementation("org.junit.jupiter:junit-jupiter:latest.release")

    // batik for svg graphic
    implementation("org.apache.xmlgraphics:batik-all:1.17")

    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
}


tasks.withType<edu.sc.seis.launch4j.tasks.DefaultLaunch4jTask> {
    outfile = "FreeXMLToolkit.exe"
    mainClassName = "org.fxt.freexmltoolkit.FxtGui" // SolvencyUI
    headerType = "gui" // gui / console
    icon = "${projectDir}/src/main/resources/img/logo.ico"
    maxHeapSize = 4096
    maxHeapPercent = 80
    copyright = System.getProperty("user.name")

    bundledJrePath = "jdk"
    // bundledJre64Bit = true
    requires64Bit = true
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