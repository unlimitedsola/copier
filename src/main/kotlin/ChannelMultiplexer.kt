package love.sola.copier

import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

class ChannelMultiplexer(
    private val src: ReadableByteChannel,
    private val dst: List<WritableByteChannel>,
    val length: Long
) {

    companion object {
        private const val QUEUE_SIZE = 8
        private const val POOL_SIZE = QUEUE_SIZE * 2
        private const val BUFFER_SIZE = 64 * 1024
    }

    private val bufferPool = ByteBufferPool(POOL_SIZE, BUFFER_SIZE, dst.size)
    @Volatile
    var readBytes = 0L
    @Volatile
    var isCancelled = false
    @Volatile
    var isDone = false

    fun start() {
        if (isDone || isCancelled) throw IllegalStateException("ChannelMultiplexer can not be reused.")
        readBytes = 0L
        val workers = dst.map { Worker(it) }
        workers.forEach { it.start() }
        while (readBytes < length && !isCancelled) {
            val pooledBuffer = bufferPool.take()
            readBytes += src.read(pooledBuffer.buffer)
            pooledBuffer.buffer.rewind()
            workers.forEach { it.writingQueue.put(pooledBuffer) }
        }
        if (!isCancelled) isDone = true
        workers.forEach { it.isDone = true }
    }

    class Worker(private val dst: WritableByteChannel) : Thread("ChannelMultiplexer-Worker") {

        init {
            isDaemon = true
        }

        val writingQueue = LinkedBlockingQueue<PooledByteBuffer>(QUEUE_SIZE)
        @Volatile
        var isDone = false

        override fun run() {
            while (!isDone) {
                val pooledBuffer = writingQueue.poll(1, SECONDS) ?: continue
                dst.write(pooledBuffer.buffer.asReadOnlyBuffer())
                pooledBuffer.release()
            }
        }
    }
}
