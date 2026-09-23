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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IFT3913 : tests ajoutés manuellement pour tuer les mutants PIT de
 * {@link XPathParser} qui survivent aux tests originaux et aux tests
 * générés par ChatUniTest.
 */
public class XPathParserMutantsTest {

    private static final String NS = "test namespace";

    private XPathParser parser;

    @BeforeEach
    public void setUp() {
        parser = new XPathParser();
        parser.addPrefix(null, null);
        parser.addPrefix("prefix", NS);
    }

    /**
     * Mutant ligne 48 : appel à addPrefix retiré du constructeur à deux arguments.
     */
    @Test
    public void testConstructorRegistersPrefix() {
        XPathParser withPrefix = new XPathParser("prefix", NS);
        Matcher matcher = withPrefix.parse("/prefix:name");
        assertTrue(matcher.descend(NS, "name").matchesElement());
        assertSame(Matcher.FAIL, matcher.descend(null, "name"));
    }

    /**
     * Mutant ligne 69 : la forme de compatibilité "/descendant:node()" n'est plus reconnue.
     */
    @Test
    public void testDescendantNodeCompatibilitySyntax() {
        Matcher matcher = parser.parse("/descendant:node()");
        assertTrue(matcher.matchesText());
        assertFalse(matcher.matchesElement());
        Matcher child = matcher.descend(NS, "name");
        assertTrue(child.matchesElement());
        assertTrue(child.descend(null, "deeper").matchesElement());
    }

    /**
     * Mutant ligne 87 : "return null" au lieu de Matcher.FAIL pour un préfixe d'attribut inconnu.
     */
    @Test
    public void testAttributeWithUnknownPrefixFails() {
        assertSame(Matcher.FAIL, parser.parse("/@unknown:name"));
    }

    /**
     * Mutant ligne 114 : "return null" au lieu de Matcher.FAIL pour une expression relative.
     */
    @Test
    public void testExpressionWithoutLeadingSlashFails() {
        assertSame(Matcher.FAIL, parser.parse("text()"));
    }
}
