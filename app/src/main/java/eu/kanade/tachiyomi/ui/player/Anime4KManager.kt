package eu.kanade.tachiyomi.ui.player

import android.content.Context
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileOutputStream

/**
 * Anime4K Manager
 * Manages GLSL shaders for real-time anime upscaling and restoration
 */
class Anime4KManager(private val context: Context) {

    companion object {
        const val SHADER_DIR = "shaders"
    }

    // Shader quality levels
    enum class Quality(val suffix: String) {
        FAST("S"),
        BALANCED("M"),
        HIGH("L"),
    }

    // Anime4K modes
    enum class Mode {
        OFF,
        A,
        B,
        C,
        A_PLUS,
        B_PLUS,
        C_PLUS,
    }

    private var shaderDir: File? = null
    private var isInitialized = false

    /**
     * Initialize: unpack shaders from assets to internal storage with size and timestamp validation.
     * This must be called and complete successfully before using getShaderChain().
     */
    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized && shaderDir?.exists() == true) {
            return true
        }

        return try {
            shaderDir = File(context.filesDir, SHADER_DIR)
            if (!shaderDir!!.exists()) {
                val created = shaderDir!!.mkdirs()
                if (!created) {
                    logcat(LogPriority.ERROR) { "Failed to create shader directory: ${shaderDir!!.absolutePath}" }
                    return false
                }
            }

            val shaderFiles = context.assets.list(SHADER_DIR) ?: emptyArray()

            for (fileName in shaderFiles) {
                if (fileName.endsWith(".glsl")) {
                    copyShaderFromAssets(fileName)
                }
            }

            isInitialized = true
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to unpack Anime4K shaders from assets" }
            isInitialized = false
            false
        }
    }

    private fun copyShaderFromAssets(fileName: String): Boolean {
        val destFile = File(shaderDir, fileName)

        return try {
            context.assets.open("$SHADER_DIR/$fileName").use { input ->
                val assetSize = input.available().toLong()

                // Skip unpacking if file already exists, is non-empty, and matches asset size
                if (destFile.exists() && destFile.length() > 0L && (assetSize <= 0L || destFile.length() == assetSize)) {
                    return false
                }

                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
                destFile.setLastModified(System.currentTimeMillis())
                logcat(LogPriority.INFO) { "Unpacked Anime4K shader: $fileName (${destFile.length()} bytes)" }
                true
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to copy shader: $fileName" }
            false
        }
    }

    /**
     * Get colon-separated shader chain for the specified mode and quality.
     * Returns empty string if mode is OFF or initialization failed.
     */
    fun getShaderChain(mode: Mode, quality: Quality): String {
        if (mode == Mode.OFF) {
            return ""
        }

        if (!isInitialized || shaderDir?.exists() != true) {
            initialize()
        }

        if (shaderDir == null || !shaderDir!!.exists()) {
            return ""
        }

        val shaders = mutableListOf<String>()
        val q = quality.suffix

        // Always prepend Clamp_Highlights to eliminate ringing artifacts
        shaders.add(getShaderPath("Anime4K_Clamp_Highlights.glsl"))

        // Add shaders based on mode
        when (mode) {
            Mode.A -> {
                // Mode A: Restore -> Upscale -> AutoDownscale -> Upscale
                shaders.add(getShaderPath("Anime4K_Restore_CNN_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.B -> {
                // Mode B: Restore Soft -> Upscale -> AutoDownscale -> Upscale
                shaders.add(getShaderPath("Anime4K_Restore_CNN_Soft_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.C -> {
                // Mode C: Upscale Denoise -> AutoDownscale -> Upscale
                shaders.add(getShaderPath("Anime4K_Upscale_Denoise_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.A_PLUS -> {
                // Mode A+A: Restore -> Upscale -> AutoDownscale -> Restore -> Upscale
                shaders.add(getShaderPath("Anime4K_Restore_CNN_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Restore_CNN_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.B_PLUS -> {
                // Mode B+B: Restore Soft -> Upscale -> AutoDownscale -> Restore Soft -> Upscale
                shaders.add(getShaderPath("Anime4K_Restore_CNN_Soft_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Restore_CNN_Soft_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.C_PLUS -> {
                // Mode C+A: Upscale Denoise -> AutoDownscale -> Restore -> Upscale
                shaders.add(getShaderPath("Anime4K_Upscale_Denoise_CNN_x2_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_AutoDownscalePre_x2.glsl"))
                shaders.add(getShaderPath("Anime4K_Restore_CNN_$q.glsl"))
                shaders.add(getShaderPath("Anime4K_Upscale_CNN_x2_$q.glsl"))
            }
            Mode.OFF -> {}
        }

        // Validate that all shader files exist
        val missingShaders = shaders.filter { path ->
            !File(path).exists()
        }

        if (missingShaders.isNotEmpty()) {
            logcat(LogPriority.WARN) { "Anime4K shaders missing on disk: $missingShaders" }
            return ""
        }

        return shaders.joinToString(":")
    }

    private fun getShaderPath(fileName: String): String {
        return File(shaderDir, fileName).absolutePath
    }
}
