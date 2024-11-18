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

package org.fxt.freexmltoolkit;

import fr.brouillard.oss.cssfx.CSSFX;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.XmlEditor;

import java.io.File;

public class GuiTest extends Application {

    @Override
    public void start(Stage primaryStage) {

        try {
            CSSFX.start();

            TabPane tabPane = new TabPane();
            XmlEditor xmlEditor = new XmlEditor();

            xmlEditor.setXmlFile(new File("src/test/resources/EAM_7290691_20240829_FUND_101.xml"));
            xmlEditor.refresh();
            tabPane.getTabs().add(xmlEditor);
            var scene = new Scene(tabPane, 1024, 768);
            scene.getStylesheets().addAll("/scss/xml-highlighting.css", "/css/app.css");
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}
