plugins {
    java
    idea
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("edu.sc.seis.launch4j") version "2.5.3"
    id("com.github.ben-manes.versions") version "0.42.0"

    id("org.beryx.jlink") version "2.25.0"
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
    version = "18.0.2"
    modules("javafx.controls", "javafx.fxml", "javafx.web")
}

run {
    // jvmArgs = [" -Xmx8g", "-Dfile.encoding=utf-8"]
}

dependencies {
    // XSLT
    implementation("net.sf.saxon:Saxon-HE:11.4") {
        exclude(group = "xml-apis", module = "xml-apis")
    }

    // Icons
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-bootstrapicons-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-win10-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.3.1")
    // https://kordamp.org/ikonli/cheat-sheet-bootstrapicons.html

    // Richtext
    implementation("org.fxmisc.richtext:richtextfx:0.10.9")

    // Logging
    implementation("org.apache.logging.log4j:log4j-api:2.18.0")
    implementation("org.apache.logging.log4j:log4j-core:2.18.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.18.0")

    // XSD Parser
    implementation("com.github.xmlet:xsdParser:1.2.4")

    //  xml signature
    implementation("org.apache.santuario:xmlsec:3.0.0")

    // Lemminx
    implementation("org.eclipse.lemminx:org.eclipse.lemminx:0.21.0")

    // FOP
    implementation("org.apache.xmlgraphics:fop:2.7")

    // Injection
    implementation("com.google.inject:guice:5.1.0")

    // Misc
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("commons-io:commons-io:2.11.0")

    // Hardware Info
    implementation("com.github.oshi:oshi-core:6.2.2")


    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}


tasks.withType<edu.sc.seis.launch4j.tasks.DefaultLaunch4jTask> {
    outfile = "FreeXMLToolkit.exe"
    mainClassName = "org.fxt.freexmltoolkit.FxtGui" // SolvencyUI
    headerType = "gui" // gui / console
    // icon = "${projectDir}/ico/logo.ico"
    maxHeapSize = 2048
    copyright = System.getProperty("user.name")

    // https://bell-sw.com/pages/libericajdk/
    bundledJrePath = "jdk"
    bundledJre64Bit = true
    jreMinVersion = "18"

    doLast {
        println("Copy resources...")
        copy {
            from(
                layout.buildDirectory.file("resources/log4j2.xml")
            )
            into(layout.buildDirectory.dir("launch4j"))
        }
        println("Copy JDK...")
        copy {
            from(zipTree("jdk/jre-18.0.1-full.zip"))
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

jlink {
    launcher {
        name = "hello"
        forceMerge("log4j-api")
    }
}