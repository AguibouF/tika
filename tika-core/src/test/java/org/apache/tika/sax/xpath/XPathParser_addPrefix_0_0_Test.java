/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.sax.xpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class XPathParser_addPrefix_0_0_Test {

    private XPathParser parser;

    @BeforeEach
    public void setUp() {
        parser = new XPathParser();
    }

    @Test
    public void testAddPrefix() throws Exception {
        // Test adding a prefix
        parser.addPrefix("ns", "http://example.com/ns");
        Field prefixesField = XPathParser.class.getDeclaredField("prefixes");
        prefixesField.setAccessible(true);
        Map<String, String> prefixes = (Map<String, String>) prefixesField.get(parser);
        assertEquals("http://example.com/ns", prefixes.get("ns"));
        // Test adding a duplicate prefix
        parser.addPrefix("ns", "http://example.com/ns2");
        prefixes = (Map<String, String>) prefixesField.get(parser);
        assertEquals("http://example.com/ns2", prefixes.get("ns"));
        // Test adding a prefix with null namespace
        parser.addPrefix("ns2", null);
        prefixes = (Map<String, String>) prefixesField.get(parser);
        assertTrue(prefixes.containsKey("ns2"));
        assertEquals(null, prefixes.get("ns2"));
    }
}
