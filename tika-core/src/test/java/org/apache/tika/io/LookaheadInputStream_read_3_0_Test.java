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
package org.apache.tika.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
