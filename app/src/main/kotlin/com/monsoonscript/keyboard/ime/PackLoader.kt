package com.monsoonscript.keyboard.ime

import android.content.res.AssetManager
import com.monsoonscript.keyboard.predict.LangPack
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Opens a prediction pack (`assets/predict/<name>.mslm`). The files are stored uncompressed in the APK (`noCompress` in
 * app/build.gradle.kts), so they are memory-mapped straight out of the APK: nothing is copied, and Android loads only the
 * pages the lookups touch. If an asset were compressed anyway (a build setting lost), it is read into memory instead.
 */
internal object PackLoader {
    fun load(assets: AssetManager, name: String): LangPack? {
        val path = "predict/$name.mslm"
        return try {
            LangPack(map(assets, path))
        } catch (e: IOException) {
            null // missing or unreadable: prediction is simply off for this language
        } catch (e: IllegalArgumentException) {
            null // not a valid pack
        }
    }

    private fun map(assets: AssetManager, path: String): ByteBuffer {
        try {
            assets.openFd(path).use { fd ->
                FileInputStream(fd.fileDescriptor).use { input ->
                    return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        } catch (e: FileNotFoundException) {
            // compressed asset: openFd refuses it
        }
        return ByteBuffer.wrap(assets.open(path).use { it.readBytes() })
    }
}
