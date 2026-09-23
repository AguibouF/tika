package org.apache.tika.io;

import org.apache.tika.io.LookaheadInputStream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import org.mockito.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;

@ExtendWith(MockitoExtension.class)
public class LookaheadInputStream_markSupported_6_0_Test {

    @Mock
    private InputStream stream;

    private LookaheadInputStream lookaheadInputStream;

    @BeforeEach
    public void setUp() {
        lookaheadInputStream = new LookaheadInputStream(stream, 10);
    }

    @Test
    public void testMarkSupported() throws IOException {
        assertTrue(lookaheadInputStream.markSupported());
    }
}
