package org.red5.server.util;

import java.util.HashMap;

/**
 * Simple query string to map converter.
 *
 * @param <K>
 *            key
 * @param <V>
 *            value
 * @author Paul Gregoire
 */
@SuppressWarnings("serial")
public final class UrlQueryStringMap<K, V> extends HashMap<K, V> {

    /**
     * <p>Constructor for UrlQueryStringMap.</p>
     */
    public UrlQueryStringMap() {
        super();
    }

    /**
     * Parse a given query string and return an instance of this class.
     *
     * @param queryString
     *            query string
     * @return query string items as map entries
     */
    public static UrlQueryStringMap<String, String> parse(String queryString) {
        UrlQueryStringMap<String, String> map = new UrlQueryStringMap<String, String>();
        if (queryString == null || queryString.isEmpty()) {
            return map;
        }
        String tmp = queryString;
        int qmIndex = queryString.indexOf('?');
        if (qmIndex == 0) {
            tmp = queryString.substring(1);
        } else if (qmIndex > 0) {
            tmp = queryString.substring(qmIndex + 1);
        }
        //now break up into key/value blocks
        if (tmp.isEmpty()) {
            return map;
        }
        String[] kvs = tmp.split("&");
        //take each key/value block and break into its key value parts
        for (String kv : kvs) {
            if (kv.isEmpty()) {
                continue;
            }
            String[] split = kv.split("=", 2);
            String key = split[0];
            String value = (split.length > 1) ? split[1] : "";
            map.put(key, value);
        }
        return map;
    }

}
