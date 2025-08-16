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
    application
    idea
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.ben-manes.versions") version "0.52.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

version = "1.0.0"
group = "org.fxt"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://repo.eclipse.org/content/groups/releases/") }
    maven { url = uri("https://repo.eclipse.org/content/repositories/lemminx-releases/") }
    maven { url = uri("https://maven.bestsolution.at/efxclipse-releases/") }
}

javafx {
    version = "23.0.2"
    modules("javafx.controls", "javafx.fxml", "javafx.web", "javafx.swing")
}

application {
    mainClass.set("org.fxt.freexmltoolkit.FxtGui")
}

dependencies {
    implementation("net.sf.saxon:Saxon-HE:12.8")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-bootstrapicons-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-win10-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-coreui-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-fontawesome-pack:12.4.0")
    implementation("io.github.mkpaz:atlantafx-base:2.1.0")
    implementation("org.fxmisc.richtext:richtextfx:0.11.5")
    implementation("org.apache.logging.log4j:log4j-api:2.24.1")
    implementation("org.apache.logging.log4j:log4j-core:2.24.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.1")
    implementation("org.apache.santuario:xmlsec:4.0.4")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    implementation("org.eclipse.lemminx:org.eclipse.lemminx:0.31.0")
    implementation("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.40.0.M2")
    implementation("org.controlsfx:controlsfx:11.2.2")
    implementation("org.apache.xmlgraphics:fop:2.11")
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    implementation("org.apache.xmlgraphics:batik-svggen:1.19")
    implementation("org.apache.xmlgraphics:batik-all:1.19")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")
    implementation("org.apache.poi:poi:5.4.1")
    implementation("org.apache.poi:poi-ooxml:5.4.1")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("commons-io:commons-io:2.20.0")
    implementation("org.apache.commons:commons-text:1.14.0")
    implementation("commons-validator:commons-validator:1.10.0")
    implementation("fr.brouillard.oss:cssfx:11.5.1")
    implementation("org.thymeleaf:thymeleaf:3.1.3.RELEASE")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("jakarta.activation:jakarta.activation-api:2.1.3")
    implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.3.4")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")
    implementation("com.github.mifmif:generex:1.0.2")
    implementation("com.github.curious-odd-man:rgxgen:3.0")
    testImplementation(platform("org.junit:junit-bom:5.13.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.testfx:testfx-core:4.0.17")
    testImplementation("org.testfx:testfx-junit5:4.0.17")
    testImplementation("org.testfx:openjfx-monocle:jdk-12.0.1+2")
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("--enable-preview")
    }

    withType<Test> {
        jvmArgs("--enable-preview")
    }

    withType<JavaExec> {
        jvmArgs("--enable-preview")
    }
}

tasks.jar {
    archiveBaseName.set("FreeXmlToolkit")
    archiveClassifier.set("")
    archiveVersion.set("")
    manifest {
        // Class-Path dynamisch aus runtimeClasspath erzeugen, referenziert lib/<jar>
        val cp = configurations.runtimeClasspath.get()
            .files
            .filter { it.name.endsWith(".jar") }
            .sortedBy { it.name }
            .joinToString(" ") { "lib/${it.name}" }

        attributes(
            "Main-Class" to "org.fxt.freexmltoolkit.FxtGui",
            "Implementation-Title" to "FreeXmlToolkit",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Karl Kauc",
            "Class-Path" to cp
        )
    }

    // Kein Fat-JAR bauen
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.SF")
}

tasks.withType<JavaExec> {
    mainClass.set("org.fxt.freexmltoolkit.FxtGui")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "16G"
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.register("createImageDirectory") {
    description = "Bereinigt und erstellt das image Verzeichnis"
    doLast {
        delete(layout.buildDirectory.dir("image"))
        mkdir(layout.buildDirectory.dir("image"))
    }
}

tasks.register("cleanDistDirectory") {
    description = "Bereinigt das dist Verzeichnis"
    doLast {
        delete(layout.buildDirectory.dir("dist"))
        mkdir(layout.buildDirectory.dir("dist"))
    }
}

tasks.register<Copy>("copyDistributionFiles") {
    dependsOn("jar", "createImageDirectory", "cleanDistDirectory")
    description = "Kopiert zusätzliche Dateien für die Distribution"

    // Zielverzeichnis fix als File
    into(layout.buildDirectory.dir("image").get().asFile)

    from(project.projectDir.resolve("release/examples"))
    from(project.projectDir.resolve("release/log4j2.xml"))
    from(project.projectDir.resolve("release/FreeXMLToolkit.properties"))

    // Haupt-JAR umbenannt ablegen (liegt im image-Root)
    from(tasks.jar) {
        rename { "FreeXmlToolkit.jar" }
    }

    // Laufzeit-Abhängigkeiten (inkl. JavaFX) unter image/lib
    from(configurations.runtimeClasspath) {
        into("lib")
    }
}

tasks.register<Zip>("packageDistribution") {
    dependsOn("createAllExecutables", "copyDistributionFiles")
    archiveFileName.set("FreeXMLToolkit.zip")
    destinationDirectory.set(layout.buildDirectory.get())
    from(layout.buildDirectory.dir("dist"))
    from(layout.buildDirectory.dir("image"))
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

tasks.register("createAllExecutables") {
    dependsOn("copyDistributionFiles")
    description = "Erstellt native Executables für alle unterstützten Betriebssysteme"
}

tasks.register<Exec>("createWindowsRuntimeImage") {
    description = "Erstellt ein benutzerdefiniertes Runtime-Image mit JavaFX-Modulen (Windows)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }

    val runtimeDir = layout.buildDirectory.dir("image/runtime").get().asFile
    val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
    val jdkJmods = File(javaHome, "jmods")
    val javafxJmodsDir = project.rootDir.resolve("jmods/javafx-jmods-21.0.8-win")

    doFirst {
        if (!jdkJmods.exists()) {
            throw GradleException("JDK jmods nicht gefunden: $jdkJmods. Setze JAVA_HOME korrekt.")
        }
        if (!javafxJmodsDir.exists()) {
            throw GradleException("JavaFX jmods Verzeichnis nicht gefunden: $javafxJmodsDir")
        }
        runtimeDir.deleteRecursively()
    }

    workingDir = layout.buildDirectory.get().asFile
    val modulePath = listOf(jdkJmods.absolutePath, javafxJmodsDir.absolutePath).joinToString(File.pathSeparator)
    val modules = listOf(
        "java.base",
        "java.logging",
        "java.xml",
        "java.desktop",
        "jdk.unsupported",
        "jdk.crypto.ec",
        "java.sql",
        "java.naming",
        "java.scripting",
        "javafx.controls",
        "javafx.fxml",
        "javafx.web",
        "javafx.swing"
    ).joinToString(",")

    commandLine(
        "jlink",
        "--module-path", modulePath,
        "--add-modules", modules,
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress", "2",
        "--output", runtimeDir.absolutePath
    )
}
tasks.register<Exec>("createMacRuntimeImage") {
    description = "Erstellt ein benutzerdefiniertes Runtime-Image mit JavaFX-Modulen (macOS)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

    val runtimeDir = layout.buildDirectory.dir("image/runtime").get().asFile
    val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
    val jdkJmods = File(javaHome, "jmods")
    val javafxJmodsDir = project.rootDir.resolve("jmods/javafx-jmods-21.0.8-osx")

    doFirst {
        if (!jdkJmods.exists()) {
            throw GradleException("JDK jmods nicht gefunden: $jdkJmods. Setze JAVA_HOME korrekt.")
        }
        if (!javafxJmodsDir.exists()) {
            throw GradleException("JavaFX jmods Verzeichnis nicht gefunden: $javafxJmodsDir")
        }
        runtimeDir.deleteRecursively()
    }

    workingDir = layout.buildDirectory.get().asFile
    val modulePath = listOf(jdkJmods.absolutePath, javafxJmodsDir.absolutePath).joinToString(File.pathSeparator)
    val modules = listOf(
        "java.base",
        "java.logging",
        "java.xml",
        "java.desktop",
        "jdk.unsupported",
        "jdk.crypto.ec",
        "java.sql",
        "java.naming",
        "java.scripting",
        "javafx.controls",
        "javafx.fxml",
        "javafx.web",
        "javafx.swing"
    ).joinToString(",")

    commandLine(
        "jlink",
        "--module-path", modulePath,
        "--add-modules", modules,
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress", "2",
        "--output", runtimeDir.absolutePath
    )
}
tasks.register<Exec>("createLinuxRuntimeImage") {
    description = "Erstellt ein benutzerdefiniertes Runtime-Image mit JavaFX-Modulen (Linux)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }

    val runtimeDir = layout.buildDirectory.dir("image/runtime").get().asFile
    val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
    val jdkJmods = File(javaHome, "jmods")
    val javafxJmodsDir = project.rootDir.resolve("jmods/javafx-jmods-21.0.8-linux")

    doFirst {
        if (!jdkJmods.exists()) {
            throw GradleException("JDK jmods nicht gefunden: $jdkJmods. Setze JAVA_HOME korrekt.")
        }
        if (!javafxJmodsDir.exists()) {
            throw GradleException("JavaFX jmods Verzeichnis nicht gefunden: $javafxJmodsDir")
        }
        runtimeDir.deleteRecursively()
    }

    workingDir = layout.buildDirectory.get().asFile
    val modulePath = listOf(jdkJmods.absolutePath, javafxJmodsDir.absolutePath).joinToString(File.pathSeparator)
    val modules = listOf(
        "java.base",
        "java.logging",
        "java.xml",
        "java.desktop",
        "jdk.unsupported",
        "jdk.crypto.ec",
        "java.sql",
        "java.naming",
        "java.scripting",
        "javafx.controls",
        "javafx.fxml",
        "javafx.web",
        "javafx.swing"
    ).joinToString(",")

    commandLine(
        "jlink",
        "--module-path", modulePath,
        "--add-modules", modules,
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress", "2",
        "--output", runtimeDir.absolutePath
    )
}
tasks.register<Exec>("createWindowsExecutable") {
    dependsOn("copyDistributionFiles", "createWindowsRuntimeImage")
    description = "Erstellt Windows Executable für benutzerbezogene Installation"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "exe",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.ico"),
        "--win-menu",
        "--win-shortcut",
        "--win-dir-chooser",
        "--win-per-user-install",
        "--win-menu-group", "FreeXmlToolkit",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview"
        // , "--win-console"
    )
}

tasks.register<Exec>("createMacOSExecutable") {
    dependsOn("copyDistributionFiles", "createMacRuntimeImage")
    description = "Erstellt macOS App Bundle für benutzerbezogene Installation"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "dmg",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.icns"),
        "--mac-package-name", "FreeXmlToolkit",
        "--mac-package-identifier", "org.fxt.freexmltoolkit",
        "--java-options", "-Djavafx.css.dump.lookup.errors=true",
        "--java-options", "--enable-preview",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath
    )
}

tasks.register<Exec>("createLinuxExecutable") {
    dependsOn("copyDistributionFiles", "createLinuxRuntimeImage")
    description = "Erstellt Linux AppImage für benutzerbezogene Installation"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "app-image",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.ico"),
        "--linux-app-category", "Development",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview"
    )
}

tasks.named("createAllExecutables") {
    dependsOn("createWindowsExecutable", "createMacOSExecutable", "createLinuxExecutable")
}