package org.fxt.freexmltoolkit;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class MainController {



    @FXML
    private Parent tabXml, tabXslt;

    @FXML
    private XmlController xmlController;

    @FXML
    Tab tabPaneXml, tabPaneXslt;

    @FXML
    Button prettyPrint;

    @FXML
    Button exit;

    @FXML
    private void initialize() {
        FXMLLoader fxmlLoader = new FXMLLoader();
        try {
            fxmlLoader.load(getClass().getResource("tab_xml.fxml").openStream());
            xmlController = fxmlLoader.getController();
        } catch (IOException e) {
            e.printStackTrace();
        }

        exit.setOnAction(e -> System.exit(0));

        prettyPrint.setOnAction(event -> {
            System.out.println("Hello World!");
            System.out.println("event.getSource() = " + event.getSource());
            System.out.println("tabPaneXml.isSelected() = " + tabPaneXml.isSelected());
            System.out.println("tabPaneXslt.isSelected() = " + tabPaneXslt.isSelected());

            if (tabPaneXml.isSelected()) {
                if (xmlController != null) {
                    String f = xmlController.getXMLContent();
                    System.out.println("f = " + f);

                    System.out.println(prettyFormat(f, 2));

                }
            }
        });
    }


    public static String prettyFormat(String input, int indent) {
        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            // transformerFactory.setAttribute("indent-number", indent);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch (Exception e) {
            throw new RuntimeException(e); // simple exception handling, please review it
        }
    }

}
