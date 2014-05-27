/*
 * * Copyright (C) 2014 Matt Baxter http://kitteh.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.kitteh.craftirc.util.loadable;

import org.apache.commons.lang.Validate;
import org.bukkit.Server;
import org.kitteh.craftirc.CraftIRC;
import org.kitteh.craftirc.exceptions.CraftIRCInvalidConfigException;
import org.kitteh.craftirc.util.MapGetter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages loadable types.
 */
public abstract class LoadableTypeManager<T extends Loadable> {
    private class LoadableLoadout {
        private final Class<? extends T> clazz;
        private final Constructor<? extends T> constructor;
        private final List<LoadableField> fields;

        private LoadableLoadout(Class<? extends T> clazz, Constructor<? extends T> constructor, List<LoadableField> fields) {
            this.clazz = clazz;
            this.constructor = constructor;
            this.fields = fields;
        }

        private Class<? extends T> getClazz() {
            return this.clazz;
        }

        private Constructor<? extends T> getConstructor() {
            return this.constructor;
        }

        private List<LoadableField> getFields() {
            return this.fields;
        }
    }

    private class LoadableField {
        private final Field field;
        private final String name;
        private final boolean required;

        private LoadableField(String name, Field field, boolean required) {
            this.field = field;
            this.name = name;
            this.required = required;
        }

        private Field getField() {
            return this.field;
        }

        private String getName() {
            return this.name;
        }

        private boolean isRequired() {
            return this.required;
        }
    }

    private final Map<String, LoadableLoadout> types = new ConcurrentHashMap<>();
    private final CraftIRC plugin;
    private final Map<String, List<Map<Object, Object>>> unRegistered = new ConcurrentHashMap<>();
    private final Class<T> clazz;

    protected LoadableTypeManager(CraftIRC plugin, Class<T> clazz) {
        this.clazz = clazz;
        this.plugin = plugin;
    }

    protected void loadList(List<Object> list) {
        for (final Object listElement : list) {
            final Map<Object, Object> data;
            if ((data = MapGetter.castToMap(listElement)) == null) {
                continue;
            }
            final String type;
            if ((type = MapGetter.getString(data, "type")) == null) {
                this.processInvalid("No type set", data);
                continue;
            }
            final LoadableLoadout loadout = this.types.get(type);
            if (loadout == null) {
                List<Map<Object, Object>> unregged = this.unRegistered.get(type);
                if (unregged == null) {
                    unregged = new LinkedList<>();
                    this.unRegistered.put(type, unregged);
                }
                unregged.add(data);
                continue;
            }
            this.load(type, loadout, data);
        }
    }

    private void load(String type, LoadableLoadout loadout, Map<Object, Object> data) {
        Class<?>[] parameterTypes = loadout.getConstructor().getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < args.length; i++) {
            if (parameterTypes[i].equals(Server.class)) {
                args[i] = this.plugin.getServer();
            } else if (parameterTypes[i].equals(CraftIRC.class)) {
                args[i] = this.plugin;
            }
        }
        T loaded;
        try {
            loaded = loadout.getConstructor().newInstance(args);
            for (LoadableField field : loadout.getFields()) {
                Object o = MapGetter.get(data, field.getName(), field.getField().getType());
                if (field.isRequired() && o == null) {
                    throw new CraftIRCInvalidConfigException(String.format("Missing required field '%s' for type '%s'", field.getName(), type));
                }
                field.getField().set(loaded, o);
            }
            loaded.load(this.plugin, data);
            this.processCompleted(loaded);
        } catch (Exception e) {
            this.processFailedLoad(e, data);
        }

    }

    /**
     * Registers a Loadable type by {@link Loadable.Type} name. Loadable
     * types registered here can be processed for loading from configuration.
     * <p/>
     * Names are unique and may not be registered twice.
     * <p/>
     * Classes must have a public constructor. The first constructor found is
     * the constructor used. The following types can be specified as
     * constructor parameters, with all others being passed null:
     * <ul>
     * <li>
     * {@link org.bukkit.Server} - Is passed the Bukkit server.
     * </li>
     * <li>
     * {@link CraftIRC} - Is passed the plugin instance
     * </li>
     * </ul>
     *
     * @param clazz class of the Loadable type to be registered
     */
    public final void registerType(Class<? extends T> clazz) {
        Validate.isTrue(this.clazz.isAssignableFrom(clazz), "Submitted class '" + clazz.getSimpleName() + "' is not of type " + this.clazz.getSimpleName());

        Constructor[] constructors = clazz.getConstructors();
        Validate.isTrue(constructors.length > 0, "Class '" + clazz.getSimpleName() + "' lacks a public constructor");
        @SuppressWarnings("unchecked")
        Constructor<? extends T> constructor = constructors[0];

        final Loadable.Type type = clazz.getAnnotation(Loadable.Type.class);
        Validate.notNull(type, "Submitted class '" + clazz.getSimpleName() + "' has no Loadable.Type annotation");
        final String name = type.name();
        if (this.types.containsKey(name)) {
            throw new IllegalArgumentException(this.clazz.getSimpleName() + " type name '" + name + "' is already registered to '" + this.types.get(name).getClazz().getSimpleName() + "' and cannot be registered by '" + clazz.getSimpleName() + "'");
        }

        Map<String, LoadableField> fieldMap = new HashMap<>();
        this.mapFields(fieldMap, clazz);
        List<LoadableField> fields = new LinkedList<>(fieldMap.values());
        LoadableLoadout loadout = new LoadableLoadout(clazz, constructor, fields);

        this.types.put(name, loadout);
        if (this.unRegistered.containsKey(name)) {
            for (final Map<Object, Object> data : this.unRegistered.get(name)) {
                this.load(name, loadout, data);
            }
        }
    }

    private void mapFields(Map<String, LoadableField> map, Class<? extends T> clazz) {
        if (this.clazz.isAssignableFrom(clazz.getSuperclass())) {
            @SuppressWarnings("unchecked")
            Class<? extends T> superClass = (Class<? extends T>) clazz.getSuperclass();
            mapFields(map, superClass);
        }
        for (Field field : clazz.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            Load loadData;
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || (loadData = field.getAnnotation(Load.class)) == null) {
                continue;
            }
            field.setAccessible(true);
            String confName = loadData.name().isEmpty() ? field.getName() : loadData.name();
            map.put(confName, new LoadableField(confName, field, loadData.required()));
        }
    }

    protected abstract void processCompleted(T loaded) throws CraftIRCInvalidConfigException;

    protected abstract void processFailedLoad(Exception exception, Map<Object, Object> data);

    protected abstract void processInvalid(String reason, Map<Object, Object> data);
}