/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.utils;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * <p>RandomGUID class.</p>
 *
 * @author mondain
 */
public class RandomGUID extends Object {

    private static final String hexChars = "0123456789ABCDEF";

    private RandomGUID() {
    }

    /**
     * Returns the 16 bytes of a UID, as AS3 mx.utils.UIDUtil.toByteArray does: only a UID, see {@link #isUID(String)}, has a byte form.
     *
     * @param uid
     *            unique id
     * @return array of bytes containing the id, or null if the id is null or not a UID
     */
    public final static byte[] toByteArray(String uid) {
        if (!isUID(uid)) {
            return null;
        }
        byte[] result = new byte[16];
        int r = 0;
        for (int i = 0; i < uid.length(); ++i) {
            if (uid.charAt(i) == '-') {
                continue;
            }
            int h1 = Character.digit(uid.charAt(i), 16);
            ++i;
            int h2 = Character.digit(uid.charAt(i), 16);
            result[(r++)] = (byte) ((h1 << 4 | h2) & 0xFF);
        }
        return result;
    }

    /**
     * Returns whether the id is a UID as AS3 mx.utils.UIDUtil.isUID defines it: 36 characters, hyphens at 8, 13, 18 and 23 and upper case
     * hex digits everywhere else. Only these ids read back from their byte form unchanged.
     *
     * @param uid
     *            id to check
     * @return true if the id is a UID
     */
    public static boolean isUID(String uid) {
        if (uid == null || uid.length() != 36) {
            return false;
        }
        for (int i = 0; i < 36; i++) {
            char c = uid.charAt(i);
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                if (c != '-') {
                    return false;
                }
            } else if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a uuid / guid for a given byte array.
     *
     * @param ba
     *            array of bytes containing the id
     * @return id
     */
    public static String fromByteArray(byte[] ba) {
        if ((ba != null) && (ba.length == 16)) {
            StringBuilder result = new StringBuilder(36);
            for (int i = 0; i < 16; ++i) {
                if ((i == 4) || (i == 6) || (i == 8) || (i == 10)) {
                    result.append('-');
                }
                result.append(hexChars.charAt(((ba[i] & 0xF0) >>> 4)));
                result.append(hexChars.charAt((ba[i] & 0xF)));
            }
            return result.toString();
        }
        return null;
    }

    /**
     * Returns a nice neat formatted string.
     *
     * @param str
     *            unformatted string
     * @return formatted string
     */
    public static String getPrettyFormatted(String str) {
        return String.format("%s-%s-%s-%s-%s", new Object[] { str.substring(0, 8), str.substring(8, 12), str.substring(12, 16), str.substring(16, 20), str.substring(20) });
    }

    /**
     * <p>create.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public static String create() {
        UUID id = UUID.randomUUID();

        byte[] bytes = ByteBuffer.allocate(16).putLong(id.getLeastSignificantBits()).putLong(id.getMostSignificantBits()).array();

        return fromByteArray(bytes);
    }
}
