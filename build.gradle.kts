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
    id("com.github.ben-manes.versions") version "0.53.0"
}

version = "1.0.0"
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
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED", "--enable-native-access=javafx.graphics")
    }

    withType<JavaExec> {
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED", "--enable-native-access=javafx.graphics")
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
    val zipTaskName = "${jpackageTaskName}Zip"
    
    tasks.register<Zip>(zipTaskName) {
        group = "distribution"
        description = "Create ZIP archive for $platform-$arch app-image"
        dependsOn(jpackageTaskName)
        
        val sourceDir = "build/dist/$platform-$arch-app-image"
        val zipFileName = "FreeXmlToolkit-$platform-$arch-app-image-${project.version}.zip"
        
        from(sourceDir)
        archiveFileName.set(zipFileName)
        destinationDirectory.set(file("build/dist"))
        
        doFirst {
            if (!file(sourceDir).exists()) {
                throw GradleException("Source directory $sourceDir does not exist")
            }
            println("Creating ZIP: $zipFileName from $sourceDir")
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
    val runtimeTaskName = "create${platform.replaceFirstChar { it.uppercaseChar() }}Runtime${arch.replaceFirstChar { it.uppercaseChar() }}"
    
    tasks.register(taskName, Exec::class) {
        group = "distribution"
        description = "Create $packageType package for $platform-$arch with custom runtime"
        dependsOn("jar", runtimeTaskName)
        
        doFirst {
            delete("build/dist/$platform-$arch-$packageType")
            mkdir("build/dist/$platform-$arch-$packageType")
        }
        
        val jpackageCmd = if (System.getProperty("os.name").lowercase().contains("windows")) "jpackage.exe" else "jpackage"
        
        // Determine app name based on package type and platform
        val appName = when {
            packageType == "app-image" -> "FreeXmlToolkit"
            platform == "linux" -> "freexmltoolkit"
            else -> "FreeXmlToolkit"
        }
        
        // Create arguments file to avoid command line length issues on Windows  
        val argsFile = File(project.layout.buildDirectory.asFile.get(), "jpackage-args-$platform-$arch-$packageType.txt")
        val iconPath = when (platform) {
            "windows" -> "release/logo.ico"
            "macos" -> "release/logo.icns"
            else -> "release/logo.ico"  // Use ICO as fallback for Linux
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
            
            // Check if icon exists before adding it to arguments
            // For app-image, icon is embedded in the executable - use project relative path
            val projectIconPath = File(project.rootDir, iconPath).absolutePath
            val iconArg = if (File(project.rootDir, iconPath).exists()) "--icon\n$projectIconPath" else ""
            
            // Use custom runtime if it exists
            val runtimePath = "build/runtime/$platform-$arch"
            val runtimeArg = if (File(runtimePath).exists()) "--runtime-image\n$runtimePath" else ""
            
            argsFile.writeText("""--type
$packageType
--input
build/libs
--dest
build/dist/$platform-$arch-$packageType
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
$runtimeArg
--java-options
--enable-preview
--java-options
--enable-native-access=ALL-UNNAMED
--java-options
--enable-native-access=javafx.graphics
$platformArgs""".trimIndent())
        }
        
        commandLine(jpackageCmd, "@${argsFile.absolutePath}")
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
                        val log4jTarget = File(appRootDir, "log4j2.xml")
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
fun createJlinkRuntimeTask(taskName: String, platform: String, arch: String) {
    tasks.register(taskName, Exec::class) {
        group = "runtime"
        description = "Create jlink runtime image for $platform-$arch using configured JDK"
        dependsOn("jar")
        
        val runtimeDir = "build/runtime/$platform-$arch"
        
        doFirst {
            delete(runtimeDir)
        }
        
        // Use configured JDK from toolchain (works with any JDK that includes JavaFX)
        val currentJavaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
        val jlinkCmd = if (System.getProperty("os.name").lowercase().contains("windows")) 
            "$currentJavaHome/bin/jlink.exe" else "$currentJavaHome/bin/jlink"
        val modulePath = "$currentJavaHome/jmods"
        
        // Required modules including JavaFX (works with any JDK that includes JavaFX)
        val requiredModules = setOf(
            "java.base",
            "java.desktop", 
            "java.xml",
            "java.logging",
            "java.prefs",
            "java.sql",
            "java.naming",
            "jdk.crypto.ec",
            "jdk.jsobject",
            "javafx.base",
            "javafx.controls",
            "javafx.fxml", 
            "javafx.graphics",
            "javafx.media",
            "javafx.web",
            "javafx.swing"
        )
        
        commandLine(
            jlinkCmd,
            "--module-path", modulePath,
            "--add-modules", requiredModules.joinToString(","),
            "--output", runtimeDir,
            "--compress=zip-6",
            "--no-header-files",
            "--no-man-pages"
        )
        
        doLast {
            println("✅ Runtime image created for $platform-$arch in $runtimeDir")
        }
    }
}

// Create jlink runtime tasks for all platforms
createJlinkRuntimeTask("createWindowsRuntimeX64", "windows", "x64")
createJlinkRuntimeTask("createLinuxRuntimeX64", "linux", "x64") 
createJlinkRuntimeTask("createMacOSRuntimeX64", "macos", "x64")
createJlinkRuntimeTask("createMacOSRuntimeAarch64", "macos", "aarch64")

// Convenience task to create all runtime images
tasks.register("createAllRuntimes") {
    group = "runtime"
    description = "Create jlink runtime images for all platforms"
    dependsOn(
        "createWindowsRuntimeX64",
        "createLinuxRuntimeX64", 
        "createMacOSRuntimeX64",
        "createMacOSRuntimeAarch64"
    )
}

