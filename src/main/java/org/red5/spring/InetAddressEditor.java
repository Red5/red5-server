/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.spring;

import java.beans.PropertyEditorSupport;
import java.net.InetAddress;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom property editor for java.net.InetAddress class
 *
 * @author Rostislav Matl
 */
public class InetAddressEditor extends PropertyEditorSupport {

    private static Logger log = LoggerFactory.getLogger(InetAddressEditor.class);

    private static Pattern ipv4 = Pattern.compile("(([01][0-9][0-9]|2[0-4][0-9]|25[0-5])\\.){3}([01][0-9][0-9]|2[0-4][0-9]|25[0-5])");

    private static Pattern ipv6 = Pattern.compile("([0-9a-fA-F]{4}:){7}[0-9a-fA-F]{4}");

    @Override
    /** 
     * Converts String IP address to InetAddress object.
     *
     * @param textValue ex. "255.255.222.255"
     */
    public void setAsText(String textValue) {
        log.trace("setAsText: {}", textValue);
        InetAddress address = null;
        try {
            if (ipv4.matcher(textValue).matches()) {
                // IPv4 address
                String[] addressParts = textValue.split("\\.");
                assert addressParts.length == 4;

                byte[] addressBytes = new byte[addressParts.length];
                for (int i = 0; i < addressParts.length; i++) {
                    addressBytes[i] = (byte) Short.parseShort(addressParts[i]);
                }

                address = InetAddress.getByAddress(addressBytes);
            } else if (ipv6.matcher(textValue).matches()) {
                // IPv6 address
                String[] addressParts = textValue.split(":");
                assert addressParts.length == 8;

                byte[] addressBytes = new byte[addressParts.length * 2];
                for (int i = 0; i < addressParts.length; i++) {
                    addressBytes[i * 2] = Byte.parseByte(addressParts[i].substring(0, 2));
                    addressBytes[i * 2 + 1] = Byte.parseByte(addressParts[i].substring(2, 4));
                }

                address = InetAddress.getByAddress(addressBytes);
            } else {
                // host name
                address = InetAddress.getByName(textValue);
            }
        } catch (Exception ex) {
            log.error("Exception setting address: {}", textValue, ex);
            throw new IllegalArgumentException(ex);
        }
        setValue(address);
    }

    /**
     * Get text representation of the value.
     *
     * @return InetAddress text.
     */
    @Override
    public String getAsText() {
        return ((InetAddress) getValue()).getHostAddress();
    }

}