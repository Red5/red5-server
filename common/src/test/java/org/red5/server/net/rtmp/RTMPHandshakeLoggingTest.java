package org.red5.server.net.rtmp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.security.KeyPair;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * RTMPE key material must never reach the logs (issue #481).
 */
public class RTMPHandshakeLoggingTest {

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    private Level previous;

    private static class TestHandshake extends RTMPHandshake {

        TestHandshake() {
            super(RTMPConnection.RTMP_ENCRYPTED);
        }

        @Override
        protected void createHandshakeBytes() {
        }

        @Override
        public boolean validate(byte[] handshake) {
            return true;
        }

        @Override
        public IoBuffer doHandshake(IoBuffer input) {
            return null;
        }
    }

    @Before
    public void attach() {
        logger = (Logger) LoggerFactory.getLogger(TestHandshake.class);
        previous = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void detach() {
        logger.detachAppender(appender);
        logger.setLevel(previous);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }

    @Test
    public void testSharedSecretAndRc4KeysAreNotLogged() {
        TestHandshake hs = new TestHandshake();
        KeyPair mine = hs.generateKeyPair();
        assertNotNull(mine);
        byte[] myPublic = hs.getPublicKey(mine);
        // peer side
        TestHandshake peer = new TestHandshake();
        KeyPair theirs = peer.generateKeyPair();
        byte[] theirPublic = peer.getPublicKey(theirs);
        hs.outgoingPublicKey = myPublic;
        hs.incomingPublicKey = theirPublic;
        byte[] secret = hs.getSharedSecret(theirPublic, hs.keyAgreement);
        hs.initRC4Encryption(secret);
        String secretHex = Hex.encodeHexString(secret);
        for (String m : messages()) {
            String lower = m.toLowerCase();
            assertFalse("shared secret leaked: " + m, m.contains(secretHex));
            assertFalse("secret material labelled in log: " + m, lower.contains("shared secret") || lower.contains("rc4 out key") || lower.contains("rc4 in key"));
        }
    }
}
