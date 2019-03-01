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
package org.kitteh.hyrc.irc;

import ninja.leaping.configurate.ConfigurationNode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.kitteh.hyrc.HyRC;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.feature.auth.NickServ;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages IRC bots.
 */
public final class BotManager {
    private final Map<String, IRCBot> bots = new ConcurrentHashMap<>();
    private final HyRC plugin;

    /**
     * Initialized by {@link HyRC} main.
     *
     * @param plugin the HyRC instance
     * @param bots list of bot data to load
     */
    public BotManager(@NonNull HyRC plugin, @NonNull List<? extends ConfigurationNode> bots) {
        this.plugin = plugin;
        this.plugin.trackShutdownable(() -> BotManager.this.bots.values().forEach(IRCBot::shutdown));
        this.loadBots(bots);
    }

    /**
     * Gets a bot by name.
     *
     * @param name bot name
     * @return named bot or null if no such bot exists
     */
    public @Nullable IRCBot getBot(@NonNull String name) {
        return this.bots.get(name);
    }

    private void loadBots(@NonNull List<? extends ConfigurationNode> list) {
        Set<String> usedBotNames = new HashSet<>();
        int nonMap = 0;
        int noName = 0;
        for (final ConfigurationNode node : list) {
            if (!node.hasMapChildren()) {
                nonMap++;
                continue;
            }
            final String name = node.getNode("name").getString();
            if (name == null) {
                noName++;
                continue;
            }
            if (!usedBotNames.add(name)) {
                HyRC.log().warning(String.format("Ignoring duplicate bot with name %s", name));
                continue;
            }
            this.addBot(name, node);
        }
        if (nonMap > 0) {
            HyRC.log().warning(String.format("Bots list contained %d entries which were not maps", nonMap));
        }
        if (noName > 0) {
            HyRC.log().warning(String.format("Bots list contained %d entries without a 'name'", noName));
        }
    }

    private void addBot(@NonNull String name, @NonNull ConfigurationNode data) {
        Client.Builder botBuilder = Client.builder();
        botBuilder.name(name);
        botBuilder.server().host(data.getNode("host").getString("localhost"));
        botBuilder.server().port(data.getNode("port").getInt(6667));
        botBuilder.server().secure(data.getNode("ssl").getBoolean());
        ConfigurationNode password = data.getNode("password");
        if (!password.isVirtual()) {
            botBuilder.server().password(password.getString());
        }
        botBuilder.user(data.getNode("user").getString("HyRC"));
        botBuilder.realName(data.getNode("realname").getString("HyRC Bot"));

        ConfigurationNode bind = data.getNode("bind");
        ConfigurationNode bindHost = bind.getNode("host");
        if (!bindHost.isVirtual()) {
            botBuilder.bind().host(bindHost.getString());
        }
        botBuilder.bind().port(bind.getNode("port").getInt(0));
        botBuilder.nick(data.getNode("nick").getString("HyRC"));

        ConfigurationNode auth = data.getNode("auth");
        String authUser = auth.getNode("user").getString();
        String authPass = auth.getNode("pass").getString();

        ConfigurationNode debug = data.getNode("debug-output");
        if (debug.getNode("exceptions").getBoolean()) {
            botBuilder.listeners().exception(exception -> HyRC.log().warning("Exception on bot " + name, exception));
        } else {
            botBuilder.listeners().exception(null);
        }
        if (debug.getNode("input").getBoolean()) {
            botBuilder.listeners().input(input -> HyRC.log().info("[IN] " + input));
        }
        if (debug.getNode("output").getBoolean()) {
            botBuilder.listeners().output(output -> HyRC.log().info("[OUT] " + output));
        }

        Client client = botBuilder.build();

        if (authUser != null && authPass != null) {
            client.getAuthManager().addProtocol(NickServ.builder(client).account(authUser).password(authPass).build());
        }

        client.connect();

        this.bots.put(name, new IRCBot(this.plugin, name, client));
    }
}
