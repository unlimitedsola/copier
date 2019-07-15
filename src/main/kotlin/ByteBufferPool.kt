package love.sola.copier

import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Sola
 */
class ByteBufferPool(val poolSize: Int, val bufferSize: Int, val expectedRefCount: Int) {

    private val pool = LinkedBlockingQueue<PooledByteBuffer>(poolSize)

    init {
        repeat(poolSize) {
            pool.put(PooledByteBuffer(ByteBuffer.allocateDirect(bufferSize), this, expectedRefCount))
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

class PooledByteBuffer(val buffer: ByteBuffer, val pool: ByteBufferPool, val expectedRefCount: Int) {

    val refCount = AtomicInteger(expectedRefCount)

    fun release() {
        if (refCount.decrementAndGet() == 0) {
            pool.returnToPool(this)
        }
    }

    fun reset() {
        buffer.clear()
        if (!refCount.compareAndSet(0, expectedRefCount)) {
            throw RuntimeException("unexpected CAS failure")
        }
    }
}
