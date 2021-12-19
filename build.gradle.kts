plugins {
    java
    idea
    application
    id("org.openjfx.javafxplugin") version "0.0.10"
    id("edu.sc.seis.launch4j") version "2.5.1"
    id("com.github.ben-manes.versions") version "0.39.0"
}

application {
    mainModule.set("org.fxt.freexmltoolkit")
    mainClass.set("org.fxt.freexmltoolkit.FxtGui")
}

group = "org.fxt"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.eclipse.org/content/groups/releases/")
    }
}

// sourceCompatibility = "17"
// targetCompatibility = "17"


javafx {
    version = "17.0.2-ea+1"
    modules("javafx.controls", "javafx.fxml", "javafx.web")
}

dependencies {
    // XSLT
    implementation("net.sf.saxon:Saxon-HE:10.6")

    // Richtext
    implementation("org.fxmisc.richtext:richtextfx:0.10.7")

    // Logging
    // implementation("org.slf4j:log4j-over-slf4j:2.0.0-alpha5")
    // implementation("org.slf4j:slf4j-log4j12:2.0.0-alpha5")

    implementation("org.apache.logging.log4j:log4j-api:2.16.0")
    implementation("org.apache.logging.log4j:log4j-core:2.16.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.16.0")

    implementation("org.apache.commons:commons-lang3:3.12.0")


    // Lemminx
    /*implementation("org.eclipse.lemminx:org.eclipse.lemminx:0.18.2") {
        exclude(group = "xml.apis", module = "xml.apis")
        exclude(group ="java.xml", module ="java.xml")
    }
     */
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

configurations {
    all {
        exclude(group = "xml.apis", module = "xml.apis")
        exclude(group = "java.xml", module = "java.xml")
    }
}



tasks.named<Test>("test") {
    useJUnitPlatform()
}