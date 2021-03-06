/*
 * * Copyright (C) 2014-2019 Matt Baxter http://kitteh.org
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
package org.kitteh.hyrc.util.loadable;

import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.kitteh.hyrc.HyRC;
import org.kitteh.hyrc.exceptions.HyRCInvalidConfigException;
import org.kitteh.irc.client.library.util.Sanity;

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
public abstract class LoadableTypeManager<Type extends Loadable> {
    private class LoadableLoadout {
        private final Class<? extends Type> clazz;
        private final Constructor<? extends Type> constructor;
        private final List<LoadableField> fields;

        private LoadableLoadout(@NonNull Class<? extends Type> clazz, @NonNull Constructor<? extends Type> constructor, @NonNull List<LoadableField> fields) {
            this.clazz = clazz;
            this.constructor = constructor;
            this.fields = fields;
        }

        private @NonNull Class<? extends Type> getClazz() {
            return this.clazz;
        }

        private @NonNull Constructor<? extends Type> getConstructor() {
            return this.constructor;
        }

        private @NonNull List<LoadableField> getFields() {
            return this.fields;
        }
    }

    private class LoadableField {
        private final Field field;
        private final String name;
        private final boolean required;

        private LoadableField(@NonNull String name, @NonNull Field field, boolean required) {
            this.field = field;
            this.name = name;
            this.required = required;
        }

        private @NonNull Field getField() {
            return this.field;
        }

        private @NonNull String getName() {
            return this.name;
        }

        private boolean isRequired() {
            return this.required;
        }
    }

    private final Map<Class<?>, ArgumentProvider<?>> argumentProviders = new ConcurrentHashMap<>();
    private final Map<String, LoadableLoadout> types = new ConcurrentHashMap<>();
    private final HyRC plugin;
    private final Map<String, List<ConfigurationNode>> unRegistered = new ConcurrentHashMap<>();
    private final Class<Type> clazz;

    protected LoadableTypeManager(@NonNull HyRC plugin, @NonNull Class<Type> clazz) {
        this.clazz = clazz;
        this.plugin = plugin;
    }

    protected void loadList(@NonNull List<? extends ConfigurationNode> list) {
        for (final ConfigurationNode node : list) {
            if (node.getNode("type").isVirtual()) {
                this.processInvalid("No type set", node);
                continue;
            }
            final String type = node.getNode("type").getString();
            final LoadableLoadout loadout = this.types.get(type);
            if (loadout == null) {
                List<ConfigurationNode> unregged = this.unRegistered.computeIfAbsent(type, k -> new LinkedList<>());
                unregged.add(node);
                continue;
            }
            this.load(type, loadout, node);
        }
    }

    protected @NonNull HyRC getHyRC() {
        return this.plugin;
    }

    private void load(@NonNull String type, @NonNull LoadableLoadout loadout, @NonNull ConfigurationNode data) {
        Class<?>[] parameterTypes = loadout.getConstructor().getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < args.length; i++) {
            if (parameterTypes[i].equals(HyRC.class)) {
                args[i] = this.plugin;
            } else if (this.argumentProviders.containsKey(parameterTypes[i])) {
                args[i] = this.argumentProviders.get(parameterTypes[i]).getArgument();
            }
        }
        Type loaded;
        try {
            loaded = loadout.getConstructor().newInstance(args);
            for (LoadableField field : loadout.getFields()) {
                ConfigurationNode node = data.getNode(field.getName());
                if (!node.isVirtual()) {
                    field.getField().set(loaded, this.getByType(node, field.getField().getType()));
                } else if (field.isRequired()) {
                    throw new HyRCInvalidConfigException(String.format("Missing required field '%s' for type '%s'", field.getName(), type));
                }
            }
            loaded.load(this.plugin, data);
            this.processCompleted(loaded);
        } catch (Exception e) {
            this.processFailedLoad(e, data);
        }
    }

    private Object getByType(ConfigurationNode node, Class<?> type) throws HyRCInvalidConfigException {
        if (type == String.class) {
            return node.getString();
        } else if (type == Boolean.class) {
            return node.getBoolean();
        } else if (type == Double.class) {
            return node.getDouble();
        } else if (type == Float.class) {
            return node.getFloat();
        } else if (type == Integer.class) {
            return node.getInt();
        } else if (type == Long.class) {
            return node.getLong();
        } else {
            throw new HyRCInvalidConfigException(String.format("Field '%s' expected an unsupported type", type));
        }
    }

    /**
     * Registers an ArgumentProvider for a particular class
     *
     * @param clazz class to register
     * @param provider provider to register
     * @return previously registered provider for given class, else null
     * @throws IllegalArgumentException for null class or provider
     */
    public final <Argument> @NonNull ArgumentProvider<? extends Argument> registerArgumentProvider(@NonNull Class<Argument> clazz, @NonNull ArgumentProvider<? extends Argument> provider) {
        Sanity.nullCheck(clazz, "Cannot register a null class");
        Sanity.nullCheck(provider, "Cannot register a null provider");
        @SuppressWarnings("unchecked")
        ArgumentProvider<? extends Argument> old = (ArgumentProvider<? extends Argument>) this.argumentProviders.put(clazz, provider);
        return old;
    }

    /**
     * Registers a Loadable type by {@link Loadable.Type} name. Loadable
     * types registered here can be processed for loading from configuration.
     * <p/>
     * Names are unique and may not be registered twice.
     * <p/>
     * Classes must have a public constructor. The first constructor found is
     * the constructor used. The following types can be specified as
     * constructor parameters, with all others being passed null unless an
     * {@link org.kitteh.hyrc.util.loadable.ArgumentProvider} has been
     * registered for the type:
     * <ul>
     * <li>
     * {@link HyRC} - Is passed the HyRC instance
     * </li>
     * </ul>
     *
     * @param clazz class of the Loadable type to be registered
     * @throws IllegalArgumentException for null classes, classes not of the
     * manager's type, classes without the {@link Loadable.Type} annotation,
     * classes without public constructors, or for duplicate name submissions
     */
    public final void registerType(@NonNull Class<? extends Type> clazz) {
        Sanity.nullCheck(clazz, "Cannot register a null class");
        Sanity.truthiness(this.clazz.isAssignableFrom(clazz), "Submitted class '" + clazz.getSimpleName() + "' is not of type " + this.clazz.getSimpleName());

        Constructor[] constructors = clazz.getConstructors();
        Sanity.truthiness(constructors.length > 0, "Class '" + clazz.getSimpleName() + "' lacks a public constructor");
        @SuppressWarnings("unchecked")
        Constructor<? extends Type> constructor = constructors[0];

        final Loadable.Type type = clazz.getAnnotation(Loadable.Type.class);
        Sanity.nullCheck(type, "Submitted class '" + clazz.getSimpleName() + "' has no Loadable.Type annotation");
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
            for (final ConfigurationNode data : this.unRegistered.get(name)) {
                this.load(name, loadout, data);
            }
        }
    }

    private void mapFields(@NonNull Map<String, LoadableField> map, @NonNull Class<? extends Type> clazz) {
        if (this.clazz.isAssignableFrom(clazz.getSuperclass())) {
            @SuppressWarnings("unchecked")
            Class<? extends Type> superClass = (Class<? extends Type>) clazz.getSuperclass();
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

    protected abstract void processCompleted(@NonNull Type loaded) throws HyRCInvalidConfigException;

    protected abstract void processFailedLoad(@NonNull Exception exception, @NonNull ConfigurationNode data);

    protected abstract void processInvalid(@NonNull String reason, @NonNull ConfigurationNode data);
}
