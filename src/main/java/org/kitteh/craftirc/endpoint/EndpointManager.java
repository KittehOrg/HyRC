package org.kitteh.craftirc.endpoint;

import org.apache.commons.lang.Validate;
import org.bukkit.Server;
import org.kitteh.craftirc.CraftIRC;
import org.kitteh.craftirc.endpoint.defaults.MinecraftEndpoint;
import org.kitteh.craftirc.util.Pair;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains {@link Endpoint}s and classes corresponding to Endpoint types.
 */
public final class EndpointManager {
    private final Map<String, Constructor<? extends Endpoint>> types = new ConcurrentHashMap<String, Constructor<? extends Endpoint>>();
    private final Map<String, List<Pair<String, Map<?, ?>>>> unRegistered = new ConcurrentHashMap<String, List<Pair<String, Map<?, ?>>>>();
    private final Map<String, Endpoint> endpoints = new ConcurrentHashMap<String, Endpoint>();
    private final Map<String, List<String>> links = new ConcurrentHashMap<String, List<String>>();
    private final MessageDistributor messageDistributor;
    private final CraftIRC plugin;

    public EndpointManager(CraftIRC plugin, List<?> endpoints, List<?> links) {
        this.plugin = plugin;
        this.messageDistributor = new MessageDistributor(this, plugin);
        // We register ours first.
        registerEndpointType(MinecraftEndpoint.class);

        loadEndpoints(endpoints);
        loadLinks(links);
    }

    /**
     * Registers an Endpoint type by {@link EndpointType} name. Endpoint
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
     * </ul>
     *
     * @param clazz class of the Endpoint type to be registered
     */
    public void registerEndpointType(Class<? extends Endpoint> clazz) {
        Validate.isTrue(Endpoint.class.isAssignableFrom(clazz), "Submitted class '" + clazz.getSimpleName() + "' is not of type Endpoint");
        Constructor[] constructors = clazz.getConstructors();
        Validate.isTrue(constructors.length > 0, "Class '" + clazz.getSimpleName() + "' lacks a public constructor");
        @SuppressWarnings("unchecked")
        Constructor<? extends Endpoint> constructor = constructors[0];
        final EndpointType type = clazz.getAnnotation(EndpointType.class);
        Validate.notNull(type, "Submitted class '" + clazz.getSimpleName() + "' has no EndpointType annotation");
        final String name = type.name();
        Validate.isTrue(!this.types.containsKey(name), "Endpoint type name '" + name + "' is already registered to '" + this.types.get(name).getDeclaringClass().getSimpleName() + "' and cannot be registered by '" + clazz.getSimpleName() + "'");
        this.types.put(name, constructor);
        if (this.unRegistered.containsKey(name)) {
            for (final Pair<String, Map<?, ?>> endpoint : this.unRegistered.get(name)) {
                this.loadEndpoint(constructor, endpoint.getA(), endpoint.getB());
            }
        }
    }

    public void sendMessage(Message message) {
        this.messageDistributor.addMessage(message);
    }

    /**
     * Gets the Endpoint destinations of a named source Endpoint
     *
     * @param source source Endpoint
     * @return destinations of a message send by the speciified Endpoint
     */
    Set<Endpoint> getDestinations(String source) {
        Set<Endpoint> destinations = new HashSet<Endpoint>();
        List<String> targets = this.links.get(source);
        if (targets != null) {
            for (String target : targets) {
                Endpoint endpoint = this.endpoints.get(target);
                if (endpoint != null) {
                    destinations.add(endpoint);
                }
            }
        }
        return destinations;
    }

    private <T> T get(Map<?, ?> map, String key, Class<T> clazz) {
        final Object object = map.get(key);
        if (clazz.isAssignableFrom(object.getClass())) {
            @SuppressWarnings("unchecked")
            final T t = (T) object;
            return t;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getStringObjectMap(Object o, String name) {
        if (o == null) {
            return null;
        }
        if (!(o instanceof Map)) {
            // TODO warn about not being a map
            return null;
        }
        final Map<?, ?> map = (Map<?, ?>) o;
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                // TODO warn about not being a map of string keys
                return null;
            }
        }
        return (Map<String, Object>) map;
    }

    private void loadEndpoint(Constructor<? extends Endpoint> type, String name, Map<?, ?> map) {
        Class<?>[] parameterTypes = type.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < args.length; i++) {
            if (parameterTypes[i].equals(Server.class)) {
                args[i] = this.plugin.getServer();
            }
        }
        try {
            final Endpoint endpoint = type.newInstance(args);
            endpoint.setName(name);
            Object oFilterList = map.get("filters");
            if (oFilterList instanceof List) {
                List<?> filterList = (List<?>) oFilterList;
                List<Map<?, ?>> filters = new LinkedList<Map<?, ?>>();
                int containsInvalid = 0;
                for (Object o : filterList) {
                    if (o instanceof Map && ((Map<?, ?>) o).containsKey("type")) {
                        filters.add((Map<?, ?>) o);
                    } else {
                        containsInvalid++;
                    }
                }
                if (containsInvalid > 0) {
                    // TODO message about containing an invalid filter
                }
                if (!filters.isEmpty()) {
                    endpoint.loadFilters(filters);
                }
            } else {
                // TODO message about filters not being properly defined
            }
            final Map<String, Object> extras = this.getStringObjectMap(map.get("extra"), "extra");
            if (extras != null) {
                endpoint.loadExtra(extras);
            }
            this.endpoints.put(name, endpoint);
        } catch (final Exception e) {
            // TODO error loading endpoint
        }
    }

    private void loadEndpoints(List<?> list) {
        Set<String> usedEndpointNames = new HashSet<>();
        for (final Object listElement : list) {
            final Map<?, ?> endpointMap;
            if ((endpointMap = this.getMap(listElement)) == null) {
                // TODO: Track (Don't fire each time!) that an invalid entry was added
                continue;
            }
            final String name = this.get(endpointMap, "name", String.class);
            if (name == null) {
                // TODO fire message for unnamed/invalidly-named endpoint
                continue;
            }
            final String type = this.get(endpointMap, "type", String.class);
            if (type == null) {
                // TODO fire message for invalid type for endpoint 'name'
                continue;
            }
            if (usedEndpointNames.contains(name)) {
                // TODO fire message for duplicate endpoint name 'name'
                continue;
            }
            usedEndpointNames.add(name);
            final Constructor<? extends Endpoint> constructor = this.types.get(type);
            if (constructor == null) {
                List<Pair<String, Map<?, ?>>> unregged = this.unRegistered.get(type);
                if (unregged == null) {
                    unregged = new LinkedList<Pair<String, Map<?, ?>>>();
                    this.unRegistered.put(type, unregged);
                }
                unregged.add(new Pair<String, Map<?, ?>>(name, endpointMap));
                continue; // Hold for later!
            }
            this.loadEndpoint(constructor, name, endpointMap);
        }
    }

    private void loadLinks(List<?> list) {
        if (list == null) {
            // TODO fire message for lack of links
            return;
        }
        for (final Object listElement : list) {
            final Map<?, ?> linkMap;
            if ((linkMap = this.getMap(listElement)) == null) {
                // TODO: Track (Don't fire each time!) that an invalid entry was added
                continue;
            }
            final String source = this.get(linkMap, "source", String.class);
            if (source == null) {
                // TODO fire message for link without source
                continue;
            }
            final String target = this.get(linkMap, "target", String.class);
            if (target == null) {
                // TODO fire message for link without target
                continue;
            }
            final Object bidirectionalObject = linkMap.get("bidirectional");
            boolean bidirectional = false;
            if (bidirectionalObject != null) {
                if (bidirectionalObject instanceof Boolean && bidirectionalObject == Boolean.TRUE) {
                    bidirectional = true;
                } else if (bidirectionalObject instanceof String && ((String) bidirectionalObject).equalsIgnoreCase("true")) {
                    bidirectional = true;
                }
            }
            this.addLink(source, target);
            if (bidirectional) {
                this.addLink(target, source);
            }
        }
    }

    private void addLink(String source, String target) {
        source = source.toLowerCase();
        target = target.toLowerCase();
        List<String> targets = this.links.get(source);
        if (targets == null) {
            targets = new LinkedList<String>();
            this.links.put(source, targets);
        }
        targets.add(target);
    }

    private Map<?, ?> getMap(Object o) {
        if (Map.class.isAssignableFrom(o.getClass())) {
            return (Map<?, ?>) o;
        }
        return null;
    }
}