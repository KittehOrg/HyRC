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
package org.kitteh.craftirc.endpoint.filter;

import org.kitteh.craftirc.CraftIRC;
import org.kitteh.craftirc.endpoint.Endpoint;
import org.kitteh.craftirc.endpoint.filter.defaults.AntiHighlight;
import org.kitteh.craftirc.endpoint.filter.defaults.BukkitPermissionFilter;
import org.kitteh.craftirc.endpoint.filter.defaults.DataMapper;
import org.kitteh.craftirc.endpoint.filter.defaults.RegexFilter;
import org.kitteh.craftirc.util.MapGetter;
import org.kitteh.craftirc.util.loadable.LoadableTypeManager;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Handles Filters.
 */
public final class FilterManager extends LoadableTypeManager<Filter> {
    enum Target {
        EndpointLoader
    }

    public FilterManager(CraftIRC plugin) {
        super(plugin, Filter.class);
        // Register filter types here
        this.registerType(AntiHighlight.class);
        this.registerType(BukkitPermissionFilter.class);
        this.registerType(DataMapper.class);
        this.registerType(RegexFilter.class);
    }

    @Override
    public void loadList(List<?> list) {
        throw new UnsupportedOperationException("Must provide Endpoint when loading filters!");
    }

    public void loadList(List<?> list, Endpoint.EndpointFilterLoader endpoint) {
        for (final Object listElement : list) {
            final Map<Object, Object> data;
            if ((data = MapGetter.castToMap(listElement)) == null) {
                continue;
            }
            data.put(Target.EndpointLoader, endpoint);
        }
        super.loadList(list);
    }

    @Override
    protected void processCompleted(Filter loaded) {
        Endpoint.EndpointFilterLoader loader = loaded.getLoader();
        if (loader != null) {
            loader.addFilter(loaded);
        }
    }

    @Override
    protected void processFailedLoad(Exception exception, Map<Object, Object> data) {
        CraftIRC.log().log(Level.WARNING, "Failed to load Filter", exception);
    }

    @Override
    protected void processInvalid(String reason, Map<Object, Object> data) {
        CraftIRC.log().warning("Encountered invalid Filter: " + reason);
    }
}