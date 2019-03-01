package org.kitteh.hyrc.util;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds maps and gets in fights.
 */
public final class MapBuilder<Key, Value> {
    private final Map<Key, Value> map = new HashMap<>();

    public @NonNull Map<Key, Value> build() {
        return this.map;
    }

    public @NonNull MapBuilder<Key, Value> put(Key key, Value value) {
        this.map.put(key, value);
        return this;
    }
}
