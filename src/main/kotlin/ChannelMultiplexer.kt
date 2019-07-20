package love.sola.copier

import love.sola.copier.util.ByteBufferPool
import love.sola.copier.util.PooledByteBuffer
import sun.nio.ch.FileChannelImpl
import java.io.IOException
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

/**
 * A channel multiplexer can copy from one source into multiple destinations in parallel.
 * the source and destinations' channel will be closed once the operation is done.
 * invoke [start] to get things going.
 *
 * NOTE: this instance can not be reused.
 *
 * @property length the length to transfer
 */
class ChannelMultiplexer(
    private val src: ReadableByteChannel,
    private val dst: List<WritableByteChannel>,
    val length: Long
) {

    companion object {
        private const val QUEUE_SIZE = 8
        private const val POOL_SIZE = QUEUE_SIZE * 2
        private const val BUFFER_SIZE = 64 * 512 // 64 sectors (usually).
    }

    private val bufferPool = ByteBufferPool(POOL_SIZE, BUFFER_SIZE, dst.size)
    @Volatile
    var readBytes = 0L
    @Volatile
    var isCancelled = false
    @Volatile
    var isDone = false

    /**
     * cancel this operation for a gracefully shutdown. can be invoked in any thread.
     */
    fun cancel() {
        isCancelled = true
    }

    /**
     * start the operation. blocks until done.
     */
    fun start() {
        if (isDone || isCancelled) throw IllegalStateException("ChannelMultiplexer can not be reused.")
        readBytes = 0L
        val workers = dst.map { Worker(it) }
        workers.forEach { it.start() }
        while (readBytes < length && !isCancelled) {
            val pooledBuffer = bufferPool.take()
            readBytes += src.read(pooledBuffer.buffer)
            pooledBuffer.buffer.rewind()
            workers.forEach { it.write(pooledBuffer) }
        }
        if (!isCancelled) isDone = true
        workers.forEach { it.isDone = true }
        src.close()
    }

    /**
     * the worker thread for writing into one destination.
     */
    class Worker(private val dst: WritableByteChannel) : Thread("ChannelMultiplexer-Worker") {

        init {
            isDaemon = true
        }

        private val writingQueue = LinkedBlockingQueue<PooledByteBuffer>(QUEUE_SIZE)
        @Volatile
        var isDone = false

        fun write(buffer: PooledByteBuffer) {
            writingQueue.put(buffer)
        }

        override fun run() {
            while (!isDone) {
                val pooledBuffer = writingQueue.poll(1, SECONDS) ?: continue
                try {
                    // sometimes we do fail for mysterious reasons.
                    retry<IOException>(4) { dst.write(pooledBuffer.buffer.asReadOnlyBuffer()) }
                } catch (e: IOException) {
                    throw if (dst is FileChannelImpl) {
                        IOException("IOException at ${dst.position()}", e)
                    } else e
                }
                pooledBuffer.release()
            }
            dst.close()
        }

        private inline fun <reified T : Throwable> retry(times: Int, block: () -> Unit) {
            var lastException: Throwable? = null
            repeat(times) {
                try {
                    block()
                    return
                } catch (e: Throwable) {
                    if (T::class.isInstance(e)) {
                        lastException = e
                        return@repeat
                    } else throw e
                }
            }
            throw lastException!!
        }
    }
}
