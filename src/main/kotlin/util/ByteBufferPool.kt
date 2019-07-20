package love.sola.copier.util

import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * A thread-safe pool of [ByteBuffer]s so we don't have to allocate them again and again
 *
 * @param concurrency the amount of releasing is required before putting back into the pool
 * @author Sola
 */
class ByteBufferPool(val poolSize: Int, val bufferSize: Int, val concurrency: Int) {

    private val pool = LinkedBlockingQueue<PooledByteBuffer>(poolSize)

    init {
        repeat(poolSize) {
            pool.put(PooledByteBuffer(ByteBuffer.allocateDirect(bufferSize), this, concurrency))
        }
    }

    fun take(): PooledByteBuffer = pool.take()

    fun returnToPool(buffer: PooledByteBuffer) {
        if (buffer.pool != this) {
            throw IllegalArgumentException("mismatched pool")
        }
        buffer.reset()
        pool.put(buffer)
    }
}

/**
 * A thread-safe [ByteBuffer] wrapper for pooling, stores [refCount] for counting acquired references.
 */
class PooledByteBuffer(val buffer: ByteBuffer, val pool: ByteBufferPool, private val concurrency: Int) {

    private val refCount = AtomicInteger(concurrency)

    /**
     * release this buffer once, which decreases the [refCount] by 1.
     * when [refCount] goes to zero, we consider this buffer is not used anymore,
     * so we put it back into the pool where it belongs.
     */
    fun release() {
        if (refCount.decrementAndGet() == 0) {
            pool.returnToPool(this)
        }
    }

    /**
     * internal function, shouldn't invoked by user.
     * resets this buffer's position status for further reusing.
     * also resets the [refCount] to the desired [concurrency]
     */
    fun reset() {
        buffer.clear()
        if (!refCount.compareAndSet(0, concurrency)) {
            throw RuntimeException("unexpected CAS failure")
        }
    }
}
