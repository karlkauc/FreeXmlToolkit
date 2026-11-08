// A sample FreeXmlToolkit plugin. Build with:  gradle jar -PfxtJar=/path/to/FreeXmlToolkit.jar
// and copy build/libs/orders-plugin.jar into ~/.freeXmlToolkit/plugins.
plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// The FreeXmlToolkit jar of an installation (app/FreeXmlToolkit.jar) or of a source build
// (build/libs/FreeXmlToolkit-<version>.jar). It is provided at run time, so compileOnly.
val fxtJar = (findProperty("fxtJar") as String?) ?: "../../build/libs/FreeXmlToolkit-2.10.0.jar"

dependencies {
    compileOnly(files(fxtJar))
    // JavaFX comes with the application's runtime; compile against the same version
    compileOnly("org.openjfx:javafx-controls:25:linux")
    testImplementation(files(fxtJar))
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

tasks.jar {
    archiveFileName.set("orders-plugin.jar")
}
