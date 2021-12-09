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
}

// sourceCompatibility = "17"
// targetCompatibility = "17"


javafx {
    version = "17.0.2-ea+1"
    modules("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation ("com.pixelduke:fxribbon:1.2.2")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}