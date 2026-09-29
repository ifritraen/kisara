package eu.kanade.tachiyomi.data.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object AiBufferUtils {
    /**
     * Allocates a native direct [FloatBuffer] required for JNI and ONNX Runtime tensors.
     */
    fun allocateDirectFloatBuffer(capacity: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(capacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }
}
