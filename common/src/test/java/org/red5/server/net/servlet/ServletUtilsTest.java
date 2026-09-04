package org.red5.server.net.servlet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;

import org.junit.Test;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Request bodies must be copied with a bounded buffer and an enforced limit regardless of Content-Length (issue #452).
 */
public class ServletUtilsTest {

    static HttpServletRequest request(int declaredLength, byte[] body) {
        ServletInputStream in = new ServletInputStream() {
            private final ByteArrayInputStream delegate = new ByteArrayInputStream(body);

            @Override
            public int read() {
                return delegate.read();
            }

            @Override
            public boolean isFinished() {
                return delegate.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(), new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getContentLength":
                    return declaredLength;
                case "getContentLengthLong":
                    return (long) declaredLength;
                case "getInputStream":
                    return in;
                case "getMethod":
                    return "POST";
                case "getContentType":
                    return "application/x-fcs";
                default:
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    if (rt == long.class) {
                        return 0L;
                    }
                    return null;
            }
        });
    }

    @Test
    public void testHugeDeclaredLengthWithSmallBodyDoesNotAllocateDeclaredSize() throws IOException {
        byte[] body = "hello".getBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ServletUtils.copy(request(Integer.MAX_VALUE, body), out);
        } catch (OutOfMemoryError e) {
            fail("declared length was used to size the copy buffer");
        } catch (IOException expected) {
            // rejecting the declared length up front is also acceptable
            return;
        }
        assertArrayEquals(body, out.toByteArray());
    }

    @Test
    public void testBodyLongerThanLimitIsRejectedWhileStreaming() {
        byte[] body = new byte[(int) ServletUtils.MAX_REQUEST_BODY_SIZE + 1];
        try {
            // declared length lies about the size
            ServletUtils.copy(request(10, body), new ByteArrayOutputStream());
            fail("Expected IOException once the streaming limit is exceeded");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testNormalBodyIsCopied() throws IOException {
        byte[] body = "normal".getBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ServletUtils.copy(request(body.length, body), out);
        assertArrayEquals(body, out.toByteArray());
    }
}
