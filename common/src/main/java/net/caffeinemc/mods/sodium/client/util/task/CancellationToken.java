package net.caffeinemc.mods.sodium.client.util.task;

public interface CancellationToken {
    boolean isCancelled();

    void setCancelled();

    CancellationToken NEVER_CANCELLED = new CancellationToken() {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setCancelled() {
            throw new UnsupportedOperationException("NEVER_CANCELLED cannot be cancelled");
        }
    };
}
