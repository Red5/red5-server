package org.red5.io.tls;

import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * TLSFactory must never log store passwords or key material (issue #481).
 */
public class TLSFactoryLoggingTest {

    private static final String KS_PASS = "s3cretKeyStorePass";

    private static final String TS_PASS = "s3cretTrustStorePass";

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    private Level previous;

    @Before
    public void attach() {
        logger = (Logger) LoggerFactory.getLogger(TLSFactory.class);
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

    private void assertNoSecrets() {
        for (String m : messages()) {
            String lower = m.toLowerCase();
            assertFalse("password leaked: " + m, m.contains(KS_PASS) || m.contains(TS_PASS));
            assertFalse("password field logged: " + m, lower.contains("password:") || lower.contains("passphrase:"));
            assertFalse("private key logged: " + m, lower.contains("private key:"));
        }
    }

    @Test
    public void testPathOverloadDoesNotLogPasswords() {
        try {
            TLSFactory.getTLSContext("PKCS12", KS_PASS, "/nonexistent/keystore.p12", TS_PASS, "/nonexistent/truststore.p12");
        } catch (Exception expected) {
        }
        assertNoSecrets();
    }

    @Test
    public void testPassphraseOverloadDoesNotLogPasswords() {
        String ks = TLSFactory.getKeystorePath();
        String ts = TLSFactory.getTruststorePath();
        try {
            TLSFactory.setKeystorePath("/nonexistent/keystore.p12");
            TLSFactory.setTruststorePath("/nonexistent/truststore.p12");
            TLSFactory.getTLSContext("PKCS12", KS_PASS.toCharArray());
        } catch (Exception expected) {
        } finally {
            TLSFactory.setKeystorePath(ks);
            TLSFactory.setTruststorePath(ts);
        }
        assertNoSecrets();
    }

    @Test
    public void testStreamOverloadDoesNotLogPasswords() {
        try {
            TLSFactory.getTLSContext("PKCS12", KS_PASS.toCharArray(), new ByteArrayInputStream(new byte[0]), TS_PASS.toCharArray(), new ByteArrayInputStream(new byte[0]));
        } catch (Exception expected) {
        }
        assertNoSecrets();
    }
}
