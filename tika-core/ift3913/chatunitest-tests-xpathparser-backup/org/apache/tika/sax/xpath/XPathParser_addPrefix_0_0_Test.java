package org.apache.tika.sax.xpath;

import org.apache.tika.sax.xpath.XPathParser;
import java.lang.reflect.Field;
import org.mockito.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.HashMap;
import java.util.Map;

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
