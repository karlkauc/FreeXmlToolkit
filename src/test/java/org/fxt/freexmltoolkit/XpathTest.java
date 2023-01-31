package org.fxt.freexmltoolkit;

import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;

public class XpathTest {


    @Test
    void xpathTest1() {
        XmlService xmlService = XmlServiceImpl.getInstance();

        var f = new File("src/test/resources/test01.xml");

        assert (xmlService != null);
        xmlService.setCurrentXmlFile(f);
        assert (xmlService.getSchemaNameFromCurrentXMLFile().equals("https://fdp-service.oekb.at/FundsXML_4.1.7_AI.xsd"));
        assert (xmlService.getCurrentXmlFile().getPath().equals("src/test/resources/test01.xml"));

        try {

            FileInputStream fileIS = new FileInputStream(xmlService.getCurrentXmlFile());
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(fileIS);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "/FundsXML4/ControlData";
            var nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);

            Node elem = nodeList.item(0);//Your Node
            StringWriter buf = new StringWriter();
            Transformer xform = TransformerFactory.newInstance().newTransformer();
            xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"); // optional
            xform.setOutputProperty(OutputKeys.INDENT, "yes"); // optional
            xform.transform(new DOMSource(elem), new StreamResult(buf));
            System.out.println("buf.toString() = " + buf);


/*            System.out.println("nodeList = " + nodeList);
            for (int i = 0; i < nodeList.getLength(); i++) {

                var node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    printNodeContent(node);
                }
            }

 */

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    void printNodeContent(Node node) {
        System.out.println("node.getNodeType() = " + node.getLocalName());
        if (!node.hasChildNodes()) {
            System.out.println(node.getNodeName() + ":" + node.getTextContent());

        } else {
            System.out.println("node.getNodeName() = " + node.getNodeName());
        }


        NodeList nodeList = node.getChildNodes();
        for (int i = 0, len = nodeList.getLength(); i < len; i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                //calls this method for all the children which is Element
                printNodeContent(currentNode);
            }
        }
    }
}
