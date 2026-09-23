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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.junit.jupiter.api.Test;

/**
 * IFT3913 : tests ajoutés manuellement pour tuer les mutants PIT de
 * {@link LookaheadInputStream} qui survivent aux tests originaux et aux
 * tests générés par ChatUniTest.
 */
public class LookaheadInputStreamMutantsTest {

    /**
     * Flux qui ne renvoie jamais plus d'un octet par appel à read(byte[], int, int),
     * comme le ferait un flux réseau. Force plusieurs appels à fill().
     */
    private static class OneByteAtATimeInputStream extends ByteArrayInputStream {
        OneByteAtATimeInputStream(byte[] data) {
            super(data);
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            return super.read(b, off, Math.min(len, 1));
        }
    }

    /**
     * Mutants lignes 67-68 : la marque n'est plus posée sur le flux sous-jacent
     * à la construction.
     */
    @Test
    public void testCloseRewindsToPositionAtConstruction() throws IOException {
        InputStream stream = new ByteArrayInputStream(new byte[]{'a', 'b', 'c'});
        assertEquals('a', stream.read());
        InputStream lookahead = new LookaheadInputStream(stream, 2);
        assertEquals('b', lookahead.read());
        assertEquals('c', lookahead.read());
        lookahead.close();
        assertEquals('b', stream.read());
    }

    /**
     * Mutant ligne 82 : la place restante dans le buffer est mal calculée
     * lors d'un second remplissage.
     */
    @Test
    public void testLookaheadWithPartialReads() throws IOException {
        InputStream stream = new OneByteAtATimeInputStream(new byte[]{'a', 'b', 'c'});
        InputStream lookahead = new LookaheadInputStream(stream, 2);
        assertEquals('a', lookahead.read());
        assertEquals('b', lookahead.read());
        assertEquals(-1, lookahead.read());
        lookahead.close();
        assertEquals('a', stream.read());
    }

    /**
     * Mutants ligne 81 (limite) et ligne 86 : quand le flux sous-jacent est épuisé,
     * il est rembobiné automatiquement, sans attendre close().
     */
    @Test
    public void testEndOfUnderlyingStreamRewindsIt() throws IOException {
        InputStream stream = new ByteArrayInputStream(new byte[]{'a', 'b'});
        InputStream lookahead = new LookaheadInputStream(stream, 3);
        assertEquals('a', lookahead.read());
        assertEquals('b', lookahead.read());
        assertEquals(-1, lookahead.read());
        assertEquals('a', stream.read());
    }

    /**
     * Mutant ligne 81 (limite) : un buffer plein ne doit pas provoquer de lecture
     * supplémentaire sur le flux sous-jacent.
     */
    @Test
    public void testFullBufferDoesNotTouchUnderlyingStream() throws IOException {
        InputStream stream = new ByteArrayInputStream(new byte[]{'a', 'b'});
        InputStream lookahead = new LookaheadInputStream(stream, 2);
        assertEquals('a', lookahead.read());
        assertEquals('b', lookahead.read());
        assertEquals(-1, lookahead.read());
        assertEquals(-1, stream.read());
    }

    /**
     * Mutants lignes 105-107 : read(byte[], int, int) doit copier les octets
     * disponibles au bon endroit, sans dépasser le buffer, et avancer la position.
     */
    @Test
    public void testReadIntoArrayAfterSingleByteRead() throws IOException {
        InputStream stream = new ByteArrayInputStream(new byte[]{'a', 'b', 'c'});
        InputStream lookahead = new LookaheadInputStream(stream, 2);
        assertEquals('a', lookahead.read());
        byte[] b = new byte[4];
        assertEquals(1, lookahead.read(b, 1, 3));
        assertArrayEquals(new byte[]{0, 'b', 0, 0}, b);
        assertEquals(-1, lookahead.read());
    }
}
