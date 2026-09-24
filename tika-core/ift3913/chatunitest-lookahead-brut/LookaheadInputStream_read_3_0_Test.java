package org.apache.tika.io;

import org.apache.tika.io.LookaheadInputStream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.io.InputStream;
import org.mockito.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class LookaheadInputStream_read_3_0_Test {

    @Mock
    private InputStream mockStream;

    private LookaheadInputStream lookaheadInputStream;

    @BeforeEach
    public void setUp() {
        lookaheadInputStream = new LookaheadInputStream(mockStream, 10);
    }

    @Test
    public void testReadWithAvailableData() throws IOException {
        when(mockStream.read(any(byte[].class), anyInt(), anyInt())).thenReturn(5);
        byte[] buffer = new byte[10];
        int result = lookaheadInputStream.read(buffer, 0, 5);
        assertEquals(5, result);
        verify(mockStream, times(1)).read(any(byte[].class), anyInt(), anyInt());
    }

    @Test
    public void testReadWithNoAvailableData() throws IOException {
        when(mockStream.read(any(byte[].class), anyInt(), anyInt())).thenReturn(-1);
        byte[] buffer = new byte[10];
        int result = lookaheadInputStream.read(buffer, 0, 5);
        assertEquals(-1, result);
        verify(mockStream, times(1)).read(any(byte[].class), anyInt(), anyInt());
    }

    @Test
    public void testReadWithException() throws IOException {
        when(mockStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException("Mocked exception"));
        byte[] buffer = new byte[10];
        assertThrows(IOException.class, () -> lookaheadInputStream.read(buffer, 0, 5));
        verify(mockStream, times(1)).read(any(byte[].class), anyInt(), anyInt());
    }
}
