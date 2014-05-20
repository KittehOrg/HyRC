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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages loadable types.
 */
public abstract class LoadableTypeManager<T extends Loadable> {
    private final Map<String, Constructor<? extends T>> types = new ConcurrentHashMap<>();
    private final CraftIRC plugin;
    private final Map<String, List<Map<Object, Object>>> unRegistered = new ConcurrentHashMap<>();
    private final Class<T> clazz;

    protected LoadableTypeManager(CraftIRC plugin, Class<T> clazz) {
        this.clazz = clazz;
        this.plugin = plugin;
    }

    protected void loadList(List<?> list) {
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
            final Constructor<? extends T> constructor = this.types.get(type);
            if (constructor == null) {
                List<Map<Object, Object>> unregged = this.unRegistered.get(type);
                if (unregged == null) {
                    unregged = new LinkedList<>();
                    this.unRegistered.put(type, unregged);
                }
                unregged.add(data);
                continue;
            }
            this.load(constructor, data);
        }
    }

    private void load(Constructor<? extends T> constructor, Map<Object, Object> data) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
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
            loaded = constructor.newInstance(args);
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
            throw new IllegalArgumentException(this.clazz.getSimpleName() + " type name '" + name + "' is already registered to '" + this.types.get(name).getDeclaringClass().getSimpleName() + "' and cannot be registered by '" + clazz.getSimpleName() + "'");
        }
        this.types.put(name, constructor);
        if (this.unRegistered.containsKey(name)) {
            for (final Map<Object, Object> loadable : this.unRegistered.get(name)) {
                this.load(constructor, loadable);
            }
        }
    }

    protected abstract void processCompleted(T loaded) throws CraftIRCInvalidConfigException;

    protected abstract void processFailedLoad(Exception exception, Map<Object, Object> data);

    protected abstract void processInvalid(String reason, Map<Object, Object> data);
}
