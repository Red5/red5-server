package org.red5.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertiesReader {

    private static final Logger log = LoggerFactory.getLogger(PropertiesReader.class);

    private static Properties props;

    static {
        log.debug("static init");
        props = new Properties();
        Path path;
        try {
            path = Paths.get("target/test-classes/test.properties");
            if (Files.exists(path)) {
                props.load(Files.newInputStream(path));
            } else {
                log.info("No test.properties file found, using defaults");
                props.setProperty("server", "localhost");
                props.setProperty("port", "1935");
                props.setProperty("app", "oflaDemo");
                props.setProperty("name", "Avengers2.mp4");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getProperty(String key) {
        log.debug("getProperty: {} props: {}", key, props);
        String value = props.getProperty(key);
        if (value == null) {
            // try it without a prefix
            value = props.getProperty(key.substring(key.indexOf('.') + 1));
        }
        return value;
    }

    public static Properties getProperties(String propsPath) {
        Properties props = null;
        Path path;
        try {
            path = Paths.get(propsPath);
            if (Files.exists(path)) {
                props = new Properties();
                props.load(Files.newInputStream(path));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return props;
    }

}
