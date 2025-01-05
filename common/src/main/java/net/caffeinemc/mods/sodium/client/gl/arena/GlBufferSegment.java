package net.caffeinemc.mods.sodium.client.gl.arena;

import net.caffeinemc.mods.sodium.client.util.UInt32;

public class GlBufferSegment {
    private final GlBufferArena arena;

    private boolean free = false;
    private int refCount = 1;
    private long hash;
    private boolean isHashed = false;

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

    public void setHash(long hash) {
        this.hash = hash;
        this.isHashed = true;
    }

    public long getHash() {
        return this.hash;
    }

    public boolean isHashed() {
        return this.isHashed;
    }

    public void addRef() {
        if (this.isFree()) {
            throw new IllegalStateException("Cannot add ref to free segment");
        }
        this.refCount++;
    }

    protected void setFree(boolean free) {
        this.free = free;
        if (this.free) {
            this.refCount = 0;
        } else {
            this.refCount = Math.max(this.refCount, 1);
        }
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
        // only actually free if there's no more users
        if (--this.refCount == 0) {
            this.arena.free(this);
            this.isHashed = false;
        }
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
