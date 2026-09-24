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

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class XPathParser_parse_1_0_Test {

    private XPathParser parser;

    @BeforeEach
    public void setUp() {
        parser = new XPathParser();
    }

    @Test
    public void testParseText() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        Matcher result = (Matcher) method.invoke(parser, "/text()");
        assertEquals(TextMatcher.INSTANCE, result);
    }

    @Test
    public void testParseNode() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        Matcher result = (Matcher) method.invoke(parser, "/node()");
        assertEquals(NodeMatcher.INSTANCE, result);
    }

    @Test
    public void testParseDescendantNode() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        Matcher result = (Matcher) method.invoke(parser, "/descendant::node()");
        assertEquals(new CompositeMatcher(TextMatcher.INSTANCE, new ChildMatcher(new SubtreeMatcher(NodeMatcher.INSTANCE))), result);
    }

    @Test
    public void testParseAttribute() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        Matcher result = (Matcher) method.invoke(parser, "/@*");
        assertEquals(AttributeMatcher.INSTANCE, result);
    }

    @Test
    public void testParseEmpty() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        Matcher result = (Matcher) method.invoke(parser, "");
        assertEquals(ElementMatcher.INSTANCE, result);
    }

    @Test
    public void testParseNamedAttribute() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        parser.addPrefix("ns", "http://example.com");
        Matcher result = (Matcher) method.invoke(parser, "/@ns:name");
        assertEquals(new NamedAttributeMatcher("http://example.com", "name"), result);
    }

    @Test
    public void testParseChild() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        Matcher result = (Matcher) method.invoke(parser, "/*");
        assertEquals(new ChildMatcher(null), result);
    }

    @Test
    public void testParseFail() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        Matcher result = (Matcher) method.invoke(parser, "///");
        assertEquals(Matcher.FAIL, result);
    }

    @Test
    public void testParseSubtree() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        Matcher result = (Matcher) method.invoke(parser, "//element");
        assertEquals(new SubtreeMatcher(null), result);
    }

    @Test
    public void testParseNamedElement() throws Exception {
        Method method = XPathParser.class.getDeclaredMethod("parse", String.class);
        method.setAccessible(true);
        parser.addPrefix("ns", "http://example.com");
        Matcher result = (Matcher) method.invoke(parser, "/ns:element");
        assertEquals(new NamedElementMatcher("http://example.com", "element", null), result);
    }
}
