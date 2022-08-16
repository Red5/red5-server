/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska.dtd;

import java.io.InputStream;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import org.red5.io.matroska.ConverterException;
import org.red5.io.matroska.VINT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * factory for creating matroska tags, it use property file - matroska_type_definition_config.properties with structure: long id = "name provided specification","java class representing tag data"
 */
public class TagFactory {
    private static Logger log = LoggerFactory.getLogger(TagFactory.class);

    private static final Map<Long, NameTag> tagsById = new Hashtable<>();

    private static final Map<String, IdTag> tagsByName = new Hashtable<>();

    static {
        Properties props = new Properties();
        try (InputStream input = TagFactory.class.getResourceAsStream("matroska_type_by_id_definition.properties")) {
            props.load(input);
            log.trace("Properties are loaded");
            for (Map.Entry<Object, Object> e : props.entrySet()) {
                if (log.isTraceEnabled()) {
                    log.trace("Processing property: {} -> {}", e.getKey(), e.getValue());
                }
                Long id = Long.valueOf("" + e.getKey(), 16);
                NameTag nt = new NameTag(e.getValue());
                tagsById.put(id, nt);
                tagsByName.put(nt.name, new IdTag(id, nt.clazz));
            }
        } catch (Exception e) {
            log.error("Unexpected exception while reading properties", e);
        }
    }

    public static Tag createTag(VINT id, VINT size, InputStream inputStream) throws ConverterException {
        NameTag nt = tagsById.get(id.getBinary());
        if (null == nt) {
            throw new ConverterException("not supported matroska tag: " + id.getBinary());
        }
        try {
            return (Tag) nt.clazz.getConstructor(String.class, VINT.class, VINT.class, InputStream.class).newInstance(nt.name, id, size, inputStream);
        } catch (Exception e) {
            log.error("Unexpected exception while creating tag", e);
        }

        return null;
    }

    public static Tag createTag(String tagName) throws ConverterException {
        log.debug("Tag: " + tagName);
        IdTag it = tagsByName.get(tagName);
        if (null == it) {
            throw new ConverterException("not supported matroska tag: " + tagName);
        }
        VINT typeVint = VINT.fromBinary(it.id);
        try {
            Tag newTag = (Tag) it.clazz.getConstructor(String.class, VINT.class).newInstance(tagName, typeVint);
            return newTag;
        } catch (Exception e) {
            log.error("Can not find property", e);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(String tagName) throws ConverterException {
        return (T) createTag(tagName);
    }

    private static class NameTag {
        private final String name;

        private final Class<? extends Tag> clazz;

        @SuppressWarnings("unchecked")
        private NameTag(Object prop) throws ClassNotFoundException {
            String[] parameters = ((String) prop).split(",");
            this.name = parameters[0];
            this.clazz = (Class<? extends Tag>) Class.forName(TagFactory.class.getPackage().getName() + "." + parameters[1]);
        }
    }

    private static class IdTag {
        private final Long id;

        private final Class<? extends Tag> clazz;

        private IdTag(Long id, Class<? extends Tag> clazz) {
            this.id = id;
            this.clazz = clazz;
        }

        @SuppressWarnings("unchecked")
        private IdTag(Object prop) throws ClassNotFoundException {
            String[] parameters = ((String) prop).split(",");
            this.id = Long.valueOf("" + parameters[0], 16);
            this.clazz = (Class<? extends Tag>) Class.forName(TagFactory.class.getPackage().getName() + "." + parameters[1]);
        }
    }
}
