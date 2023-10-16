/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package com.ardikars.vertx.config.yaml;

import io.vertx.config.spi.ConfigProcessor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;


/**
 * A processor using Jackson and SnakeYaml to read Yaml files.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public final class YamlProcessor implements ConfigProcessor {

    private static final LoaderOptions DEFAULT_OPTIONS = new LoaderOptions();

    @Override
    public String name() {
        return "yaml";
    }

    @Override
    public Future<JsonObject> process(Vertx vertx, JsonObject configuration, Buffer input) {
        if (input.length() == 0) {
            // the parser does not support empty files, which should be managed to be homogeneous
            return ((ContextInternal) vertx.getOrCreateContext()).succeededFuture(new JsonObject());
        }

        // Use executeBlocking even if the bytes are in memory
        return vertx.executeBlocking(() -> {
            try {
                final Yaml yamlMapper = new Yaml(new SafeConstructor(DEFAULT_OPTIONS));
                final Map<Object, Object> doc = yamlMapper.load(input.toString(StandardCharsets.UTF_8));
                return jsonify(doc);
            } catch (ClassCastException e) {
                throw new DecodeException("Failed to decode YAML", e);
            }
        });
    }

    /**
     * Yaml allows map keys of type object, however json always requires key as String,
     * this helper method will ensure we adapt keys to the right type
     *
     * @param yaml yaml map
     * @return json map
     */
    private static JsonObject jsonify(final Map<Object, Object> yaml) {
        if (yaml == null) {
            return null;
        }
        final JsonObject json = new JsonObject();
        for (final Map.Entry<Object, Object> kv : yaml.entrySet()) {
            Object value = kv.getValue();
            if (value instanceof Map) {
                value = jsonify((Map<Object, Object>) value);
            }
            // snake yaml handles dates as java.util.Date, and JSON does Instant
            if (value instanceof Date) {
                value = ((Date) value).toInstant();
            }
            if (value instanceof List) {
                final List list = (List) value;
                final List<Object> newList = new ArrayList<>(list.size());
                for (Object o : list) {
                    final Object newVal;
                    if (o instanceof Map) {
                        newVal = jsonify((Map<Object, Object>) o);
                    } else if (o instanceof Date) {
                        newVal = ((Date) o).toInstant();
                    } else {
                        newVal = o;
                    }
                    newList.add(newVal);
                }
                value = newList;
            }
            if (value instanceof List) {
                json.put(kv.getKey().toString(), value);
            } else if (value instanceof JsonObject) {
                json.put(kv.getKey().toString(), value);
            } else {
                json.put(kv.getKey().toString(), resolvePlaceholder(value.toString()));
            }
        }
        return json;
    }

    private static Object resolvePlaceholder(String value) {
        value = value.trim();
        final int length = value.length();
        if (length > 3) {
            if (value.charAt(0) == '$' && value.charAt(1) == '{' && value.charAt(length - 1) == '}') {
                String key = value.substring(2, length - 1).trim();
                final int keyLength = key.length();
                if (keyLength > 1) {
                    int separatorIndex = -1;
                    for (int i = 0; i < keyLength; i++) {
                        if (key.charAt(i) == ':') {
                            separatorIndex = i;
                            break;
                        }
                    }
                    if (separatorIndex == 0) {
                        //":k"
                        return normalizeValue(key.substring(1));
                    } else if (separatorIndex > 0) {
                        //"k:"
                        final String actualKey = key.substring(0, separatorIndex);
                        final String defaultVal = key.substring(separatorIndex + 1);
                        final String envVal = System.getenv(actualKey);
                        if (envVal == null || envVal.isEmpty()) {
                            return normalizeValue(defaultVal);
                        } else {
                            return normalizeValue(envVal);
                        }
                    } else {
                        final String env = System.getenv(key);
                        return env == null ? "" : normalizeValue(env);
                    }
                } else {
                    return "";
                }
            } else {
                return normalizeValue(value);
            }
        } else {
            return normalizeValue(value);
        }
    }

    private static Object normalizeValue(String value) {
        if (value.trim().startsWith("[") && value.endsWith("]")) {
            final String substring = value.substring(1, value.length() - 1);
            if (substring.contains(",")) {
                final List<Object> list = new ArrayList<>();
                for (final String str : substring.split(",")) {
                    list.add(normalizeObject0(str));
                }
                return list;
            } else {
                return normalizeObject0(substring);
            }
        } else {
            return normalizeObject0(value);
        }
    }

    private static boolean isInteger(String val) {
        for (char c : val.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    public static Object normalizeObject0(String str) {
        final String strTrimmed = str.trim();
        if (isInteger(strTrimmed)) {
            final long val = Long.parseLong(strTrimmed);
            if (val > 0x7fffffffL) {
                return val;
            } else {
                return (int) val;
            }
        } else {
            try {
                return Double.parseDouble(strTrimmed);
            } catch (Exception e) {
                if ((strTrimmed.startsWith("\"") && strTrimmed.endsWith("\"")) || ((strTrimmed.startsWith("'") && strTrimmed.endsWith("'")))) {
                    return strTrimmed.substring(1, strTrimmed.length() - 1);
                } else {
                    return strTrimmed;
                }
            }
        }
    }
}

