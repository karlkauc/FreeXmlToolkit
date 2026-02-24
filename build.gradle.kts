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
    jacoco
    id("com.github.ben-manes.versions") version "0.53.0"
}

version = "1.6.0"
group = "org.fxt"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// JavaFX is included in Liberica Full JDK - no separate configuration needed

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

    // JSON Editor support
    implementation("de.marhali:json5-java:3.0.0")                  // JSON5 support (comments, trailing commas)
    implementation("com.networknt:json-schema-validator:1.5.3")    // JSON Schema validation
    implementation("com.jayway.jsonpath:json-path:2.10.0")          // JSONPath queries
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-bootstrapicons-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-win10-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-coreui-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-fontawesome-pack:12.4.0")
    implementation("io.github.mkpaz:atlantafx-base:2.1.0")

    implementation("org.fxmisc.richtext:richtextfx:0.11.7")

    implementation("org.apache.logging.log4j:log4j-api:2.24.1")
    implementation("org.apache.logging.log4j:log4j-core:2.24.1")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.24.1")

    implementation("org.apache.santuario:xmlsec:4.0.4")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")

    implementation("org.controlsfx:controlsfx:11.2.3")

    implementation("org.apache.xmlgraphics:fop:2.11")
    implementation("org.apache.pdfbox:pdfbox:3.0.6")
    implementation("org.apache.xmlgraphics:batik-svggen:1.19")
    implementation("org.apache.xmlgraphics:batik-all:1.19")
    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")

    implementation("org.apache.poi:poi:5.5.1")
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    implementation("org.apache.commons:commons-lang3:3.20.0")
    implementation("commons-io:commons-io:2.21.0")
    implementation("org.apache.commons:commons-text:1.15.0")
    implementation("commons-validator:commons-validator:1.10.1")

    implementation("fr.brouillard.oss:cssfx:11.5.1")
    implementation("org.thymeleaf:thymeleaf:3.1.3.RELEASE")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("jakarta.activation:jakarta.activation-api:2.1.3")

    implementation("org.apache.httpcomponents.core5:httpcore5-h2:5.4")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.6")

    implementation("com.github.mifmif:generex:1.0.2")
    implementation("com.github.curious-odd-man:rgxgen:3.1")

    // Note: Guice 7.0.0 doesn't support Java 25 (class file version 69)
    // Using manual DI pattern instead - see org.fxt.freexmltoolkit.di package

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.21.0")
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.testfx:openjfx-monocle:21.0.2")

    implementation("com.helger.schematron:ph-schematron-parent-pom:9.1.1")
    implementation("com.helger.commons:ph-io:12.1.1")
    // implementation("com.helger.commons:ph-commons:12.0.3")
    // implementation("com.helger.commons:ph-xml:12.0.3")
    implementation("com.helger.schematron:ph-schematron-api:9.1.1")
    implementation("com.helger.schematron:ph-schematron-xslt:9.1.1")
    implementation("com.helger.schematron:ph-schematron-pure:9.1.1")
    implementation("com.helger.schematron:ph-schematron-schxslt:9.1.1")
}

// Java Toolchain Configuration - Use JDK with JavaFX included
// REQUIREMENTS for cross-platform builds:
// - JDK must include JavaFX modules (Liberica Full JDK, Azul Zulu FX, etc.)
// - For GitHub Actions: Configure JDK setup action with appropriate distribution
// - For local development: Set JAVA_HOME to JDK with JavaFX or use toolchain auto-provisioning
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25) // Use Java 25 with JavaFX
        vendor = JvmVendorSpec.BELLSOFT  // Use Liberica Full JDK with JavaFX
    }
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
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED", "--enable-native-access=javafx.graphics", "--enable-native-access=javafx.web")
    }

    withType<JavaExec> {
        jvmArgs(
            "--enable-preview",
            "--enable-native-access=ALL-UNNAMED",
            "--enable-native-access=javafx.graphics",
            "-Dprism.order=sw",           // Force software rendering (may reduce WebView rendering errors)
            "-Dprism.verbose=false"        // Reduce graphics logging
        )
    }
}

// Exclude node_modules from resources processing
tasks.processResources {
    exclude("**/node_modules/**")
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
    exclude("**/node_modules/**")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "16G"
    testLogging {
        events("passed", "skipped", "failed")
    }
    // JVM arguments required for Mockito/Objenesis with Java 21+ and JavaFX
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        // Required for Mockito/Objenesis on Java 25+
        "--add-opens", "java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED",
        "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
        "--add-opens", "java.base/sun.security.ssl=ALL-UNNAMED",
        // JavaFX module opens for testing (TestFX + Monocle headless)
        "--add-opens", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.prism=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.glass.utils=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/javafx.stage=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/javafx.scene=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.javafx.util=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.prism.impl=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.javafx.geom=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED",
        "--add-opens", "javafx.base/com.sun.javafx.runtime=ALL-UNNAMED",
        "--add-opens", "javafx.base/com.sun.javafx.logging=ALL-UNNAMED",
        "--add-opens", "javafx.base/com.sun.javafx.reflect=ALL-UNNAMED",
        "--add-opens", "javafx.controls/javafx.scene.control=ALL-UNNAMED",
        "--add-opens", "javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED",
        "--add-opens", "javafx.controls/com.sun.javafx.scene.control.behavior=ALL-UNNAMED",
        // Additional exports for native access (Monocle headless platform)
        "--add-exports", "javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.util=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.glass.ui.monocle=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.prism=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.geom=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED",
        "--add-exports", "javafx.base/com.sun.javafx.logging=ALL-UNNAMED",
        // Headless mode for JavaFX tests
        "-Djava.awt.headless=true",
        "-Dtestfx.robot=glass",
        "-Dtestfx.headless=true",
        "-Dprism.order=sw",
        "-Dprism.text=t2k",
        "-Dglass.platform=Monocle",
        "-Dmonocle.platform=Headless"
    )
}

// JaCoCo Code Coverage Configuration
jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
    }

    classDirectories.setFrom(
        files(tasks.compileJava.get().destinationDirectory).asFileTree.matching {
            exclude(
                "**/demo/**"
            )
        }
    )

    sourceDirectories.setFrom(files("src/main/java"))
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// Native packaging tasks using jpackage
val jpackageTask = tasks.register("jpackage") {
    group = "distribution"
    description = "Base task for creating native packages"
    dependsOn("jar")
}

// Helper function to create ZIP task for app-image
fun createZipTask(jpackageTaskName: String, platform: String, arch: String): String {
    val currentOs = when {
        System.getProperty("os.name").lowercase().contains("windows") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "macos"
        else -> "linux"
    }
    
    val currentArch = when {
        System.getProperty("os.arch").contains("aarch64") || System.getProperty("os.arch").contains("arm") -> "arm64"
        else -> "x64"
    }
    
    val zipTaskName = "${jpackageTaskName}Zip"
    
    // Only create ZIP for host platform/architecture
    if (platform != currentOs || arch != currentArch) {
        tasks.register(zipTaskName) {
            group = "distribution"
            description = "Skip ZIP for $platform-$arch (can only create native packages on $currentOs-$currentArch)"
            doFirst {
                println("⚠️ Skipping $platform-$arch ZIP creation (cross-platform packaging not supported)")
                println("💡 Current platform: $currentOs-$currentArch")
            }
        }
        return zipTaskName
    }
    
    tasks.register<Zip>(zipTaskName) {
        group = "distribution"
        description = "Create ZIP archive for $platform-$arch app-image"
        dependsOn(jpackageTaskName)

        val sourceDir = "build/dist/$platform-$arch-app-image"
        val zipFileName = "FreeXmlToolkit-$platform-$arch-app-image-${project.version}.zip"

        from(sourceDir)

        archiveFileName.set(zipFileName)
        destinationDirectory.set(file("build/dist"))

        // Preserve file timestamps (build time) in ZIP archive
        isPreserveFileTimestamps = true

        doFirst {
            if (!file(sourceDir).exists()) {
                throw GradleException("Source directory $sourceDir does not exist")
            }
            println("Creating ZIP: $zipFileName from $sourceDir")
            println("Including updater script for $platform")
        }

        doLast {
            val zipFile = archiveFile.get().asFile
            if (zipFile.exists()) {
                println("ZIP created successfully: ${zipFile.absolutePath} (${zipFile.length()} bytes)")
                // Clean up the directory after successful zipping
                delete(sourceDir)
            } else {
                throw GradleException("ZIP creation failed for $zipFileName")
            }
        }
    }
    
    return zipTaskName
}

// Helper function to create platform-specific jpackage tasks
fun createJPackageTask(taskName: String, platform: String, arch: String, packageType: String) {
    val currentOs = when {
        System.getProperty("os.name").lowercase().contains("windows") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "macos"
        else -> "linux"
    }
    
    val currentArch = when {
        System.getProperty("os.arch").contains("aarch64") || System.getProperty("os.arch").contains("arm") -> "arm64"
        else -> "x64"
    }
    
    // Only create packages for host platform/architecture
    if (platform != currentOs || arch != currentArch) {
        tasks.register(taskName) {
            group = "distribution"
            description = "Skip $platform-$arch $packageType (can only create native packages on $currentOs-$currentArch)"
            doFirst {
                println("⚠️ Skipping $platform-$arch $packageType creation (cross-platform packaging not supported)")
                println("💡 Current platform: $currentOs-$currentArch")
            }
        }
        return
    }
    
    val platformName = when(platform) {
        "macos" -> "MacOS"
        "windows" -> "Windows" 
        "linux" -> "Linux"
        else -> platform.replaceFirstChar { it.uppercaseChar() }
    }
    val archName = when(arch) {
        "x64" -> "X64"
        "arm64" -> "Arm64"
        "aarch64" -> "Arm64"
        else -> arch.replaceFirstChar { it.uppercaseChar() }
    }
    val runtimeTaskName = "create${platformName}Runtime${archName}"
    
    tasks.register(taskName, Exec::class) {
        group = "distribution"
        description = "Create $packageType package for $platform-$arch with custom runtime"
        dependsOn("jar", runtimeTaskName)
        
        doFirst {
            val outputDir = project.layout.buildDirectory.dir("dist/$platform-$arch-$packageType").get().asFile
            delete(outputDir)
            mkdir(outputDir)
        }
        
        val jpackageCmd = if (System.getProperty("os.name").lowercase().contains("windows")) "jpackage.exe" else "jpackage"
        
        // Determine app name based on package type and platform
        val appName = when {
            packageType == "app-image" -> "FreeXmlToolkit"
            platform == "linux" -> "freexmltoolkit"
            else -> "FreeXmlToolkit"
        }
        
        // Arguments file will be created in doFirst block
        val iconPath = when (platform) {
            "windows" -> "release/logo.ico"
            "macos" -> "release/logo.icns"
            else -> "src/main/resources/img/logo.png"  // Linux requires PNG format
        }
        
        // Build platform-specific arguments
        val platformArgs = when {
            platform == "windows" && packageType == "app-image" -> ""
            platform == "windows" && packageType != "app-image" -> """
--icon """ + project.projectDir.resolve("release/logo.ico") + """
--win-dir-chooser
--win-menu
--win-shortcut"""
            platform == "macos" && packageType != "app-image" -> """
--icon """ + project.projectDir.resolve("release/logo.icns") + """
--mac-package-identifier
org.fxt.freexmltoolkit
--mac-package-name
FreeXmlToolkit"""
            platform == "linux" && packageType != "app-image" -> """
--icon """ + project.projectDir.resolve("src/main/resources/img/logo.png") + """
--linux-package-name
freexmltoolkit
--linux-app-category
Development
--linux-shortcut"""
            else -> ""
        }
        
        // Copy all runtime dependencies to libs directory and create argument file
        doFirst {
            copy {
                from(configurations.runtimeClasspath.get())
                into("build/libs/lib")
            }
            
            // Create arguments file to avoid command line length issues on Windows  
            val argsFile = File(project.layout.buildDirectory.asFile.get(), "jpackage-args-$platform-$arch-$packageType.txt")
            
            // Check if icon exists before adding it to arguments
            // For app-image, icon is embedded in the executable - use project relative path
            val projectIconPath = File(project.rootDir, iconPath).absolutePath
            val iconArg = if (File(project.rootDir, iconPath).exists()) "--icon\n$projectIconPath" else ""
            val helperLauncherFile = File(project.layout.buildDirectory.asFile.get(),
                "update-helper-$platform-$arch-$packageType.properties")
            helperLauncherFile.writeText("""
                main-jar=FreeXmlToolkit.jar
                main-class=org.fxt.freexmltoolkit.service.update.UpdateHelperMain
                name=UpdateHelper
            """.trimIndent())
            
            // Use custom runtime - REQUIRED for all packages
            val runtimePath = project.layout.buildDirectory.dir("runtime/$platform-$arch").get().asFile
            val runtimeFile = runtimePath
            if (!runtimeFile.exists() || !runtimeFile.isDirectory()) {
                throw GradleException("""
                    ❌ Runtime not found at: ${runtimeFile.absolutePath}

                    The jlink runtime is required for creating packages with embedded JVM.
                    Please run the corresponding runtime task first:
                    ./gradlew create${platformName}Runtime${archName}

                    Or ensure the runtime task dependency is correctly set up.
                """.trimIndent())
            }
            println("✅ Using custom runtime: ${runtimeFile.absolutePath}")
            println("📁 Runtime contents:")
            runtimeFile.listFiles()?.take(10)?.forEach { println("   - ${it.name}") }
            val runtimeArg = "--runtime-image\n${runtimeFile.absolutePath}"
            
            // Use absolute paths for better compatibility
            val inputDir = project.layout.buildDirectory.dir("libs").get().asFile.absolutePath
            val destDir = project.layout.buildDirectory.dir("dist/$platform-$arch-$packageType").get().asFile.absolutePath
            
            argsFile.writeText("""--type
$packageType
--input
$inputDir
--dest
$destDir
--name
$appName
--main-jar
FreeXmlToolkit.jar
--main-class
org.fxt.freexmltoolkit.FxtGui
--app-version
${project.version}
--vendor
"Karl Kauc"
--copyright
"Copyright (c) Karl Kauc 2024"
--description
"FreeXMLToolkit - Universal Toolkit for XML"
$iconArg
--add-launcher
UpdateHelper=${helperLauncherFile.absolutePath}
$runtimeArg
--java-options
--enable-preview
--java-options
--enable-native-access=ALL-UNNAMED
--java-options
--enable-native-access=javafx.graphics
--java-options
-Xms128m
--java-options
-Xmx1g
--java-options
-XX:TieredStopAtLevel=1
--java-options
-XX:+UseParallelGC
--java-options
-Dprism.order=d3d,sw
--verbose
$platformArgs""".trimIndent())
            
            // Add debug output after creating the file
            println("🔧 jpackage command: $jpackageCmd")
            println("📋 Arguments file: ${argsFile.absolutePath}")
            println("📄 Arguments file contents:")
            println(argsFile.readText())
            
            if (platform == "linux" && packageType == "app-image") {
                println("🐧 Running Linux app-image creation with runtime...")
            }
        }
        
        val argsFilePath = File(project.layout.buildDirectory.asFile.get(), "jpackage-args-$platform-$arch-$packageType.txt").absolutePath
        commandLine(jpackageCmd, "@$argsFilePath")
        isIgnoreExitValue = true
        
        // Post-processing for naming and packaging
        doLast {
            val sourceDir = "build/dist/$platform-$arch-$packageType"
            val version = project.version.toString()
            
            when {
                packageType == "app-image" -> {
                    // Ensure source directory exists before processing
                    if (file(sourceDir).exists()) {
                        // Copy additional files from release directory to app root
                        val appName = when (platform) {
                            "linux" -> "freexmltoolkit"
                            else -> "FreeXmlToolkit"
                        }
                        val appRootDir = File(sourceDir, appName)
                        
                        // Copy examples directory
                        val examplesSource = File("release/examples")
                        val examplesTarget = File(appRootDir, "examples")
                        if (examplesSource.exists()) {
                            copy {
                                from(examplesSource)
                                into(examplesTarget)
                            }
                            println("Copied examples directory to app root")
                        }
                        
                        // Copy log4j2.xml
                        val log4jSource = File("release/log4j2.xml")
                        File(appRootDir, "log4j2.xml")
                        if (log4jSource.exists()) {
                            copy {
                                from(log4jSource)
                                into(appRootDir)
                            }
                            println("Copied log4j2.xml to app root")
                        }
                        println("App-image created successfully in: $sourceDir")
                        println("Note: Use corresponding ZIP task to create archive")
                    } else {
                        println("Source directory $sourceDir does not exist")
                    }
                }
                packageType == "exe" -> {
                    // Rename exe files to match required naming convention
                    val expectedName = "FreeXmlToolkit-$arch-$version.exe"
                    fileTree(sourceDir).filter { it.name.endsWith(".exe") }.forEach { file ->
                        file.renameTo(File(file.parent, expectedName))
                    }
                }
                packageType == "msi" -> {
                    // Rename msi files to match required naming convention
                    val expectedName = "FreeXmlToolkit-$arch-$version.msi"
                    fileTree(sourceDir).filter { it.name.endsWith(".msi") }.forEach { file ->
                        file.renameTo(File(file.parent, expectedName))
                    }
                }
                packageType == "dmg" -> {
                    // Rename dmg files to match required naming convention
                    val expectedName = "FreeXmlToolkit-$arch-$version.dmg"
                    fileTree(sourceDir).filter { it.name.endsWith(".dmg") }.forEach { file ->
                        file.renameTo(File(file.parent, expectedName))
                    }
                }
                packageType == "pkg" -> {
                    // Rename pkg files to match required naming convention
                    val expectedName = "FreeXmlToolkit-$arch-$version.pkg"
                    fileTree(sourceDir).filter { it.name.endsWith(".pkg") }.forEach { file ->
                        file.renameTo(File(file.parent, expectedName))
                    }
                }
                packageType == "deb" -> {
                    // Rename deb files to match required naming convention
                    val expectedName = "freexmltoolkit-${arch}_${version}_amd64.deb"
                    fileTree(sourceDir).filter { it.name.endsWith(".deb") }.forEach { file ->
                        file.renameTo(File(file.parent, expectedName))
                    }
                }
                packageType == "rpm" -> {
                    // Rename rpm files to match required naming convention
                    val expectedName = "freexmltoolkit-$arch-$version-1.x86_64.rpm"
                    fileTree(sourceDir).filter { it.name.endsWith(".rpm") }.forEach { file ->
                        file.renameTo(File(file.parent, expectedName))
                    }
                }
            }

            // Set timestamps on all output files to build time
            // This fixes the 1980-01-01 timestamp issue from GitHub Actions checkout
            val buildTime = System.currentTimeMillis()
            val outputDir = file(sourceDir)
            if (outputDir.exists()) {
                outputDir.walkTopDown().forEach { f ->
                    f.setLastModified(buildTime)
                }
                println("Updated timestamps in $sourceDir to build time")
            }
        }
    }
}

// Create all package types for all platform/architecture combinations with new naming convention

// Windows packages  
createJPackageTask("createWindowsExecutableX64", "windows", "x64", "exe")
createJPackageTask("createWindowsMsiX64", "windows", "x64", "msi")
createJPackageTask("createWindowsAppImageX64", "windows", "x64", "app-image")
val zipWindowsAppImageX64 = createZipTask("createWindowsAppImageX64", "windows", "x64")
createJPackageTask("createWindowsExecutableArm64", "windows", "arm64", "exe")
createJPackageTask("createWindowsMsiArm64", "windows", "arm64", "msi")
createJPackageTask("createWindowsAppImageArm64", "windows", "arm64", "app-image")
val zipWindowsAppImageArm64 = createZipTask("createWindowsAppImageArm64", "windows", "arm64")

// macOS packages  
createJPackageTask("createMacOSExecutableX64", "macos", "x64", "dmg")
createJPackageTask("createMacOSPkgX64", "macos", "x64", "pkg")
createJPackageTask("createMacOSAppImageX64", "macos", "x64", "app-image")
val zipMacOSAppImageX64 = createZipTask("createMacOSAppImageX64", "macos", "x64")
createJPackageTask("createMacOSExecutableArm64", "macos", "arm64", "dmg")
createJPackageTask("createMacOSPkgArm64", "macos", "arm64", "pkg")
createJPackageTask("createMacOSAppImageArm64", "macos", "arm64", "app-image")
val zipMacOSAppImageArm64 = createZipTask("createMacOSAppImageArm64", "macos", "arm64")

// Linux packages
createJPackageTask("createLinuxDebX64", "linux", "x64", "deb")
createJPackageTask("createLinuxRpmX64", "linux", "x64", "rpm")
createJPackageTask("createLinuxAppImageX64", "linux", "x64", "app-image")
val zipLinuxAppImageX64 = createZipTask("createLinuxAppImageX64", "linux", "x64")
createJPackageTask("createLinuxDebArm64", "linux", "arm64", "deb")
createJPackageTask("createLinuxRpmArm64", "linux", "arm64", "rpm")
createJPackageTask("createLinuxAppImageArm64", "linux", "arm64", "app-image")
val zipLinuxAppImageArm64 = createZipTask("createLinuxAppImageArm64", "linux", "arm64")

// Convenience tasks for creating all packages per platform
tasks.register("createWindowsPackages") {
    group = "distribution"
    description = "Create all Windows packages"
    dependsOn(
        "createWindowsExecutableX64", "createWindowsMsiX64", zipWindowsAppImageX64,
        "createWindowsExecutableArm64", "createWindowsMsiArm64", zipWindowsAppImageArm64
    )
}

tasks.register("createMacOSPackages") {
    group = "distribution"
    description = "Create all macOS packages"
    dependsOn(
        "createMacOSExecutableX64", "createMacOSPkgX64", zipMacOSAppImageX64,
        "createMacOSExecutableArm64", "createMacOSPkgArm64", zipMacOSAppImageArm64
    )
}

tasks.register("createLinuxPackages") {
    group = "distribution"
    description = "Create all Linux packages"
    dependsOn(
        "createLinuxDebX64", "createLinuxRpmX64", zipLinuxAppImageX64,
        "createLinuxDebArm64", "createLinuxRpmArm64", zipLinuxAppImageArm64
    )
}

tasks.register("createAllExecutables") {
    group = "distribution"
    description = "Create native packages for all platforms and architectures"
    dependsOn("createWindowsPackages", "createMacOSPackages", "createLinuxPackages")
}

// Task to create a distribution with all packages
tasks.register<Zip>("packageDistribution") {
    group = "distribution"
    description = "Package all executables into a distribution archive"
    dependsOn("createAllExecutables")
    
    from("build/dist")
    archiveFileName.set("FreeXmlToolkit-${project.version}-all-platforms.zip")
    destinationDirectory.set(file("build/distribution"))
    
    doFirst {
        file("build/distribution").mkdirs()
    }
    
    doLast {
        val zipFile = archiveFile.get().asFile
        println("Distribution ZIP created: ${zipFile.absolutePath} (${zipFile.length()} bytes)")
    }
}

// ===============================
// jlink Runtime Creation using configured JDK with JavaFX
// ===============================

// Helper function to create jlink runtime images using configured JDK
// IMPORTANT: jlink can only create runtimes for the host platform
fun createJlinkRuntimeTask(taskName: String, platform: String, arch: String) {
    val currentOs = when {
        System.getProperty("os.name").lowercase().contains("windows") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "macos"
        else -> "linux"
    }
    
    val currentArch = when {
        System.getProperty("os.arch").contains("aarch64") || System.getProperty("os.arch").contains("arm") -> "arm64"
        else -> "x64"
    }
    
    // Only create runtime for host platform/architecture
    if (platform != currentOs || arch != currentArch) {
        tasks.register(taskName) {
            group = "runtime"
            description = "Skip $platform-$arch runtime (can only create native runtime on $currentOs-$currentArch)"
            doFirst {
                println("⚠️ Skipping $platform-$arch runtime creation (cross-platform runtimes not supported)")
                println("💡 Current platform: $currentOs-$currentArch")
            }
        }
        return
    }
    
    tasks.register(taskName, Exec::class) {
        group = "runtime"
        description = "Create native jlink runtime image for $platform-$arch using configured JDK"
        dependsOn("jar")
        
        val runtimeDir = project.layout.buildDirectory.dir("runtime/$platform-$arch").get().asFile
        
        doFirst {
            delete(runtimeDir)
            println("🔨 Creating native runtime for $platform-$arch")
        }
        
        // Use configured JDK from toolchain (works with any JDK that includes JavaFX)
        val currentJavaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
        val jlinkCmd = if (currentOs == "windows") 
            "$currentJavaHome/bin/jlink.exe" else "$currentJavaHome/bin/jlink"
        val modulePath = "$currentJavaHome/jmods"
        
        // Required modules including JavaFX (works with any JDK that includes JavaFX)
        val requiredModules = setOf(
            "java.base",
            "java.desktop",
            "java.xml",
            "java.xml.crypto",
            "java.logging",
            "java.prefs",
            "java.sql",
            "java.naming",
            "jdk.crypto.ec",
            "jdk.httpserver",
            "jdk.jsobject",
            "javafx.base",
            "javafx.controls",
            "javafx.fxml",
            "javafx.graphics",
            "javafx.web",
            "javafx.swing"
        )
        
        commandLine(
            jlinkCmd,
            "--module-path", modulePath,
            "--add-modules", requiredModules.joinToString(","),
            "--output", runtimeDir.absolutePath,
            "--compress=zip-9",
            "--no-header-files",
            "--no-man-pages",
            "--strip-debug",
            "--strip-native-commands"
        )
        
        doLast {
            println("✅ Native runtime image created for $platform-$arch in ${runtimeDir.absolutePath}")
        }
    }
}

// Create jlink runtime tasks for all platforms
createJlinkRuntimeTask("createWindowsRuntimeX64", "windows", "x64")
createJlinkRuntimeTask("createWindowsRuntimeArm64", "windows", "arm64")
createJlinkRuntimeTask("createLinuxRuntimeX64", "linux", "x64")
createJlinkRuntimeTask("createLinuxRuntimeArm64", "linux", "arm64")
createJlinkRuntimeTask("createMacOSRuntimeX64", "macos", "x64")
createJlinkRuntimeTask("createMacOSRuntimeArm64", "macos", "arm64")

// Convenience task to create all runtime images
tasks.register("createAllRuntimes") {
    group = "runtime"
    description = "Create jlink runtime images for all platforms"
    dependsOn(
        "createWindowsRuntimeX64", "createWindowsRuntimeArm64",
        "createLinuxRuntimeX64", "createLinuxRuntimeArm64",
        "createMacOSRuntimeX64", "createMacOSRuntimeArm64"
    )
}

// Task to run XmlCodeEditorV2 Demo
tasks.register<JavaExec>("runEditorV2Demo") {
    group = "application"
    description = "Run XmlCodeEditorV2 Demo Application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.fxt.freexmltoolkit.demo.XmlCodeEditorV2Demo")
}

tasks.register<JavaExec>("runXmlViewsDemo") {
    group = "application"
    description = "Run XML Editor Views Demo (Tree/Grid/Text Views)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.fxt.freexmltoolkit.demo.XmlEditorViewsDemo")
}

tasks.register<JavaExec>("runSimpleGridDemo") {
    group = "application"
    description = "Run Simple Grid Demo (Tests XmlGridView directly)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.fxt.freexmltoolkit.demo.SimpleXmlGridDemo")
}

tasks.register<JavaExec>("runCanvasViewDemo") {
    group = "application"
    description = "Run XML Canvas View Demo (Canvas-based with embedded tables)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.fxt.freexmltoolkit.demo.XmlCanvasViewDemo")
}

// ===============================
// macOS Code Signing Tasks
// ===============================

// Helper function to create code signing tasks
fun createMacOSSigningTask(taskName: String, jpackageTaskName: String, arch: String, signType: String) {
    val currentOs = when {
        System.getProperty("os.name").lowercase().contains("mac") -> "macos"
        else -> "other"
    }

    // Only create signing tasks on macOS
    if (currentOs != "macos") {
        tasks.register(taskName) {
            group = "distribution"
            description = "Skip macOS code signing (can only sign on macOS)"
            doFirst {
                println("⚠️ Skipping macOS code signing (not running on macOS)")
            }
        }
        return
    }

    tasks.register(taskName, Exec::class) {
        group = "distribution"
        description = "Sign macOS DMG with $signType signing"
        dependsOn(jpackageTaskName)

        val scriptName = if (signType == "Developer ID") "sign-macos-dmg.sh" else "sign-macos-adhoc.sh"
        val version = project.version.toString()
        val dmgPath = "build/dist/macos-$arch-dmg/FreeXmlToolkit-$arch-$version.dmg"

        // Get signing identity from environment variable (for Developer ID signing)
        val signingIdentity = System.getenv("MACOS_SIGNING_IDENTITY") ?: ""

        doFirst {
            if (!file(dmgPath).exists()) {
                throw GradleException("DMG file not found: $dmgPath. Please run $jpackageTaskName first.")
            }

            val scriptPath = file("scripts/$scriptName")
            if (!scriptPath.exists()) {
                throw GradleException("Signing script not found: ${scriptPath.absolutePath}")
            }

            println("🔐 Signing DMG with $signType: $dmgPath")
        }

        if (signType == "Developer ID" && signingIdentity.isNotEmpty()) {
            commandLine("bash", "scripts/$scriptName", dmgPath, signingIdentity)
        } else {
            commandLine("bash", "scripts/$scriptName", dmgPath)
        }

        doLast {
            println("✅ DMG signed successfully: $dmgPath")
            println("")
            if (signType == "ad-hoc") {
                println("⚠️  Ad-hoc signing is for LOCAL TESTING ONLY")
                println("    This DMG cannot be distributed to other users")
            } else {
                println("ℹ️  Next step: Notarize the DMG with scripts/notarize-macos-dmg.sh")
            }
        }
    }
}

// Create ad-hoc signing tasks (for testing without Developer ID)
createMacOSSigningTask("signMacOSExecutableX64AdHoc", "createMacOSExecutableX64", "x64", "ad-hoc")
createMacOSSigningTask("signMacOSExecutableArm64AdHoc", "createMacOSExecutableArm64", "arm64", "ad-hoc")

// Create Developer ID signing tasks (requires Developer ID certificate)
createMacOSSigningTask("signMacOSExecutableX64", "createMacOSExecutableX64", "x64", "Developer ID")
createMacOSSigningTask("signMacOSExecutableArm64", "createMacOSExecutableArm64", "arm64", "Developer ID")

// Convenience task to sign all macOS packages with Developer ID
tasks.register("signAllMacOSPackages") {
    group = "distribution"
    description = "Sign all macOS DMG files with Developer ID"
    dependsOn("signMacOSExecutableX64", "signMacOSExecutableArm64")
}

// Convenience task for ad-hoc signing (testing)
tasks.register("signAllMacOSPackagesAdHoc") {
    group = "distribution"
    description = "Sign all macOS DMG files with ad-hoc signature (testing only)"
    dependsOn("signMacOSExecutableX64AdHoc", "signMacOSExecutableArm64AdHoc")
}

// Task to notarize macOS DMG files
tasks.register("notarizeMacOSDMG", Exec::class) {
    group = "distribution"
    description = "Notarize macOS DMG files with Apple (requires Apple Developer Account)"

    doFirst {
        val appleId = System.getenv("APPLE_ID")
        val teamId = System.getenv("APPLE_TEAM_ID")
        val appPassword = System.getenv("APPLE_APP_PASSWORD")

        if (appleId.isNullOrEmpty() || teamId.isNullOrEmpty() || appPassword.isNullOrEmpty()) {
            println("⚠️  Notarization requires environment variables:")
            println("    APPLE_ID - Your Apple Developer email")
            println("    APPLE_TEAM_ID - Your Apple Developer Team ID")
            println("    APPLE_APP_PASSWORD - App-specific password from appleid.apple.com")
            println("")
            println("Or run the script manually:")
            println("    ./scripts/notarize-macos-dmg.sh <dmg-file> <apple-id> <team-id> <app-password>")
            throw GradleException("Missing notarization credentials")
        }

        println("🍎 Starting notarization process...")
        println("   This may take several minutes...")

        val currentArch = when {
            System.getProperty("os.arch").contains("aarch64") || System.getProperty("os.arch")
                .contains("arm") -> "arm64"

            else -> "x64"
        }

        val version = project.version.toString()
        val dmgPath = "build/dist/macos-$currentArch-dmg/FreeXmlToolkit-$currentArch-$version.dmg"

        // Important: set command line at execution time (env vars may be null during configuration)
        commandLine(
            "bash",
            "scripts/notarize-macos-dmg.sh",
            dmgPath,
            appleId,
            teamId,
            appPassword
        )
    }
}

// Task to collect LOC data from git history
tasks.register<Exec>("collectLocData") {
    group = "reporting"
    description = "Collect LOC data from last 100 commits"

    doFirst {
        file("build/reports/loc").mkdirs()
        println("📊 Starting LOC data collection...")
    }

    commandLine("bash", "scripts/loc-analysis.sh")
}

// Task to assemble the LOC report from collected data
tasks.register<Exec>("assembleLocReport") {
    group = "reporting"
    description = "Assemble LOC report from collected data"

    dependsOn("collectLocData")

    commandLine("bash", "scripts/assemble-loc-report.sh")

    doLast {
        val reportFile = file("build/reports/loc/index.html")
        if (reportFile.exists()) {
            println("")
            println("✅ LOC report generated successfully!")
            println("📊 Report location: ${reportFile.absolutePath}")
            println("🌐 Open in browser: file://${reportFile.absolutePath}")
        } else {
            throw GradleException("Failed to generate LOC report")
        }
    }
}

// Main task to generate complete LOC report
tasks.register("generateLocReport") {
    group = "reporting"
    description = "Generate complete LOC analysis report for last 100 commits (Java only)"
    dependsOn("assembleLocReport")
}
