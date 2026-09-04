package org.red5.io.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Test;
import org.w3c.dom.Document;

/**
 * XML parser hardening tests (issue #458): DOCTYPE and entity expansion must be refused.
 */
public class XMLHardeningTest {

    @Test
    public void testInternalEntityExpansionIsRejected() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">]><red5>&lol2;</red5>";
        try {
            XMLUtils.stringToDoc(xml);
            fail("Document with DOCTYPE/internal entities must be rejected");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testExternalEntityIsRejected() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]><red5>&xxe;</red5>";
        try {
            XMLUtils.stringToDoc(xml);
            fail("Document with external entity must be rejected");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testPlainDocumentStillParses() throws IOException {
        Document doc = XMLUtils.stringToDoc("<?xml version=\"1.0\"?><red5><note>ok</note></red5>");
        assertEquals("red5", doc.getDocumentElement().getNodeName());
    }
}
