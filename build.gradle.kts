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

/*
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}
 */

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
    version = "21.0.8"
    modules("javafx.controls", "javafx.fxml", "javafx.web", "javafx.swing")
}

application {
    mainClass.set("org.fxt.freexmltoolkit.FxtGui")
    // mainClass.set("org.fxt.freexmltoolkit.demo.IntelliSenseDemo")
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

    implementation("com.helger.schematron:ph-schematron-parent-pom:9.0.0")
    implementation("com.helger.commons:ph-io:12.0.0")
    // implementation("com.helger.commons:ph-commons:12.0.0")
    // implementation("com.helger.commons:ph-xml:12.0.0")
    implementation("com.helger.schematron:ph-schematron-api:9.0.0")
    implementation("com.helger.schematron:ph-schematron-xslt:9.0.0")
    implementation("com.helger.schematron:ph-schematron-pure:9.0.0")
    implementation("com.helger.schematron:ph-schematron-schxslt:9.0.0")
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
            "Main-Class" to application.mainClass,
            "Implementation-Title" to "FreeXmlToolkit",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Karl Kauc",
            "Class-Path" to cp
        )
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.SF")
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
            // throw GradleException("JDK jmods nicht gefunden: $jdkJmods. Setze JAVA_HOME korrekt.")
        }
        if (!javafxJmodsDir.exists()) {
            // throw GradleException("JavaFX jmods Verzeichnis nicht gefunden: $javafxJmodsDir")
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
        "--compress", "zip-6",
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
            // throw GradleException("JDK jmods nicht gefunden: $jdkJmods. Setze JAVA_HOME korrekt.")
        }
        if (!javafxJmodsDir.exists()) {
            // throw GradleException("JavaFX jmods Verzeichnis nicht gefunden: $javafxJmodsDir")
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
        "--compress", "zip-6",
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
            // throw GradleException("JDK jmods nicht gefunden: $jdkJmods. Setze JAVA_HOME korrekt.")
        }
        if (!javafxJmodsDir.exists()) {
            // throw GradleException("JavaFX jmods Verzeichnis nicht gefunden: $javafxJmodsDir")
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
        "--compress", "zip-6",
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
        // "--win-banner-image", "${projectDir}/release/win-banner.png",
        // "--win-dialog-image", "${projectDir}/release/win-dialog.png",
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

tasks.register<Exec>("createLinuxDeb") {
    dependsOn("copyDistributionFiles", "createLinuxRuntimeImage")
    description = "Erstellt ein .deb-Paket für Debian-basierte Linux-Distributionen"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "deb",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.ico"),
        "--linux-package-name", "freexmltoolkit",
        "--linux-deb-maintainer", "karl.kauc@gmail.com",
        "--linux-app-category", "Development",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview"
    )
}

tasks.register<Exec>("createLinuxRpm") {
    dependsOn("copyDistributionFiles", "createLinuxRuntimeImage")
    description = "Erstellt ein .rpm-Paket für Red Hat-basierte Linux-Distributionen"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "rpm",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.ico"),
        "--linux-package-name", "freexmltoolkit",
        "--linux-rpm-license-type", "Apache 2.0",
        "--linux-app-category", "Development",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview"
    )
}

tasks.register<Exec>("createLinuxAppImage") {
    dependsOn("copyDistributionFiles", "createLinuxRuntimeImage")
    description = "Erstellt ein AppImage für Linux-Distributionen"
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

tasks.register<Exec>("createLinuxTar") {
    dependsOn("copyDistributionFiles", "createLinuxRuntimeImage")
    description = "Erstellt ein tar.gz Archiv für Linux"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "tgz",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.ico"),
        "--linux-app-category", "Development",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview"
    )
}

tasks.register<Exec>("createMacOSPkg") {
    dependsOn("copyDistributionFiles", "createMacRuntimeImage")
    description = "Erstellt ein .pkg Installer für macOS"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "pkg",
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

tasks.register<Exec>("createMacOSAppImage") {
    dependsOn("copyDistributionFiles", "createMacRuntimeImage")
    description = "Erstellt ein App Bundle für macOS (ohne Installer)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
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
        "--icon", project.projectDir.resolve("release/logo.icns"),
        "--mac-package-name", "FreeXmlToolkit",
        "--mac-package-identifier", "org.fxt.freexmltoolkit",
        "--java-options", "-Djavafx.css.dump.lookup.errors=true",
        "--java-options", "--enable-preview",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath
    )
}

tasks.register<Exec>("createWindowsMsi") {
    dependsOn("copyDistributionFiles", "createWindowsRuntimeImage")
    description = "Erstellt Windows MSI Installer für systemweite Installation"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "msi",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.ico"),
        "--win-menu",
        "--win-shortcut",
        "--win-dir-chooser",
        "--win-menu-group", "FreeXmlToolkit",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview"
    )
}

tasks.register<Exec>("createWindowsAppImage") {
    dependsOn("copyDistributionFiles", "createWindowsRuntimeImage")
    description = "Erstellt Windows App Image (ohne Installer)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
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
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview"
    )
}

tasks.register<Zip>("zipWindowsAppImage") {
    description = "Zippt das erstellte Windows App Image und löscht das Originalverzeichnis."
    dependsOn(tasks.named("createWindowsAppImage"))

    val sourceDirProvider = layout.buildDirectory.dir("dist/FreeXmlToolkit")
    from(sourceDirProvider)
    archiveFileName.set("FreeXmlToolkit-windows-app-image-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    // Dieser Task sollte nur ausgeführt werden, wenn das Quellverzeichnis aus dem vorherigen Task existiert.
    onlyIf { sourceDirProvider.get().asFile.exists() }

    // Nach dem Zippen das Originalverzeichnis löschen
    doLast {
        val sourceDir = sourceDirProvider.get().asFile
        logger.lifecycle("Lösche originales AppImage-Verzeichnis nach dem Zippen: ${sourceDir.path}")
        sourceDir.deleteRecursively()
    }
}

tasks.named("createAllExecutables") {
    dependsOn(
        "createWindowsExecutable",
        "createWindowsMsi",
        "zipWindowsAppImage",
        "createMacOSExecutable",
        "createMacOSPkg",
        "createMacOSAppImage",
        "createLinuxDeb",
        "createLinuxRpm",
        "createLinuxAppImage",
        "createLinuxTar"
    )
}

// Convenience tasks for platform-specific packages
tasks.register("createWindowsPackages") {
    description = "Erstellt alle Windows-Pakete (exe, msi, app-image)"
    dependsOn("createWindowsExecutable", "createWindowsMsi", "zipWindowsAppImage")
}

tasks.register("createMacOSPackages") {
    description = "Erstellt alle macOS-Pakete (dmg, pkg, app-image)"
    dependsOn("createMacOSExecutable", "createMacOSPkg", "createMacOSAppImage")
}

tasks.register("createLinuxPackages") {
    description = "Erstellt alle Linux-Pakete (deb, rpm, app-image, tar.gz)"
    dependsOn("createLinuxDeb", "createLinuxRpm", "createLinuxAppImage", "createLinuxTar")
}

// Task to create only installers (no app-images)
tasks.register("createAllInstallers") {
    description = "Erstellt alle Installer-Pakete (exe, msi, dmg, pkg, deb, rpm)"
    dependsOn(
        "createWindowsExecutable",
        "createWindowsMsi",
        "createMacOSExecutable",
        "createMacOSPkg",
        "createLinuxDeb",
        "createLinuxRpm"
    )
}

// Task to create only app-images (portable versions)
tasks.register("createAllAppImages") {
    description = "Erstellt alle App-Image-Pakete (portable Versionen)"
    dependsOn(
        "zipWindowsAppImage",
        "createMacOSAppImage",
        "createLinuxAppImage",
        "createLinuxTar"
    )
}
