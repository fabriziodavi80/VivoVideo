package it.fabriziodavi.vivovideo.engine

import android.content.Context
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory

/**
 * Decodificatori per Transformer. Di serie quelli del telefono (hardware, veloci); con [softwareOnly]
 * quelli software: più lenti ma non si guastano a metà quando i decodificatori hardware sono esauriti.
 */
object Decoders {
    fun factory(context: Context, softwareOnly: Boolean): DefaultDecoderFactory {
        val b = DefaultDecoderFactory.Builder(context)
        if (softwareOnly) {
            b.setMediaCodecSelector { mime, secure, tunneling ->
                val all = MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
                all.filter { it.softwareOnly }.ifEmpty { all }
            }
        }
        return b.build()
    }

    fun assetLoader(context: Context, softwareOnly: Boolean) =
        DefaultAssetLoaderFactory(context, factory(context, softwareOnly), Clock.DEFAULT, null)
}
