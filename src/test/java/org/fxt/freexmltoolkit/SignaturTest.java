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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jcajce.BCFKSLoadStoreParameter;
import org.fxt.freexmltoolkit.service.SignatureService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.security.Provider;
import java.security.Security;

public class SignaturTest {

    SignatureService signatureService = new SignatureService();
    private final static Logger logger = LogManager.getLogger(SignaturTest.class);


    @Test
    public void testAllProviders() {
        for (Provider provider : Security.getProviders()) {
            System.out.println("Provider: " + provider.getName());
            for (Provider.Service service : provider.getServices()) {
                if (service.getType().equals("KeyPairGenerator")) {
                    System.out.println("  Algorithm: " + service.getAlgorithm());
                }
            }
        }

        for (var x : BCFKSLoadStoreParameter.SignatureAlgorithm.values()) {
            System.out.println("x.name() = " + x.name());
        }
    }

    @Test
    public void createNewSignatureFile() {
        signatureService.createNewKeystoreFile(null, "karl", "123", "123");
    }

    @Test
    public void signDocumentTest() {
        File fileToCheck = new File("src/test/resources/FundsXML_428.xml");
        File keyFile = new File("release/examples/certs/karl/karl_Keystore.jks");
        final String password = "123";
        final String outputFileName = "signed_document.xml";
        final String alias = "karl";

        if (new File(outputFileName).exists()) {
            new File(outputFileName).delete();
        }

        var outputFile = signatureService.signDocument(fileToCheck, keyFile, password, alias, password, outputFileName);
        logger.debug("OutFile: {}", outputFile.getAbsoluteFile());
    }

    @Test
    public void verifySignatureTest() {
        File fileToCheck = new File("signed_document.xml");
        File keyFile = new File("release/examples/certs/karl/karl_Keystore.jks");
        final String password = "123";
        final String alias = "karl";

        var isOk = signatureService.isSignatureValid(fileToCheck);
        logger.debug("Is File signed correct: {}", isOk);

        Assertions.assertTrue(isOk);
    }

    @Test
    public void testBoth() {
        signDocumentTest();
        verifySignatureTest();
    }


}
