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

package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IdentityConstraint")
class IdentityConstraintTest {

    private IdentityConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = new IdentityConstraint();
    }

    @Nested
    @DisplayName("Type Enum")
    class TypeEnumTests {

        @Test
        @DisplayName("All three types are defined")
        void allTypesAreDefined() {
            assertEquals(3, IdentityConstraint.Type.values().length);
        }

        @Test
        @DisplayName("KEY type exists")
        void keyTypeExists() {
            assertEquals(IdentityConstraint.Type.KEY, IdentityConstraint.Type.valueOf("KEY"));
        }

        @Test
        @DisplayName("KEYREF type exists")
        void keyrefTypeExists() {
            assertEquals(IdentityConstraint.Type.KEYREF, IdentityConstraint.Type.valueOf("KEYREF"));
        }

        @Test
        @DisplayName("UNIQUE type exists")
        void uniqueTypeExists() {
            assertEquals(IdentityConstraint.Type.UNIQUE, IdentityConstraint.Type.valueOf("UNIQUE"));
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor initializes empty fields list")
        void defaultConstructorInitializesEmptyFieldsList() {
            IdentityConstraint empty = new IdentityConstraint();

            assertNull(empty.getType());
            assertNull(empty.getName());
            assertNull(empty.getSelector());
            assertNotNull(empty.getFields());
            assertTrue(empty.getFields().isEmpty());
            assertNull(empty.getRefer());
            assertNull(empty.getDocumentation());
        }

        @Test
        @DisplayName("Parameterized constructor sets type and name")
        void parameterizedConstructorSetsTypeAndName() {
            IdentityConstraint withParams = new IdentityConstraint(
                    IdentityConstraint.Type.KEY,
                    "primaryKey"
            );

            assertEquals(IdentityConstraint.Type.KEY, withParams.getType());
            assertEquals("primaryKey", withParams.getName());
            assertNotNull(withParams.getFields());
            assertTrue(withParams.getFields().isEmpty());
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("Set and get type")
        void setAndGetType() {
            constraint.setType(IdentityConstraint.Type.KEY);
            assertEquals(IdentityConstraint.Type.KEY, constraint.getType());

            constraint.setType(IdentityConstraint.Type.UNIQUE);
            assertEquals(IdentityConstraint.Type.UNIQUE, constraint.getType());
        }

        @Test
        @DisplayName("Set and get name")
        void setAndGetName() {
            constraint.setName("employeeKey");
            assertEquals("employeeKey", constraint.getName());
        }

        @Test
        @DisplayName("Set and get selector")
        void setAndGetSelector() {
            constraint.setSelector(".//employee");
            assertEquals(".//employee", constraint.getSelector());
        }

        @Test
        @DisplayName("Set and get fields")
        void setAndGetFields() {
            List<String> fields = Arrays.asList("@id", "name");
            constraint.setFields(fields);

            assertEquals(2, constraint.getFields().size());
            assertEquals("@id", constraint.getFields().get(0));
            assertEquals("name", constraint.getFields().get(1));
        }

        @Test
        @DisplayName("Add field")
        void addField() {
            constraint.addField("@id");
            constraint.addField("@department");

            assertEquals(2, constraint.getFields().size());
            assertTrue(constraint.getFields().contains("@id"));
            assertTrue(constraint.getFields().contains("@department"));
        }

        @Test
        @DisplayName("Set and get refer")
        void setAndGetRefer() {
            constraint.setRefer("departmentKey");
            assertEquals("departmentKey", constraint.getRefer());
        }

        @Test
        @DisplayName("Set and get documentation")
        void setAndGetDocumentation() {
            constraint.setDocumentation("Ensures unique employee IDs");
            assertEquals("Ensures unique employee IDs", constraint.getDocumentation());
        }
    }

    @Nested
    @DisplayName("getTypeDisplayName")
    class GetTypeDisplayNameTests {

        @Test
        @DisplayName("KEY returns 'Key'")
        void keyReturnsKey() {
            constraint.setType(IdentityConstraint.Type.KEY);
            assertEquals("Key", constraint.getTypeDisplayName());
        }

        @Test
        @DisplayName("KEYREF returns 'Key Reference'")
        void keyrefReturnsKeyReference() {
            constraint.setType(IdentityConstraint.Type.KEYREF);
            assertEquals("Key Reference", constraint.getTypeDisplayName());
        }

        @Test
        @DisplayName("UNIQUE returns 'Unique'")
        void uniqueReturnsUnique() {
            constraint.setType(IdentityConstraint.Type.UNIQUE);
            assertEquals("Unique", constraint.getTypeDisplayName());
        }
    }

    @Nested
    @DisplayName("isKeyRef")
    class IsKeyRefTests {

        @Test
        @DisplayName("Returns true for KEYREF")
        void returnsTrueForKeyref() {
            constraint.setType(IdentityConstraint.Type.KEYREF);
            assertTrue(constraint.isKeyRef());
        }

        @Test
        @DisplayName("Returns false for KEY")
        void returnsFalseForKey() {
            constraint.setType(IdentityConstraint.Type.KEY);
            assertFalse(constraint.isKeyRef());
        }

        @Test
        @DisplayName("Returns false for UNIQUE")
        void returnsFalseForUnique() {
            constraint.setType(IdentityConstraint.Type.UNIQUE);
            assertFalse(constraint.isKeyRef());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains type, name, selector, and fields")
        void toStringContainsBasicInfo() {
            constraint.setType(IdentityConstraint.Type.KEY);
            constraint.setName("productKey");
            constraint.setSelector(".//product");
            constraint.addField("@sku");

            String str = constraint.toString();
            assertTrue(str.contains("KEY"));
            assertTrue(str.contains("productKey"));
            assertTrue(str.contains(".//product"));
            assertTrue(str.contains("@sku"));
        }

        @Test
        @DisplayName("toString includes refer when present")
        void toStringIncludesReferWhenPresent() {
            constraint.setType(IdentityConstraint.Type.KEYREF);
            constraint.setName("categoryRef");
            constraint.setRefer("categoryKey");

            String str = constraint.toString();
            assertTrue(str.contains("refer"));
            assertTrue(str.contains("categoryKey"));
        }

        @Test
        @DisplayName("toString excludes refer when null")
        void toStringExcludesReferWhenNull() {
            constraint.setType(IdentityConstraint.Type.KEY);
            constraint.setName("testKey");

            String str = constraint.toString();
            assertFalse(str.contains("refer"));
        }
    }

    @Nested
    @DisplayName("Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Can serialize and deserialize")
        void canSerializeAndDeserialize() throws IOException, ClassNotFoundException {
            constraint.setType(IdentityConstraint.Type.KEY);
            constraint.setName("orderKey");
            constraint.setSelector(".//order");
            constraint.addField("@orderId");
            constraint.addField("@customerId");
            constraint.setDocumentation("Unique order identifier");

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(constraint);
            oos.close();

            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            IdentityConstraint deserialized = (IdentityConstraint) ois.readObject();
            ois.close();

            assertEquals(constraint.getType(), deserialized.getType());
            assertEquals(constraint.getName(), deserialized.getName());
            assertEquals(constraint.getSelector(), deserialized.getSelector());
            assertEquals(constraint.getFields(), deserialized.getFields());
            assertEquals(constraint.getDocumentation(), deserialized.getDocumentation());
        }

        @Test
        @DisplayName("Can serialize keyref with refer")
        void canSerializeKeyrefWithRefer() throws IOException, ClassNotFoundException {
            constraint.setType(IdentityConstraint.Type.KEYREF);
            constraint.setName("orderCustomerRef");
            constraint.setRefer("customerKey");
            constraint.setSelector(".//order");
            constraint.addField("@customerId");

            // Serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(constraint);
            oos.close();

            // Deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            IdentityConstraint deserialized = (IdentityConstraint) ois.readObject();
            ois.close();

            assertEquals("customerKey", deserialized.getRefer());
            assertTrue(deserialized.isKeyRef());
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("Employee ID primary key")
        void employeeIdPrimaryKey() {
            IdentityConstraint employeeKey = new IdentityConstraint(
                    IdentityConstraint.Type.KEY,
                    "employeeKey"
            );
            employeeKey.setSelector(".//employee");
            employeeKey.addField("@id");
            employeeKey.setDocumentation("Unique employee identifier");

            assertEquals(IdentityConstraint.Type.KEY, employeeKey.getType());
            assertEquals("Key", employeeKey.getTypeDisplayName());
            assertFalse(employeeKey.isKeyRef());
            assertEquals(1, employeeKey.getFields().size());
        }

        @Test
        @DisplayName("Order with composite key")
        void orderWithCompositeKey() {
            IdentityConstraint orderKey = new IdentityConstraint(
                    IdentityConstraint.Type.KEY,
                    "orderKey"
            );
            orderKey.setSelector(".//order");
            orderKey.addField("@orderId");
            orderKey.addField("@orderDate");
            orderKey.setDocumentation("Composite key: order ID and date");

            assertEquals(2, orderKey.getFields().size());
            assertTrue(orderKey.getFields().contains("@orderId"));
            assertTrue(orderKey.getFields().contains("@orderDate"));
        }

        @Test
        @DisplayName("Foreign key reference to department")
        void foreignKeyReferenceToDepartment() {
            IdentityConstraint deptRef = new IdentityConstraint(
                    IdentityConstraint.Type.KEYREF,
                    "employeeDeptRef"
            );
            deptRef.setSelector(".//employee");
            deptRef.addField("@deptId");
            deptRef.setRefer("departmentKey");
            deptRef.setDocumentation("Employee must belong to valid department");

            assertTrue(deptRef.isKeyRef());
            assertEquals("departmentKey", deptRef.getRefer());
            assertEquals("Key Reference", deptRef.getTypeDisplayName());
        }

        @Test
        @DisplayName("Unique constraint on email")
        void uniqueConstraintOnEmail() {
            IdentityConstraint emailUnique = new IdentityConstraint(
                    IdentityConstraint.Type.UNIQUE,
                    "emailUnique"
            );
            emailUnique.setSelector(".//user");
            emailUnique.addField("email");
            emailUnique.setDocumentation("Email addresses must be unique");

            assertEquals(IdentityConstraint.Type.UNIQUE, emailUnique.getType());
            assertEquals("Unique", emailUnique.getTypeDisplayName());
            assertFalse(emailUnique.isKeyRef());
            assertNull(emailUnique.getRefer());
        }

        @Test
        @DisplayName("Self-referencing keyref for manager")
        void selfReferencingKeyrefForManager() {
            IdentityConstraint managerRef = new IdentityConstraint(
                    IdentityConstraint.Type.KEYREF,
                    "managerRef"
            );
            managerRef.setSelector(".//employee");
            managerRef.addField("@managerId");
            managerRef.setRefer("employeeKey");
            managerRef.setDocumentation("Manager must be a valid employee");

            assertTrue(managerRef.isKeyRef());
            assertEquals("employeeKey", managerRef.getRefer());
        }
    }
}
