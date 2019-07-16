package love.sola.copier

import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
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

class PooledByteBuffer(val buffer: ByteBuffer, val pool: ByteBufferPool, val concurrency: Int) {

    val refCount = AtomicInteger(concurrency)

    fun release() {
        if (refCount.decrementAndGet() == 0) {
            pool.returnToPool(this)
        }
    }

    fun reset() {
        buffer.clear()
        if (!refCount.compareAndSet(0, concurrency)) {
            throw RuntimeException("unexpected CAS failure")
        }
    }
}
