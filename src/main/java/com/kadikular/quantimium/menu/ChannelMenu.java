package com.kadikular.quantimium.menu;

/**
 * A menu for something on a laser channel ({@link com.kadikular.quantimium.laser.Channels}): a port, a tank, a receptacle, a
 * terminal's controller. Its channel is a data slot, so the screen shows it as it is.
 */
public interface ChannelMenu {
    /** Its channel, as last synced on the client. */
    int channel();

    /** Sets its channel (on the server). */
    void setChannel(int channel);

    /** The data slot that keeps {@link #channel} in step: reads {@code server} there, keeps what it's sent here. */
    final class ChannelSlot extends net.minecraft.world.inventory.DataSlot {
        private final java.util.function.IntSupplier server;
        private int client;

        public ChannelSlot(java.util.function.IntSupplier server) {
            this.server = server;
        }

        @Override
        public int get() {
            return server == null ? client : server.getAsInt();
        }

        @Override
        public void set(int value) {
            client = value;
        }

        /** What the client was last sent. */
        public int shown() {
            return client;
        }
    }
}
