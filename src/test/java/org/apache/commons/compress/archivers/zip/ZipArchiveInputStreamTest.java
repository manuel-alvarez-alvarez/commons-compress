/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
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

package org.apache.commons.compress.archivers.zip;

import static org.apache.commons.compress.AbstractTestCase.getFile;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Test;

public class ZipArchiveInputStreamTest {

    /**
     * @see "https://issues.apache.org/jira/browse/COMPRESS-176"
     */
    @Test
    public void winzipBackSlashWorkaround() throws Exception {
        ZipArchiveInputStream in = null;
        try {
            in = new ZipArchiveInputStream(new FileInputStream(getFile("test-winzip.zip")));
            ZipArchiveEntry zae = in.getNextZipEntry();
            zae = in.getNextZipEntry();
            zae = in.getNextZipEntry();
            assertEquals("\u00e4/", zae.getName());
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * @see "https://issues.apache.org/jira/browse/COMPRESS-189"
     */
    @Test
    public void properUseOfInflater() throws Exception {
        ZipFile zf = null;
        ZipArchiveInputStream in = null;
        try {
            zf = new ZipFile(getFile("COMPRESS-189.zip"));
            final ZipArchiveEntry zae = zf.getEntry("USD0558682-20080101.ZIP");
            in = new ZipArchiveInputStream(new BufferedInputStream(zf.getInputStream(zae)));
            ZipArchiveEntry innerEntry;
            while ((innerEntry = in.getNextZipEntry()) != null) {
                if (innerEntry.getName().endsWith("XML")) {
                    assertTrue(0 < in.read());
                }
            }
        } finally {
            if (zf != null) {
                zf.close();
            }
            if (in != null) {
                in.close();
            }
        }
    }

    @Test
    public void shouldConsumeArchiveCompletely() throws Exception {
        final InputStream is = ZipArchiveInputStreamTest.class
            .getResourceAsStream("/archive_with_trailer.zip");
        final ZipArchiveInputStream zip = new ZipArchiveInputStream(is);
        while (zip.getNextZipEntry() != null) {
            // just consume the archive
        }
        final byte[] expected = new byte[] {
            'H', 'e', 'l', 'l', 'o', ',', ' ', 'w', 'o', 'r', 'l', 'd', '!', '\n'
        };
        final byte[] actual = new byte[expected.length];
        is.read(actual);
        assertArrayEquals(expected, actual);
        zip.close();
    }

    /**
     * @see "https://issues.apache.org/jira/browse/COMPRESS-219"
     */
    @Test
    public void shouldReadNestedZip() throws IOException {
        ZipArchiveInputStream in = null;
        try {
            in = new ZipArchiveInputStream(new FileInputStream(getFile("COMPRESS-219.zip")));
            extractZipInputStream(in);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private void extractZipInputStream(final ZipArchiveInputStream in)
        throws IOException {
        ZipArchiveEntry zae = in.getNextZipEntry();
        while (zae != null) {
            if (zae.getName().endsWith(".zip")) {
                extractZipInputStream(new ZipArchiveInputStream(in));
            }
            zae = in.getNextZipEntry();
        }
    }

    @Test
    public void testUnshrinkEntry() throws Exception {
        final ZipArchiveInputStream in = new ZipArchiveInputStream(new FileInputStream(getFile("SHRUNK.ZIP")));
        
        ZipArchiveEntry entry = in.getNextZipEntry();
        assertEquals("method", ZipMethod.UNSHRINKING.getCode(), entry.getMethod());
        assertTrue(in.canReadEntryData(entry));
        
        FileInputStream original = new FileInputStream(getFile("test1.xml"));
        try {
            assertArrayEquals(IOUtils.toByteArray(original), IOUtils.toByteArray(in));
        } finally {
            original.close();
        }
        
        entry = in.getNextZipEntry();
        assertEquals("method", ZipMethod.UNSHRINKING.getCode(), entry.getMethod());
        assertTrue(in.canReadEntryData(entry));
        
        original = new FileInputStream(getFile("test2.xml"));
        try {
            assertArrayEquals(IOUtils.toByteArray(original), IOUtils.toByteArray(in));
        } finally {
            original.close();
        }
    }


    /**
     * Test case for 
     * <a href="https://issues.apache.org/jira/browse/COMPRESS-264"
     * >COMPRESS-264</a>.
     */
    @Test
    public void testReadingOfFirstStoredEntry() throws Exception {
        final ZipArchiveInputStream in = new ZipArchiveInputStream(new FileInputStream(getFile("COMPRESS-264.zip")));
        
        try {
            final ZipArchiveEntry ze = in.getNextZipEntry();
            assertEquals(5, ze.getSize());
            assertArrayEquals(new byte[] {'d', 'a', 't', 'a', '\n'},
                              IOUtils.toByteArray(in));
        } finally {
            in.close();
        }
    }

    /**
     * Test case for
     * <a href="https://issues.apache.org/jira/browse/COMPRESS-351"
     * >COMPRESS-351</a>.
     */
    @Test
    public void testMessageWithCorruptFileName() throws Exception {
        final ZipArchiveInputStream in = new ZipArchiveInputStream(new FileInputStream(getFile("COMPRESS-351.zip")));
        try {
            ZipArchiveEntry ze = in.getNextZipEntry();
            while (ze != null) {
                ze = in.getNextZipEntry();
            }
            fail("expected EOFException");
        } catch (EOFException ex) {
            String m = ex.getMessage();
            assertTrue(m.startsWith("Truncated ZIP entry: ?2016")); // the first character is not printable
        } finally {
            in.close();
        }
    }

    @Test
    public void testUnzipBZip2CompressedEntry() throws Exception {
        final ZipArchiveInputStream in = new ZipArchiveInputStream(new FileInputStream(getFile("bzip2-zip.zip")));
        
        try {
            final ZipArchiveEntry ze = in.getNextZipEntry();
            assertEquals(42, ze.getSize());
            final byte[] expected = new byte[42];
            Arrays.fill(expected , (byte)'a');
            assertArrayEquals(expected, IOUtils.toByteArray(in));
        } finally {
            in.close();
        }
    }

    @Test
    public void singleByteReadThrowsAtEofForCorruptedStoredEntry() throws Exception {
        byte[] content;
        FileInputStream fs = null;
        try {
            fs = new FileInputStream(getFile("COMPRESS-264.zip"));
            content = IOUtils.toByteArray(fs);
        } finally {
            IOUtils.closeQuietly(fs);
        }
        // make size much bigger than entry's real size
        for (int i = 17; i < 26; i++) {
            content[i] = (byte) 0xff;
        }
        ByteArrayInputStream in = null;
        ZipArchiveInputStream archive = null;
        try {
            in = new ByteArrayInputStream(content);
            archive = new ZipArchiveInputStream(in);
            ArchiveEntry e = archive.getNextEntry();
            try {
                IOUtils.toByteArray(archive);
                fail("expected exception");
            } catch (IOException ex) {
                assertEquals("Truncated ZIP file", ex.getMessage());
            }
            try {
                archive.read();
                fail("expected exception");
            } catch (IOException ex) {
                assertEquals("Truncated ZIP file", ex.getMessage());
            }
            try {
                archive.read();
                fail("expected exception");
            } catch (IOException ex) {
                assertEquals("Truncated ZIP file", ex.getMessage());
            }
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(archive);
        }
    }

    @Test
    public void multiByteReadThrowsAtEofForCorruptedStoredEntry() throws Exception {
        byte[] content;
        FileInputStream fs = null;
        try {
            fs = new FileInputStream(getFile("COMPRESS-264.zip"));
            content = IOUtils.toByteArray(fs);
        } finally {
            IOUtils.closeQuietly(fs);
        }
        // make size much bigger than entry's real size
        for (int i = 17; i < 26; i++) {
            content[i] = (byte) 0xff;
        }
        byte[] buf = new byte[2];
        ByteArrayInputStream in = null;
        ZipArchiveInputStream archive = null;
        try {
            in = new ByteArrayInputStream(content);
            archive = new ZipArchiveInputStream(in);
            ArchiveEntry e = archive.getNextEntry();
            try {
                IOUtils.toByteArray(archive);
                fail("expected exception");
            } catch (IOException ex) {
                assertEquals("Truncated ZIP file", ex.getMessage());
            }
            try {
                archive.read(buf);
                fail("expected exception");
            } catch (IOException ex) {
                assertEquals("Truncated ZIP file", ex.getMessage());
            }
            try {
                archive.read(buf);
                fail("expected exception");
            } catch (IOException ex) {
                assertEquals("Truncated ZIP file", ex.getMessage());
            }
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(archive);
        }
    }
}
