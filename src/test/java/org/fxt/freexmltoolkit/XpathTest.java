/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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

import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;

public class XpathTest {

    XmlService xmlService = XmlServiceImpl.getInstance();

    File xmlFile = new File("src/test/resources/FundsXML_420.xml");
    File xsdFile = new File("src/test/resources/FundsXML_420.xsd");

    @Test
    void xPahtElementTest() {
        xmlService.setCurrentXmlFile(xsdFile);
        var s = xmlService.getXmlFromXpath("//xs:element[@name='OtherID']");
        System.out.println("s = " + s);
    }

    @Test
    void testDoubleElements() {
        // /FundsXML4/AssetMasterData/Asset/AssetDetails/Repo/Rate
        // Rate kommt öfters vor

        xmlService.setCurrentXmlFile(xsdFile);
        var s = xmlService.getXmlFromXpath("//xs:element[@name='Rate']");
        // /FundsXML4/AssetMasterData/Asset/AssetDetails/Repo/Rate
        System.out.println("s = " + s);

        s = xmlService.getXmlFromXpath("//xs:complexType[@name='ReposType']//xs:element[@name='Rate']");
        System.out.println("s = " + s);
    }

    @Test
    void xPathFromXml() {
        Assertions.assertNotNull(xmlService);
        xmlService.setCurrentXmlFile(xmlFile);

        Assertions.assertEquals(xmlService.getSchemaNameFromCurrentXMLFile().get(), "https://github.com/fundsxml/schema/releases/download/4.2.2/FundsXML.xsd");
        Assertions.assertEquals(xmlService.getCurrentXmlFile().getPath(), "src\\test\\resources\\FundsXML_420.xml");

        String expectedOutput = """
                <ControlData xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                   <UniqueDocumentID>EAM_FUND_001</UniqueDocumentID>
                   <DocumentGenerated>2021-11-30T16:14:04</DocumentGenerated>
                   <ContentDate>2021-11-30</ContentDate>
                   <DataSupplier>
                      <SystemCountry>AT</SystemCountry>
                      <Short>EAM</Short>
                      <Name>Erste Asset Management GmbH</Name>
                      <Type>Asset Manager</Type>
                      <Contact>
                         <Email>datamanagement@erste-am.com</Email>
                      </Contact>
                   </DataSupplier>
                   <DataOperation>INITIAL</DataOperation>
                   <Language>EN</Language>
                </ControlData>""";

        String result = xmlService.getXmlFromXpath("/FundsXML4/ControlData");

        Assertions.assertEquals(result.trim(), expectedOutput.trim());
    }

    @Test
    void xpathFromXsdTest() {
        xmlService.setCurrentXmlFile(xsdFile);
        var s = xmlService.getXmlFromXpath("//xs:complexType[@name='AccountType']");

        String expectedOutput = """
                <xs:complexType xmlns:altova="http://www.altova.com/xml-schema-extensions"
                                 xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                                 xmlns:xs="http://www.w3.org/2001/XMLSchema"
                                 name="AccountType">
                   <xs:annotation>
                      <xs:documentation>Master data of accounts</xs:documentation>
                   </xs:annotation>
                   <xs:sequence>
                      <xs:element minOccurs="0" name="IndicatorCreditDebit">
                         <xs:annotation>
                            <xs:documentation xml:lang="en">Account balance set as default</xs:documentation>
                            <xs:documentation xml:lang="de">Gibt an ob das Konto default einen Soll oder Habensaldo aufweist
                                    </xs:documentation>
                         </xs:annotation>
                         <xs:simpleType>
                            <xs:restriction base="xs:string">
                               <xs:enumeration value="Credit"/>
                               <xs:enumeration value="Debit"/>
                            </xs:restriction>
                         </xs:simpleType>
                      </xs:element>
                      <xs:element minOccurs="0" name="AccountNumber" type="xs:integer">
                         <xs:annotation>
                            <xs:documentation xml:lang="en">Account number used for booking procedure</xs:documentation>
                            <xs:documentation xml:lang="de">Gibt das bei einer Buchung verwendete Konto an</xs:documentation>
                         </xs:annotation>
                      </xs:element>
                      <xs:element minOccurs="0" name="InterestRateDebit" type="xs:decimal">
                         <xs:annotation>
                            <xs:documentation xml:lang="en">Debit rate</xs:documentation>
                            <xs:documentation xml:lang="de">Zinssatz Soll</xs:documentation>
                         </xs:annotation>
                      </xs:element>
                      <xs:element minOccurs="0" name="InterestRateCredit" type="xs:decimal">
                         <xs:annotation>
                            <xs:documentation xml:lang="en">Credit rate</xs:documentation>
                            <xs:documentation xml:lang="de">Zinssatz Haben</xs:documentation>
                         </xs:annotation>
                      </xs:element>
                      <xs:element name="Counterparty" type="CompanyType">
                         <xs:annotation>
                            <xs:documentation>Counterparty details</xs:documentation>
                         </xs:annotation>
                      </xs:element>
                   </xs:sequence>
                </xs:complexType>
                """;

        Assertions.assertEquals(s, expectedOutput);
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
                printNodeContent(currentNode);
            }
        }
    }
}
