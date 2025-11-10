import java.net.URI
import java.util.*

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
    id("com.github.ben-manes.versions") version "0.53.0"
}

version = "1.0.0"
group = "org.fxt"

repositories {
    mavenCentral()
    gradlePluginPortal()
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
    implementation("net.sf.saxon:Saxon-HE:12.9")

    // XSD 1.1 support with assertions - use special Xerces build from exist-db
    // This version includes XSD 1.1 support with assertions
    implementation("org.exist-db.thirdparty.xerces:xercesImpl:2.12.2") {
        artifact {
            classifier = "xml-schema-1.1"
        }
    }
    // XPath 2.0 processor required for XSD 1.1 assertions
    runtimeOnly("org.exist-db.thirdparty.org.eclipse.wst.xml:xpath2:1.2.0")
    // Java CUP runtime required for XPath 2.0 processor
    runtimeOnly("edu.princeton.cup:java-cup:10k")
    
    // Alternative: Use local custom Xerces XSD 1.1 JAR files if available
    // These would be loaded before any other Xerces implementations
    /*
    implementation(fileTree("libs/xerces-j-xsd11") {
        include("*.jar")
    })
    */

    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.4")
    implementation("com.google.code.gson:gson:2.13.2")
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
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.1")

    implementation("org.apache.santuario:xmlsec:4.0.4")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.82")

    implementation("org.controlsfx:controlsfx:11.2.2")

    implementation("org.apache.xmlgraphics:fop:2.11")
    implementation("org.apache.pdfbox:pdfbox:3.0.6")
    implementation("org.apache.xmlgraphics:batik-svggen:1.19")
    implementation("org.apache.xmlgraphics:batik-all:1.19")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")

    implementation("org.apache.poi:poi:5.4.1")
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    implementation("org.apache.commons:commons-lang3:3.19.0")
    implementation("commons-io:commons-io:2.20.0")
    implementation("org.apache.commons:commons-text:1.14.0")
    implementation("commons-validator:commons-validator:1.10.0")

    implementation("fr.brouillard.oss:cssfx:11.5.1")
    implementation("org.thymeleaf:thymeleaf:3.1.3.RELEASE")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("jakarta.activation:jakarta.activation-api:2.1.3")

    implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.3.5")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5.1")

    implementation("com.github.mifmif:generex:1.0.2")
    implementation("com.github.curious-odd-man:rgxgen:3.1")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.testfx:openjfx-monocle:21.0.2")

    implementation("com.helger.schematron:ph-schematron-parent-pom:9.0.1")
    implementation("com.helger.commons:ph-io:12.1.0")
    // implementation("com.helger.commons:ph-commons:12.0.3")
    // implementation("com.helger.commons:ph-xml:12.0.3")
    implementation("com.helger.schematron:ph-schematron-api:9.0.1")
    implementation("com.helger.schematron:ph-schematron-xslt:9.0.1")
    implementation("com.helger.schematron:ph-schematron-pure:9.0.1")
    implementation("com.helger.schematron:ph-schematron-schxslt:9.0.1")
}

// Exclude old SLF4J bindings to avoid conflicts with SLF4J 2.x
configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-simple")
    exclude(group = "org.slf4j", module = "slf4j-log4j12")
    exclude(group = "org.slf4j", module = "slf4j-jdk14")
    exclude(group = "ch.qos.logback", module = "logback-classic")
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

// JavaFX Konfiguration
val javafxVersion = "25" // oder Ihre gewünschte Version
val jmodsDir = file("${projectDir}/javafx-jmods")

// Betriebssystem-Erkennung für JavaFX
fun getJavafxPlatform(): String {
    val os = org.gradle.internal.os.OperatingSystem.current()
    val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())

    return when {
        os.isWindows -> "windows-x64" // Nur x64 verfügbar
        os.isMacOsX -> if (arch.contains("aarch64") || arch.contains("arm")) "osx-aarch64" else "osx-x64"
        os.isLinux -> "linux-x64" // Nur x64 verfügbar
        else -> throw GradleException("Unsupported operating system")
    }
}

// Task zum Herunterladen der JavaFX JMODs
tasks.register("downloadJavaFXJmods") {
    description = "Lädt JavaFX JMODs für alle verfügbaren Plattformen herunter"

    doLast {
        // Nur die verfügbaren Plattformen von Gluon
        val platforms = mapOf(
            "linux-x64" to "linux-x64",
            "osx-aarch64" to "osx-aarch64",
            "osx-x64" to "osx-x64",
            "windows-x64" to "windows-x64"
        )

        platforms.forEach { (platform, urlPlatform) ->
            val platformDir = file("$jmodsDir/$platform")

            if (!platformDir.exists() || platformDir.listFiles { it.name.endsWith(".jmod") }.isNullOrEmpty()) {
                println("Downloading JavaFX JMODs for $platform...")
                platformDir.mkdirs()

                val jmodsUrl =
                    "https://download2.gluonhq.com/openjfx/${javafxVersion}/openjfx-${javafxVersion}_${urlPlatform}_bin-jmods.zip"
                val zipFile = file("$layout.buildDirectory/tmp/javafx-${platform}-jmods.zip")
                zipFile.parentFile.mkdirs()

                println("Downloading from: $jmodsUrl")

                // Download mit Ant
                try {
                    ant.invokeMethod(
                        "get", mapOf(
                            "src" to jmodsUrl,
                            "dest" to zipFile,
                            "verbose" to true,
                            "skipexisting" to false
                        )
                    )

                    // Extract
                    println("Extracting JavaFX JMODs to $platformDir...")
                    copy {
                        from(zipTree(zipFile))
                        into(platformDir)
                        // Die JMODs sind in javafx-jmods-{version}/ Unterverzeichnis
                        eachFile {
                            if (path.startsWith("javafx-jmods-${javafxVersion}/")) {
                                relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                            }
                        }
                        includeEmptyDirs = false
                    }

                    // Cleanup ZIP file
                    zipFile.delete()

                    println("✅ JavaFX JMODs for $platform downloaded successfully")
                } catch (e: Exception) {
                    println("❌ Failed to download JavaFX JMODs for $platform: ${e.message}")
                    platformDir.deleteRecursively()
                    throw e
                }
            } else {
                println("✅ JavaFX JMODs for $platform already exist in $platformDir")
            }
        }
    }
}

// Alternative Download-Methode mit Gradle's download capabilities
tasks.register("downloadJavaFXJmodsWithGradle") {
    description = "Lädt JavaFX JMODs mit Gradle's eigenen Download-Mechanismen herunter"

    doLast {
        val platforms = listOf("linux-x64", "osx-aarch64", "osx-x64", "windows-x64")

        platforms.forEach { platform ->
            val platformDir = file("$jmodsDir/$platform")

            if (!platformDir.exists() || platformDir.listFiles { it.name.endsWith(".jmod") }.isNullOrEmpty()) {
                platformDir.mkdirs()

                val jmodsUrl =
                    "https://download2.gluonhq.com/openjfx/${javafxVersion}/openjfx-${javafxVersion}_${platform}_bin-jmods.zip"
                val zipFile = file("$layout.buildDirectory/tmp/javafx-${platform}-jmods.zip")
                zipFile.parentFile.mkdirs()

                println("Downloading JavaFX JMODs for $platform from $jmodsUrl...")

                // Download mit Java
                val url = URI(jmodsUrl).toURL()
                url.openStream().use { input ->
                    zipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Extract
                println("Extracting JavaFX JMODs...")
                copy {
                    from(zipTree(zipFile))
                    into(platformDir)
                    eachFile {
                        if (path.startsWith("javafx-jmods-${javafxVersion}/")) {
                            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
                        }
                    }
                    includeEmptyDirs = false
                }

                zipFile.delete()
                println("✅ JavaFX JMODs for $platform installed")
            }
        }
    }
}

// Modifizierter createRuntimeImage Task mit JavaFX JMODs
tasks.register<Exec>("createRuntimeImage") {
    dependsOn("downloadJavaFXJmods")
    description = "Erstellt ein benutzerdefiniertes Runtime-Image mit JavaFX für alle Plattformen"

    val runtimeDir = layout.buildDirectory.dir("image/runtime").get().asFile
    val currentPlatform = getJavafxPlatform()
    val javafxJmodsPath = file("$jmodsDir/$currentPlatform")

    doFirst {
        runtimeDir.deleteRecursively()

        // Überprüfen, ob JMODs vorhanden sind
        if (!javafxJmodsPath.exists() || javafxJmodsPath.listFiles { f -> f.name.endsWith(".jmod") }.isNullOrEmpty()) {
            throw GradleException("JavaFX JMODs not found for platform $currentPlatform in $javafxJmodsPath. Run 'downloadJavaFXJmods' first.")
        }

        println("Using JavaFX JMODs from: $javafxJmodsPath")
        println("Found JMODs: ${javafxJmodsPath.listFiles { it.name.endsWith(".jmod") }?.map { it.name }}")
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
        "java.management",
        "java.prefs",
        "jdk.xml.dom",
        "jdk.unsupported.desktop",
        // JavaFX Module
        "javafx.base",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.media",
        "javafx.web",
        "javafx.swing"
    ).joinToString(",")

    val javaHome = System.getProperty("java.home")

    // Erstelle den Module-Path mit System-JMODs und JavaFX-JMODs
    val modulePath = listOf(
        "$javaHome/jmods",
        javafxJmodsPath.absolutePath
    ).joinToString(File.pathSeparator)

    println("Module path: $modulePath")

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

// Plattform-spezifische Runtime-Images erstellen
tasks.register<Exec>("createRuntimeImageForWindows") {
    dependsOn("downloadJavaFXJmods")
    description = "Erstellt ein Runtime-Image speziell für Windows x64"

    val runtimeDir = layout.buildDirectory.dir("image/runtime-windows-x64").get().asFile
    val javafxJmodsPath = file("$jmodsDir/windows-x64")

    doFirst {
        runtimeDir.deleteRecursively()
        if (!javafxJmodsPath.exists()) {
            throw GradleException("JavaFX JMODs not found for Windows x64. Run 'downloadJavaFXJmods' first.")
        }
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
        "java.management",
        "java.prefs",
        "jdk.xml.dom",
        "jdk.unsupported.desktop",
        "javafx.base",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.media",
        "javafx.web",
        "javafx.swing"
    ).joinToString(",")

    val javaHome = System.getProperty("java.home")
    val modulePath = listOf(
        "$javaHome/jmods",
        javafxJmodsPath.absolutePath
    ).joinToString(File.pathSeparator)

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

tasks.register<Exec>("createRuntimeImageForLinux") {
    dependsOn("downloadJavaFXJmods")
    description = "Erstellt ein Runtime-Image speziell für Linux x64"

    val runtimeDir = layout.buildDirectory.dir("image/runtime-linux-x64").get().asFile
    val javafxJmodsPath = file("$jmodsDir/linux-x64")

    doFirst {
        runtimeDir.deleteRecursively()
        if (!javafxJmodsPath.exists()) {
            throw GradleException("JavaFX JMODs not found for Linux x64. Run 'downloadJavaFXJmods' first.")
        }
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
        "java.management",
        "java.prefs",
        "jdk.xml.dom",
        "jdk.unsupported.desktop",
        "javafx.base",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.media",
        "javafx.web",
        "javafx.swing"
    ).joinToString(",")

    val javaHome = System.getProperty("java.home")
    val modulePath = listOf(
        "$javaHome/jmods",
        javafxJmodsPath.absolutePath
    ).joinToString(File.pathSeparator)

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

tasks.register<Exec>("createRuntimeImageForMacOSX64") {
    dependsOn("downloadJavaFXJmods")
    description = "Erstellt ein Runtime-Image speziell für macOS Intel x64"

    val runtimeDir = layout.buildDirectory.dir("image/runtime-osx-x64").get().asFile
    val javafxJmodsPath = file("$jmodsDir/osx-x64")

    doFirst {
        runtimeDir.deleteRecursively()
        if (!javafxJmodsPath.exists()) {
            throw GradleException("JavaFX JMODs not found for macOS x64. Run 'downloadJavaFXJmods' first.")
        }
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
        "java.management",
        "java.prefs",
        "jdk.xml.dom",
        "jdk.unsupported.desktop",
        "javafx.base",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.media",
        "javafx.web",
        "javafx.swing"
    ).joinToString(",")

    val javaHome = System.getProperty("java.home")
    val modulePath = listOf(
        "$javaHome/jmods",
        javafxJmodsPath.absolutePath
    ).joinToString(File.pathSeparator)

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

tasks.register<Exec>("createRuntimeImageForMacOSAArch64") {
    dependsOn("downloadJavaFXJmods")
    description = "Erstellt ein Runtime-Image speziell für macOS Apple Silicon"

    val runtimeDir = layout.buildDirectory.dir("image/runtime-osx-aarch64").get().asFile
    val javafxJmodsPath = file("$jmodsDir/osx-aarch64")

    doFirst {
        runtimeDir.deleteRecursively()
        if (!javafxJmodsPath.exists()) {
            throw GradleException("JavaFX JMODs not found for macOS AArch64. Run 'downloadJavaFXJmods' first.")
        }
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
        "java.management",
        "java.prefs",
        "jdk.xml.dom",
        "jdk.unsupported.desktop",
        "javafx.base",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.media",
        "javafx.web",
        "javafx.swing"
    ).joinToString(",")

    val javaHome = System.getProperty("java.home")
    val modulePath = listOf(
        "$javaHome/jmods",
        javafxJmodsPath.absolutePath
    ).joinToString(File.pathSeparator)

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

// Task zum Bereinigen der JMODs
tasks.register<Delete>("cleanJavaFXJmods") {
    description = "Löscht heruntergeladene JavaFX JMODs"
    delete(jmodsDir)
}

// Task zum Verifizieren der JMODs
tasks.register("verifyJavaFXJmods") {
    dependsOn("downloadJavaFXJmods")
    description = "Überprüft, ob alle benötigten JavaFX JMODs vorhanden sind"

    doLast {
        val platforms = listOf("linux-x64", "osx-aarch64", "osx-x64", "windows-x64")
        val requiredModules = listOf(
            "javafx.base.jmod",
            "javafx.controls.jmod",
            "javafx.fxml.jmod",
            "javafx.graphics.jmod",
            "javafx.media.jmod",
            "javafx.web.jmod",
            "javafx.swing.jmod"
        )

        var allGood = true

        platforms.forEach { platform ->
            val platformDir = file("$jmodsDir/$platform")
            println("\n📦 Checking JavaFX JMODs for $platform:")
            println("   Directory: $platformDir")

            if (!platformDir.exists()) {
                println("   ❌ Directory not found!")
                allGood = false
            } else {
                val missingModules = mutableListOf<String>()
                requiredModules.forEach { module ->
                    val moduleFile = file("$platformDir/$module")
                    if (moduleFile.exists()) {
                        val sizeMB = moduleFile.length() / 1024 / 1024
                        println("   ✅ $module (${sizeMB} MB)")
                    } else {
                        println("   ❌ $module - MISSING!")
                        missingModules.add(module)
                        allGood = false
                    }
                }

                if (missingModules.isEmpty()) {
                    println("   ✅ All modules present for $platform")
                } else {
                    println("   ⚠️  Missing modules: ${missingModules.joinToString(", ")}")
                }
            }
        }

        if (allGood) {
            println("\n✅ All JavaFX JMODs successfully verified!")
        } else {
            println("\n❌ Some JavaFX JMODs are missing. Run './gradlew downloadJavaFXJmods' to download them.")
        }
    }
}

// Info Task
tasks.register("javaFXInfo") {
    description = "Zeigt Informationen über die JavaFX-Konfiguration"

    doLast {
        println("\n╔════════════════════════════════════════════╗")
        println("║          JavaFX JMODs Configuration        ║")
        println("╚════════════════════════════════════════════╝")
        println()
        println("JavaFX Version: $javafxVersion")
        println("JMODs Directory: $jmodsDir")
        println("Current Platform: ${getJavafxPlatform()}")
        println()
        println("Available platforms:")
        println("  • linux-x64    - Linux 64-bit")
        println("  • osx-x64      - macOS Intel 64-bit")
        println("  • osx-aarch64  - macOS Apple Silicon")
        println("  • windows-x64  - Windows 64-bit")
        println()
        println("Download URLs:")
        listOf("linux-x64", "osx-aarch64", "osx-x64", "windows-x64").forEach { platform ->
            println("  • https://download2.gluonhq.com/openjfx/$javafxVersion/openjfx-${javafxVersion}_${platform}_bin-jmods.zip")
        }
        println()
        println("Usage:")
        println("  1. ./gradlew downloadJavaFXJmods    - Download JMODs")
        println("  2. ./gradlew verifyJavaFXJmods      - Verify installation")
        println("  3. ./gradlew createRuntimeImage     - Create runtime with JavaFX")
        println()
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

tasks.register<Exec>("createRuntimeImageOLD") {
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
