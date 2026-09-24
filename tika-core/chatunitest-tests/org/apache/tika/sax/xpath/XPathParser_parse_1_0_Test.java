package org.apache.tika.sax.xpath;

import org.apache.tika.sax.xpath.Matcher;
import org.apache.tika.sax.xpath.TextMatcher;
import org.apache.tika.sax.xpath.NodeMatcher;
import org.apache.tika.sax.xpath.CompositeMatcher;
import org.apache.tika.sax.xpath.ChildMatcher;
import org.apache.tika.sax.xpath.SubtreeMatcher;
import org.apache.tika.sax.xpath.AttributeMatcher;
import org.apache.tika.sax.xpath.ElementMatcher;
import org.apache.tika.sax.xpath.NamedAttributeMatcher;
import org.apache.tika.sax.xpath.NamedElementMatcher;
import org.apache.tika.sax.xpath.XPathParser;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;
import org.mockito.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;

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
