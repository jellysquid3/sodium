package net.caffeinemc.mods.sodium.client.gl.arena;

import net.caffeinemc.mods.sodium.client.util.UInt32;

public class GlBufferSegment {
    private final GlBufferArena arena;

    private boolean free = false;

    private int offset; /* Uint32 */
    private int length; /* Uint32 */

    private GlBufferSegment next;
    private GlBufferSegment prev;

    public GlBufferSegment(GlBufferArena arena, long offset, long length) {
        this.arena = arena;
        this.offset = UInt32.downcast(offset);
        this.length = UInt32.downcast(length);
    }

    /* Uint32 */
    protected long getEnd() {
        return this.getOffset() + this.getLength();
    }

    /* Uint32 */
    public long getOffset() {
        return UInt32.upcast(this.offset);
    }

    /* Uint32 */
    public long getLength() {
        return UInt32.upcast(this.length);
    }

    protected void setOffset(long offset /* Uint32 */) {
        this.offset = UInt32.downcast(offset);
    }

    protected void setLength(long length /* Uint32 */) {
        this.length = UInt32.downcast(length);
    }

    protected void setFree(boolean free) {
        this.free = free;
    }

    protected boolean isFree() {
        return this.free;
    }

    protected void setNext(GlBufferSegment next) {
        this.next = next;
    }

    protected GlBufferSegment getNext() {
        return this.next;
    }

    protected GlBufferSegment getPrev() {
        return this.prev;
    }

    protected void setPrev(GlBufferSegment prev) {
        this.prev = prev;
    }

    public void delete() {
        this.arena.free(this);
    }

    protected void mergeInto(GlBufferSegment entry) {
        this.setLength(this.getLength() + entry.getLength());
        this.setNext(entry.getNext());

        if (this.getNext() != null) {
            this.getNext()
                    .setPrev(this);
        }
    }
}
