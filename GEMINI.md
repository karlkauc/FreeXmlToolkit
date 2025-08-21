# FreeXmlToolkit - Gemini Development Environment

This document provides instructions and context for developing and interacting with the FreeXmlToolkit project using
Gemini.

## Project Overview

FreeXmlToolkit is a cross-platform desktop application built with Java and JavaFX for working with XML files. It
provides a comprehensive suite of tools for editing, validating, transforming, and signing XML documents. The project is
built using Gradle and is designed to be run on Windows, macOS, and Linux.

### Key Technologies

* **Java:** The core programming language for the application.
* **JavaFX:** Used for building the graphical user interface.
* **Gradle:** The build automation tool for managing dependencies and building the project.
* **jpackage:** Used for creating native installers and packages for different operating systems.
* **XML Technologies:** The application supports various XML technologies, including XSD, XPath, XSLT, and XML Digital
  Signatures.

## Building and Running

The project is built using Gradle. The following commands can be used to build and run the application:

* **Run the application:**
  ```bash
  ./gradlew run
  ```

* **Build the project:**
  ```bash
  ./gradlew build
  ```

* **Run tests:**
  ```bash
  ./gradlew test
  ```

* **Create native packages:**
  The project includes Gradle tasks for creating native packages for different operating systems. These tasks use the
  `jpackage` tool to create installers and application images.

    * **Create all packages:**
      ```bash
      ./gradlew createAllExecutables
      ```

    * **Create Windows packages:**
      ```bash
      ./gradlew createWindowsPackages
      ```

    * **Create macOS packages:**
      ```bash
      ./gradlew createMacOSPackages
      ```

    * **Create Linux packages:**
      ```bash
      ./gradlew createLinuxPackages
      ```

## Development Conventions

* **Code Style:** The project follows standard Java coding conventions.
* **Testing:** The project uses JUnit 5 for testing. Tests are located in the `src/test/java` directory.
* **Dependencies:** Dependencies are managed using Gradle. A list of dependencies can be found in the `build.gradle.kts`
  file.
* **Main Class:** The main entry point for the application is the `org.fxt.freexmltoolkit.FxtGui` class.

## Project Structure

The project is organized into the following directories:

* `src/main/java`: Contains the Java source code for the application.
* `src/main/resources`: Contains the resources for the application, such as FXML files, CSS files, images, and logging
  configuration.
* `src/test/java`: Contains the Java source code for the tests.
* `src/test/resources`: Contains the resources for the tests.
* `build`: Contains the build output.
* `release`: Contains files for creating the release packages.
* `.github/workflows`: Contains the GitHub Actions workflow for CI/CD.

## GUI

The graphical user interface is built using JavaFX and FXML. The main FXML file is located at
`src/main/resources/pages/main.fxml`. The UI is styled using CSS. The main CSS file is located at
`src/main/resources/css/fxt-theme.css`.

The main window is divided into a menu bar, a left menu with buttons for different functionalities, and a center content
pane where the different pages are loaded.

## Logging

The application uses Log4j 2 for logging. The configuration file is located at `src/main/resources/log4j2.xml`. It is
configured to log to both the console and a file.

## CI/CD

The project uses GitHub Actions for continuous integration and continuous delivery. The workflow is defined in the
`.github/workflows/build-packages-on-new-release.yml` file. The workflow is triggered when a new release is created. It
builds the application for Windows, macOS, and Linux, and uploads the packages as release assets.