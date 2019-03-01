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
package org.kitteh.hyrc.endpoint.defaults;

import ninja.leaping.configurate.ConfigurationNode;
import org.kitteh.hyrc.HyRC;
import org.kitteh.hyrc.endpoint.Endpoint;
import org.kitteh.hyrc.endpoint.TargetedMessage;
import org.kitteh.hyrc.exceptions.HyRCInvalidConfigException;
import org.kitteh.hyrc.irc.IRCBot;
import org.kitteh.hyrc.util.loadable.Loadable;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * The standard {@link Endpoint} for IRC bots.
 */
@Loadable.Type(name = "irc")
public class IRCEndpoint extends Endpoint {
    public enum MessageType {
        ME("* %s %s"),
        MESSAGE("<%s> %s");

        private final String format;

        MessageType(String format) {
            this.format = format;
        }

        public @NonNull String getFormat() {
            return this.format;
        }
    }

    public static final String IRC_CHANNEL = "IRC_CHANNEL";
    public static final String IRC_MASK = "IRC_MASK";
    public static final String IRC_PREFIX = "IRC_PREFIX";
    public static final String IRC_PREFIXES = "IRC_PREFIXES";
    public static final String IRC_NICK = "IRC_NICK";
    public static final String IRC_MESSAGE_TYPE = "IRC_MESSAGE_TYPE";

    private IRCBot bot;
    private String channel;
    private final HyRC plugin;

    public IRCEndpoint(HyRC plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void receiveMessage(@NonNull TargetedMessage message) {
        this.bot.sendMessage(this.channel, message.getCustomMessage());
    }

    @Override
    protected void loadExtra(@NonNull ConfigurationNode data) throws HyRCInvalidConfigException {
        final String botName = data.getNode("bot").getString();
        if (botName == null) {
            throw new HyRCInvalidConfigException("No bot defined");
        }
        this.bot = this.plugin.getBotManager().getBot(botName);
        if (this.bot == null) {
            throw new HyRCInvalidConfigException("No bot defined with name '" + botName + "'");
        }
        String channelName = data.getNode("channel").getString();
        if (channelName == null) {
            throw new HyRCInvalidConfigException("No channel defined");
        }
        this.channel = channelName;
        this.bot.addChannel(this, this.channel);
    }
}
