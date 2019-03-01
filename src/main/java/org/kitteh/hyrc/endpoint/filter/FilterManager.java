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
package org.kitteh.hyrc.endpoint.filter;

import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.kitteh.hyrc.HyRC;
import org.kitteh.hyrc.endpoint.filter.defaults.AntiHighlight;
import org.kitteh.hyrc.endpoint.filter.defaults.DataMapper;
import org.kitteh.hyrc.endpoint.filter.defaults.RegexFilter;
import org.kitteh.hyrc.endpoint.link.Link;
import org.kitteh.hyrc.util.loadable.LoadableTypeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Filters.
 */
public final class FilterManager extends LoadableTypeManager<Filter> {
    enum Target {
        EndpointLoader
    }

    private final Map<String, ConfigurationNode> repeatableObjects = new ConcurrentHashMap<>();

    public FilterManager(@NonNull HyRC plugin, @NonNull ConfigurationNode repeatables) {
        super(plugin, Filter.class);
        // Register filter types here
        this.registerType(AntiHighlight.class);
        this.registerType(DataMapper.class);
        this.registerType(RegexFilter.class);
        if (!repeatables.isVirtual() && repeatables.hasMapChildren()) {
            this.loadRepeatables(repeatables);
        }
    }

    @Override
    protected void loadList(@NonNull List<? extends ConfigurationNode> list) {
        throw new UnsupportedOperationException("Must provide Endpoint when loading filters!");
    }

    public void loadList(@NonNull List<? extends ConfigurationNode> list, @NonNull Link.LinkFilterLoader link) {
        List<ConfigurationNode> updatedList = new ArrayList<>(list);
        for (int i = 0; i < updatedList.size(); i++) {
            ConfigurationNode node = updatedList.get(i);
            if (!node.hasMapChildren()) {
                if (this.repeatableObjects.containsKey(node.getString())) {
                    node = this.repeatableObjects.get(node.getString());
                    updatedList.set(i, node);
                } else {
                    continue;
                }
            }
            node.getNode(Target.EndpointLoader).setValue(link);
        }
        super.loadList(updatedList);
    }

    private void loadRepeatables(@NonNull ConfigurationNode repeatables) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : repeatables.getChildrenMap().entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                // TODO log
                continue;
            }
            this.repeatableObjects.put((String) entry.getKey(), entry.getValue());
        }
    }

    @Override
    protected void processCompleted(@NonNull Filter loaded) {
        Link.LinkFilterLoader loader = loaded.getLoader();
        if (loader != null) {
            loader.addFilter(loaded);
        }
    }

    @Override
    protected void processFailedLoad(@NonNull Exception exception, @NonNull ConfigurationNode data) {
        HyRC.log().warning("Failed to load Filter", exception);
    }

    @Override
    protected void processInvalid(@NonNull String reason, @NonNull ConfigurationNode data) {
        HyRC.log().warning("Encountered invalid Filter: " + reason);
    }
}
