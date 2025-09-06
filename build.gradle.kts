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

    implementation("org.fxmisc.richtext:richtextfx:0.11.6")

    implementation("org.apache.logging.log4j:log4j-api:2.24.1")
    implementation("org.apache.logging.log4j:log4j-core:2.24.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.24.1")

    implementation("org.apache.santuario:xmlsec:4.0.4")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")

    // implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    // implementation("org.eclipse.lemminx:org.eclipse.lemminx:0.31.0")
    // implementation("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.40.0.M2")


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

    implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.3.5")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5")

    implementation("com.github.mifmif:generex:1.0.2")
    implementation("com.github.curious-odd-man:rgxgen:3.1")

    testImplementation(platform("org.junit:junit-bom:5.13.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.19.0")
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.testfx:openjfx-monocle:21.0.2")

    implementation("com.helger.schematron:ph-schematron-parent-pom:9.0.1")
    implementation("com.helger.commons:ph-io:12.0.0")
    // implementation("com.helger.commons:ph-commons:12.0.0")
    // implementation("com.helger.commons:ph-xml:12.0.0")
    implementation("com.helger.schematron:ph-schematron-api:9.0.1")
    implementation("com.helger.schematron:ph-schematron-xslt:9.0.1")
    implementation("com.helger.schematron:ph-schematron-pure:9.0.1")
    implementation("com.helger.schematron:ph-schematron-schxslt:9.0.1")
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("--enable-preview")
    }

    withType<Test> {
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED", "--enable-native-access=javafx.graphics")
    }

    withType<JavaExec> {
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED", "--enable-native-access=javafx.graphics")
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

tasks.register<Exec>("createRuntimeImage") {
    description = "Erstellt ein benutzerdefiniertes Runtime-Image für alle Plattformen"

    val runtimeDir = layout.buildDirectory.dir("image/runtime").get().asFile

    doFirst {
        runtimeDir.deleteRecursively()
    }

    workingDir = layout.buildDirectory.get().asFile
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
        "--add-modules", modules,
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress", "zip-6",
        "--output", runtimeDir.absolutePath
    )
}
tasks.register<Exec>("createWindowsExecutableX64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt Windows Executable für x64 (benutzerbezogene Installation)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-x64",
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
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createWindowsExecutableArm64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt Windows Executable für ARM64 (benutzerbezogene Installation)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-arm64",
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
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createMacOSExecutableX64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt macOS App Bundle für Intel x64 Macs"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-x64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "dmg",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.icns"),
        "--mac-package-name", "FreeXmlToolkit-x64",
        "--mac-package-identifier", "org.fxt.freexmltoolkit.x64",
        "--java-options", "-Djavafx.css.dump.lookup.errors=true",
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath
    )
}

tasks.register<Exec>("createMacOSExecutableArm64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt macOS App Bundle für Apple Silicon ARM64 Macs"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-arm64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "dmg",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.icns"),
        "--mac-package-name", "FreeXmlToolkit-arm64",
        "--mac-package-identifier", "org.fxt.freexmltoolkit.arm64",
        "--java-options", "-Djavafx.css.dump.lookup.errors=true",
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath
    )
}

tasks.register<Exec>("createLinuxDebX64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein .deb-Paket für Debian-basierte Linux-Distributionen (x64)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-x64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "deb",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("src/main/resources/img/logo.png"),
        "--linux-package-name", "freexmltoolkit-x64",
        "--linux-deb-maintainer", "karl.kauc@gmail.com",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createLinuxDebArm64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein .deb-Paket für Debian-basierte Linux-Distributionen (ARM64)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-arm64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "deb",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("src/main/resources/img/logo.png"),
        "--linux-package-name", "freexmltoolkit-arm64",
        "--linux-deb-maintainer", "karl.kauc@gmail.com",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createLinuxRpmX64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein .rpm-Paket für Red Hat-basierte Linux-Distributionen (x64)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-x64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "rpm",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("src/main/resources/img/logo.png"),
        "--linux-package-name", "freexmltoolkit-x64",
        "--linux-rpm-license-type", "Apache 2.0",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createLinuxRpmArm64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein .rpm-Paket für Red Hat-basierte Linux-Distributionen (ARM64)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-arm64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "rpm",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("src/main/resources/img/logo.png"),
        "--linux-package-name", "freexmltoolkit-arm64",
        "--linux-rpm-license-type", "Apache 2.0",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createLinuxAppImageX64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein AppImage für Linux-Distributionen (x64)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-x64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "app-image",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("src/main/resources/img/logo.png"),
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createLinuxAppImageArm64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein AppImage für Linux-Distributionen (ARM64)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-arm64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "app-image",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("src/main/resources/img/logo.png"),
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}


tasks.register<Exec>("createMacOSPkgX64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein .pkg Installer für Intel x64 Macs"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-x64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "pkg",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.icns"),
        "--mac-package-name", "FreeXmlToolkit-x64",
        "--mac-package-identifier", "org.fxt.freexmltoolkit.x64",
        "--java-options", "-Djavafx.css.dump.lookup.errors=true",
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath
    )
}

tasks.register<Exec>("createMacOSPkgArm64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein .pkg Installer für Apple Silicon ARM64 Macs"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-arm64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "pkg",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.icns"),
        "--mac-package-name", "FreeXmlToolkit-arm64",
        "--mac-package-identifier", "org.fxt.freexmltoolkit.arm64",
        "--java-options", "-Djavafx.css.dump.lookup.errors=true",
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath
    )
}

tasks.register<Exec>("createMacOSAppImageX64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein App Bundle für Intel x64 Macs (ohne Installer)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-x64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "app-image",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.icns"),
        "--mac-package-name", "FreeXmlToolkit-x64",
        "--mac-package-identifier", "org.fxt.freexmltoolkit.x64",
        "--java-options", "-Djavafx.css.dump.lookup.errors=true",
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath
    )
}

tasks.register<Exec>("createMacOSAppImageArm64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt ein App Bundle für Apple Silicon ARM64 Macs (ohne Installer)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-arm64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "app-image",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.icns"),
        "--mac-package-name", "FreeXmlToolkit-arm64",
        "--mac-package-identifier", "org.fxt.freexmltoolkit.arm64",
        "--java-options", "-Djavafx.css.dump.lookup.errors=true",
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics",
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath
    )
}

tasks.register<Exec>("createWindowsMsiX64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt Windows MSI Installer für x64 (systemweite Installation)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-x64",
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
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createWindowsMsiArm64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt Windows MSI Installer für ARM64 (systemweite Installation)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-arm64",
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
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createWindowsAppImageX64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt Windows App Image für x64 (ohne Installer)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-x64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "app-image",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.ico"),
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Exec>("createWindowsAppImageArm64") {
    dependsOn("copyDistributionFiles", "createRuntimeImage")
    description = "Erstellt Windows App Image für ARM64 (ohne Installer)"
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    workingDir = layout.buildDirectory.get().asFile
    commandLine(
        "jpackage",
        "--input", "image",
        "--name", "FreeXmlToolkit-arm64",
        "--main-jar", "FreeXmlToolkit.jar",
        "--main-class", "org.fxt.freexmltoolkit.FxtGui",
        "--type", "app-image",
        "--vendor", "Karl Kauc",
        "--app-version", version,
        "--icon", project.projectDir.resolve("release/logo.ico"),
        "--dest", "dist",
        "--runtime-image", layout.buildDirectory.dir("image/runtime").get().asFile.absolutePath,
        "--java-options", "--enable-preview",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "--enable-native-access=javafx.graphics"
    )
}

tasks.register<Zip>("zipWindowsAppImageX64") {
    description = "Zippt das erstellte Windows x64 App Image und löscht das Originalverzeichnis."
    dependsOn(tasks.named("createWindowsAppImageX64"))
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }

    val sourceDirProvider = layout.buildDirectory.dir("dist/FreeXmlToolkit-x64")
    from(sourceDirProvider)
    archiveFileName.set("FreeXmlToolkit-windows-x64-app-image-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    // Dieser Task sollte nur ausgeführt werden, wenn das Quellverzeichnis aus dem vorherigen Task existiert.
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows && sourceDirProvider.get().asFile.exists() }

    // Nach dem Zippen das Originalverzeichnis löschen
    doLast {
        val sourceDir = sourceDirProvider.get().asFile
        logger.lifecycle("Lösche originales x64 AppImage-Verzeichnis nach dem Zippen: ${sourceDir.path}")
        sourceDir.deleteRecursively()
    }
}

tasks.register<Zip>("zipWindowsAppImageArm64") {
    description = "Zippt das erstellte Windows ARM64 App Image und löscht das Originalverzeichnis."
    dependsOn(tasks.named("createWindowsAppImageArm64"))
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }

    val sourceDirProvider = layout.buildDirectory.dir("dist/FreeXmlToolkit-arm64")
    from(sourceDirProvider)
    archiveFileName.set("FreeXmlToolkit-windows-arm64-app-image-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    // Dieser Task sollte nur ausgeführt werden, wenn das Quellverzeichnis aus dem vorherigen Task existiert.
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows && sourceDirProvider.get().asFile.exists() }

    // Nach dem Zippen das Originalverzeichnis löschen
    doLast {
        val sourceDir = sourceDirProvider.get().asFile
        logger.lifecycle("Lösche originales ARM64 AppImage-Verzeichnis nach dem Zippen: ${sourceDir.path}")
        sourceDir.deleteRecursively()
    }
}

tasks.register<Zip>("zipMacOSAppImageX64") {
    description = "Zippt das erstellte macOS App Bundle für Intel x64 Macs."
    dependsOn(tasks.named("createMacOSAppImageX64"))
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

    val sourceDirProvider = layout.buildDirectory.dir("dist/FreeXmlToolkit-x64.app")
    val distDirProvider = layout.buildDirectory.dir("dist")

    // Das komplette .app Bundle als einzelnen Eintrag ins Zip
    from(distDirProvider) {
        include("FreeXmlToolkit-x64.app/**")
    }

    archiveFileName.set("FreeXmlToolkit-macos-x64-app-image-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    // Dieser Task sollte nur ausgeführt werden, wenn das Quellverzeichnis aus dem vorherigen Task existiert.
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX && sourceDirProvider.get().asFile.exists() }

    // Nach dem Zippen das Originalverzeichnis löschen
    doLast {
        val sourceDir = sourceDirProvider.get().asFile
        logger.lifecycle("Lösche originales x64 App Bundle nach dem Zippen: ${sourceDir.path}")
        sourceDir.deleteRecursively()
    }
}

tasks.register<Zip>("zipMacOSAppImageArm64") {
    description = "Zippt das erstellte macOS App Bundle für Apple Silicon ARM64 Macs."
    dependsOn(tasks.named("createMacOSAppImageArm64"))
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

    val sourceDirProvider = layout.buildDirectory.dir("dist/FreeXmlToolkit-arm64.app")
    val distDirProvider = layout.buildDirectory.dir("dist")

    // Das komplette .app Bundle als einzelnen Eintrag ins Zip
    from(distDirProvider) {
        include("FreeXmlToolkit-arm64.app/**")
    }

    archiveFileName.set("FreeXmlToolkit-macos-arm64-app-image-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    // Dieser Task sollte nur ausgeführt werden, wenn das Quellverzeichnis aus dem vorherigen Task existiert.
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX && sourceDirProvider.get().asFile.exists() }

    // Nach dem Zippen das Originalverzeichnis löschen
    doLast {
        val sourceDir = sourceDirProvider.get().asFile
        logger.lifecycle("Lösche originales ARM64 App Bundle nach dem Zippen: ${sourceDir.path}")
        sourceDir.deleteRecursively()
    }
}

tasks.register<Zip>("zipLinuxAppImageX64") {
    description = "Zippt das erstellte Linux x64 App Image und löscht das Originalverzeichnis."
    dependsOn(tasks.named("createLinuxAppImageX64"))
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }

    val sourceDirProvider = layout.buildDirectory.dir("dist/FreeXmlToolkit-x64")
    from(sourceDirProvider)
    archiveFileName.set("FreeXmlToolkit-linux-x64-app-image-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    // Dieser Task sollte nur ausgeführt werden, wenn das Quellverzeichnis aus dem vorherigen Task existiert.
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux && sourceDirProvider.get().asFile.exists() }

    // Nach dem Zippen das Originalverzeichnis löschen
    doLast {
        val sourceDir = sourceDirProvider.get().asFile
        logger.lifecycle("Lösche originales Linux x64 AppImage-Verzeichnis nach dem Zippen: ${sourceDir.path}")
        sourceDir.deleteRecursively()
    }
}

tasks.register<Zip>("zipLinuxAppImageArm64") {
    description = "Zippt das erstellte Linux ARM64 App Image und löscht das Originalverzeichnis."
    dependsOn(tasks.named("createLinuxAppImageArm64"))
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }

    val sourceDirProvider = layout.buildDirectory.dir("dist/FreeXmlToolkit-arm64")
    from(sourceDirProvider)
    archiveFileName.set("FreeXmlToolkit-linux-arm64-app-image-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))

    // Dieser Task sollte nur ausgeführt werden, wenn das Quellverzeichnis aus dem vorherigen Task existiert.
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux && sourceDirProvider.get().asFile.exists() }

    // Nach dem Zippen das Originalverzeichnis löschen
    doLast {
        val sourceDir = sourceDirProvider.get().asFile
        logger.lifecycle("Lösche originales Linux ARM64 AppImage-Verzeichnis nach dem Zippen: ${sourceDir.path}")
        sourceDir.deleteRecursively()
    }
}

tasks.named("createAllExecutables") {
    dependsOn(
        "createWindowsExecutableX64",
        "createWindowsExecutableArm64",
        "createWindowsMsiX64",
        "createWindowsMsiArm64",
        "zipWindowsAppImageX64",
        "zipWindowsAppImageArm64",
        "createMacOSExecutableX64",
        "createMacOSExecutableArm64",
        "createMacOSPkgX64",
        "createMacOSPkgArm64",
        "zipMacOSAppImageX64",
        "zipMacOSAppImageArm64",
        "createLinuxDebX64",
        "createLinuxDebArm64",
        "createLinuxRpmX64",
        "createLinuxRpmArm64",
        "zipLinuxAppImageX64",
        "zipLinuxAppImageArm64"
    )
}

// Convenience tasks for platform-specific packages
tasks.register("createWindowsPackages") {
    description = "Erstellt alle Windows-Pakete für beide Architekturen (exe, msi, app-image)"
    dependsOn(
        "createWindowsExecutableX64",
        "createWindowsExecutableArm64",
        "createWindowsMsiX64",
        "createWindowsMsiArm64",
        "zipWindowsAppImageX64",
        "zipWindowsAppImageArm64"
    )
}

tasks.register("createMacOSPackages") {
    description = "Erstellt alle macOS-Pakete für beide Architekturen (dmg, pkg, app-image)"
    dependsOn(
        "createMacOSExecutableX64",
        "createMacOSExecutableArm64",
        "createMacOSPkgX64",
        "createMacOSPkgArm64",
        "zipMacOSAppImageX64",
        "zipMacOSAppImageArm64"
    )
}

tasks.register("createLinuxPackages") {
    description = "Erstellt alle Linux-Pakete für beide Architekturen (deb, rpm, app-image)"
    dependsOn(
        "createLinuxDebX64",
        "createLinuxDebArm64",
        "createLinuxRpmX64",
        "createLinuxRpmArm64",
        "zipLinuxAppImageX64",
        "zipLinuxAppImageArm64"
    )
}

// Task to create only installers (no app-images)
tasks.register("createAllInstallers") {
    description = "Erstellt alle Installer-Pakete für alle Architekturen (exe, msi, dmg, pkg, deb, rpm)"
    dependsOn(
        "createWindowsExecutableX64",
        "createWindowsExecutableArm64",
        "createWindowsMsiX64",
        "createWindowsMsiArm64",
        "createMacOSExecutableX64",
        "createMacOSExecutableArm64",
        "createMacOSPkgX64",
        "createMacOSPkgArm64",
        "createLinuxDebX64",
        "createLinuxDebArm64",
        "createLinuxRpmX64",
        "createLinuxRpmArm64"
    )
}

// Task to create only app-images (portable versions)
tasks.register("createAllAppImages") {
    description = "Erstellt alle App-Image-Pakete (portable Versionen) für alle Architekturen"
    dependsOn(
        "zipWindowsAppImageX64",
        "zipWindowsAppImageArm64",
        "zipMacOSAppImageX64",
        "zipMacOSAppImageArm64",
        "zipLinuxAppImageX64",
        "zipLinuxAppImageArm64"
    )
}
