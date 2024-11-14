package net.caffeinemc.mods.sodium.client.gl.arena;

import net.caffeinemc.mods.sodium.client.util.UInt32;

class PendingBufferCopyCommand {
    private final int readOffset; /* Uint32 */
    private final int writeOffset; /* Uint32 */

    private int length;

    PendingBufferCopyCommand(long readOffset, long writeOffset, long length) {
        this.readOffset = UInt32.downcast(readOffset);
        this.writeOffset = UInt32.downcast(writeOffset);
        this.length = UInt32.downcast(length);
    }

    /* Uint32 */
    public long getReadOffset() {
        return UInt32.upcast(this.readOffset);
    }

    /* Uint32 */
    public long getWriteOffset() {
        return UInt32.upcast(this.writeOffset);
    }

    /* Uint32 */
    public long getLength() {
        return UInt32.upcast(this.length);
    }

    public void setLength(long length /* Uint32 */) {
        this.length = UInt32.downcast(length);
    }
}
