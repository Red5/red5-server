/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp.codec;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * RTMP codec factory.
 *
 * @author mondain
 */
public class RTMPMinaCodecFactory implements ProtocolCodecFactory, ApplicationContextAware, InitializingBean {

    protected ApplicationContext appCtx;

    /**
     * RTMP Mina protocol decoder.
     */
    protected RTMPMinaProtocolDecoder decoder = new RTMPMinaProtocolDecoder();

    /**
     * RTMP Mina protocol encoder.
     */
    protected RTMPMinaProtocolEncoder encoder = new RTMPMinaProtocolEncoder();

    /**
     * <p>afterPropertiesSet.</p>
     *
     * @throws java.lang.Exception if any.
     */
    public void afterPropertiesSet() throws Exception {
        decoder = (RTMPMinaProtocolDecoder) appCtx.getBean("minaDecoder");
        encoder = (RTMPMinaProtocolEncoder) appCtx.getBean("minaEncoder");
    }

    /** {@inheritDoc} */
    public ProtocolDecoder getDecoder(IoSession session) {
        return decoder;
    }

    /** {@inheritDoc} */
    public ProtocolEncoder getEncoder(IoSession session) {
        return encoder;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("null")
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        appCtx = applicationContext;
    }

}
