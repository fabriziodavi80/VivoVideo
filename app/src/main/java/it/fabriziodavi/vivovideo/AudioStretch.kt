package it.fabriziodavi.vivovideo

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import it.fabriziodavi.vivovideo.model.MusicTrack
import it.fabriziodavi.vivovideo.model.Project
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import it.fabriziodavi.vivovideo.engine.AudioDsp
import it.fabriziodavi.vivovideo.engine.BusDsp
import it.fabriziodavi.vivovideo.engine.PeakLimiter
import it.fabriziodavi.vivovideo.model.Bus
import it.fabriziodavi.vivovideo.model.Mix
import it.fabriziodavi.vivovideo.model.bus
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ponte verso il codice nativo (Signalsmith Stretch). */
object NativeStretch {
    init {
        System.loadLibrary("vivostretch")
    }

    external fun stretch(
        input: FloatArray,
        channels: Int,
        sampleRate: Int,
        segEnd: DoubleArray,
        speed: DoubleArray,
    ): FloatArray
}

private const val OUT_RATE = 48_000
private const val OUT_CH = 2

/**
 * Costruisce la colonna sonora dell'intera timeline con tono naturale:
 * per ogni clip decodifica il tratto tagliato, lo allunga/accorcia con la sua curva
 * di velocità e lo accoda in un unico WAV stereo a 48 kHz.
 * Clip mute o senza audio diventano silenzio della durata giusta, così tutto resta in sincrono.
 * Alla fine vi somma i brani musicali.
 */
fun renderTimelineAudio(context: Context, project: Project, loudness: Boolean = false, onProgress: (Float) -> Unit = {}): File {
    val file = File(context.cacheDir, "timeline_${System.currentTimeMillis()}.wav")
    // Il mix si fa in virgola mobile (niente tagli prima del volume finale e del limitatore); il WAV a 16 bit alla fine.
    val raw = File(context.cacheDir, "mix_${System.currentTimeMillis()}.f32")
    val mix = project.mix
    val clipDsp = if (BusDsp.needed(mix, Bus.CLIPS)) BusDsp.of(mix, Bus.CLIPS) else null
    var frames = 0L
    BufferedOutputStream(FileOutputStream(raw), 1 shl 16).use { out ->
        project.clips.forEachIndexed { i, clip ->
            val target = clip.outputUs * OUT_RATE / 1_000_000
            val audio = if (clip.muted || clip.isPhoto) null else runCatching {
                decodeAudioRange(context, Uri.parse(clip.uri), clip.trimStartUs, clip.trimEndUs)
            }.getOrNull()
            var samples: FloatArray = if (audio == null || audio.isEmpty()) FloatArray(0) else {
                val curve = clip.speedCurve()
                if (curve.speeds.all { it == 1f }) audio else {
                    val ends = DoubleArray(curve.segEndUs.size) { k ->
                        if (k == curve.segEndUs.lastIndex) Double.MAX_VALUE
                        else curve.segEndUs[k] * OUT_RATE / 1_000_000.0
                    }
                    val sp = DoubleArray(curve.speeds.size) { curve.speeds[it].toDouble() }
                    NativeStretch.stretch(audio, OUT_CH, OUT_RATE, ends, sp)
                }
            }
            if (clip.volume != 1f) for (k in samples.indices) samples[k] *= clip.volume
            // Lunghezza esatta della clip nel video: si completa con silenzio o si taglia.
            val needed = (target * OUT_CH).toInt()
            if (samples.size != needed) samples = samples.copyOf(needed)
            // Dissolvenze in entrata/uscita della clip anche sull'audio.
            if (clip.fadeAudio && (clip.fadeInUs > 0 || clip.fadeOutUs > 0)) {
                val frames = samples.size / OUT_CH
                val fin = (clip.fadeInUs * OUT_RATE / 1_000_000).toInt().coerceAtMost(frames)
                val fout = (clip.fadeOutUs * OUT_RATE / 1_000_000).toInt().coerceAtMost(frames)
                for (f in 0 until fin) { val g = f.toFloat() / fin; for (ch in 0 until OUT_CH) samples[f * OUT_CH + ch] *= g * g }
                for (f in 0 until fout) {
                    val g = f.toFloat() / fout
                    for (ch in 0 until OUT_CH) samples[(frames - 1 - f) * OUT_CH + ch] *= g * g
                }
            }
            // Micro-dissolvenze (8 ms) agli estremi di ogni clip: niente "clic" sui tagli.
            val ramp = minOf(OUT_RATE * 8 / 1000, samples.size / OUT_CH / 2)
            for (f in 0 until ramp) {
                val g = f.toFloat() / ramp
                for (ch in 0 until OUT_CH) {
                    samples[f * OUT_CH + ch] *= g
                    samples[samples.size - (f + 1) * OUT_CH + ch] *= g
                }
            }
            clipDsp?.process(samples)
            writeF32(out, samples)
            frames += target
            onProgress((i + 1f) / project.clips.size * if (project.music.isEmpty() && project.layers.none { it.isVideo }) 1f else 0.6f)
        }
    }
    RandomAccessFile(raw, "rw").use { raf ->
        // Prima le voci (voce fuori campo, audio staccato) e l'audio dei video sovrapposti, poi la musica:
        // così si sa dove si parla e la musica può abbassarsi lì (ducking).
        val layerAudio = project.layers.filter { it.isVideo && !it.muted && it.startUs < project.durationUs }.map { l ->
            MusicTrack(
                uri = l.uri, name = l.name, sourceDurationUs = l.sourceDurationUs, startUs = l.startUs,
                offsetUs = l.sourceStartUs, lengthUs = l.durationUs, volume = l.volume, fadeInUs = 30_000, fadeOutUs = 30_000, dialog = true,
            )
        }
        // Gruppi del mixer muti (o esclusi dal "solo"): non si leggono nemmeno.
        val dialog = (project.audibleMusic().filter { it.dialog } + layerAudio).filter { mix.audible(busOf(it, layerAudio)) }
        val music = project.audibleMusic().filter { !it.dialog && mix.audible(Bus.MUSIC) }
        val all = dialog.size + music.size
        var k = 0
        val ducking = project.duck && music.isNotEmpty()
        // Dove si parla: le voci (voce fuori campo, audio staccato) si misurano da sole, l'audio delle clip a parte,
        // con una soglia sopra il suo rumore di fondo.
        val clipLevels = if (ducking) levelsOf(raf, frames) else null
        val voiceEnergy = if (ducking) DoubleArray((frames / AudioDsp.BLOCK).toInt()) else null
        dialog.forEach { t -> mixTrack(context, raf, frames, t, project.durationUs, null, voiceEnergy, dspFor(mix, busOf(t, layerAudio))); onProgress(0.6f + 0.3f * ++k / all) }
        val duck = if (ducking) {
            val v = AudioDsp.voiceMask(AudioDsp.levelsFromEnergy(voiceEnergy!!))
            val c = AudioDsp.clipSpeechMask(clipLevels!!)
            AudioDsp.duckGainsFrom(BooleanArray(v.size) { v[it] || c.getOrElse(it) { false } }, project.duckDb.toFloat(),
                mix.duckSpeed.holdBlocks, mix.duckSpeed.releaseBlocks)
        } else null
        music.forEach { t -> mixTrack(context, raf, frames, t, project.durationUs, duck, null, dspFor(mix, Bus.MUSIC)); onProgress(0.6f + 0.3f * ++k / all) }
        // Uscita: volume per la destinazione scelta e limitatore dei picchi, poi WAV a 16 bit.
        finish(raf, frames, file, if (loudness) mix.target.lufs else null, mix.limiter)
        onProgress(1f)
    }
    raw.delete()
    return file
}

private fun busOf(t: MusicTrack, layerAudio: List<MusicTrack>): Bus = if (t in layerAudio) Bus.OVERLAY else t.bus()

private fun dspFor(mix: Mix, b: Bus): BusDsp? = if (BusDsp.needed(mix, b)) BusDsp.of(mix, b) else null

private fun writeF32(out: BufferedOutputStream, samples: FloatArray) {
    val chunk = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
    for (v in samples) {
        if (!chunk.hasRemaining()) { out.write(chunk.array(), 0, chunk.position()); chunk.clear() }
        chunk.putFloat(v)
    }
    out.write(chunk.array(), 0, chunk.position())
}

/** Legge [n] fotogrammi del mix in virgola mobile a partire da [from]. */
private fun readF32(raf: RandomAccessFile, from: Long, n: Int, bytes: ByteArray, out: FloatArray) {
    raf.seek(from * OUT_CH * 4)
    raf.readFully(bytes, 0, n * OUT_CH * 4)
    val bb = ByteBuffer.wrap(bytes, 0, n * OUT_CH * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until n * OUT_CH) out[i] = bb.getFloat(i * 4)
}

/**
 * Uscita del mix: porta il volume percepito a [targetLufs] (null = com'è) e scrive il WAV.
 * Con il limitatore il volume arriva al livello scelto e i picchi si tengono sotto -1 dBFS;
 * senza, il guadagno si ferma prima che i picchi superino -1 dBFS.
 */
private fun finish(raf: RandomAccessFile, frames: Long, wav: File, targetLufs: Double?, limiter: Boolean) {
    val blockFrames = 1 shl 14
    val bytes = ByteArray(blockFrames * OUT_CH * 4)
    val buf = FloatArray(blockFrames * OUT_CH)
    var gain = 1f
    if (targetLufs != null) {
        val meter = AudioDsp.LoudnessMeter()
        var done = 0L
        while (done < frames) {
            val n = minOf(blockFrames.toLong(), frames - done).toInt()
            readF32(raf, done, n, bytes, buf)
            for (f in 0 until n) meter.add(buf[f * 2], buf[f * 2 + 1])
            done += n
        }
        val lufs = meter.lufs()
        gain = if (lufs <= -69.0) 1f
            else if (limiter) minOf(Math.pow(10.0, (targetLufs - lufs) / 20), 8.0).toFloat()
            else AudioDsp.normalizeGain(lufs, meter.peak, targetLufs)
    }
    val lim = if (limiter) PeakLimiter() else null
    BufferedOutputStream(FileOutputStream(wav), 1 shl 16).use { out ->
        out.write(wavHeader(frames))
        var done = 0L
        while (done < frames) {
            val n = minOf(blockFrames.toLong(), frames - done).toInt()
            readF32(raf, done, n, bytes, buf)
            if (gain != 1f) for (i in 0 until n * OUT_CH) buf[i] *= gain
            lim?.process(buf, 0, n)
            writePcm16(out, if (n * OUT_CH == buf.size) buf else buf.copyOf(n * OUT_CH))
            done += n
        }
    }
}

/** Livelli (10 ms) di quello che è già nel WAV: clip e voci, per capire dove si parla. */
private fun levelsOf(raf: RandomAccessFile, frames: Long): FloatArray {
    val blocks = (frames / AudioDsp.BLOCK).toInt()
    val out = FloatArray(blocks)
    val bytes = ByteArray(AudioDsp.BLOCK * OUT_CH * 4)
    val tmp = FloatArray(AudioDsp.BLOCK * OUT_CH)
    for (b in 0 until blocks) {
        readF32(raf, b.toLong() * AudioDsp.BLOCK, AudioDsp.BLOCK, bytes, tmp)
        out[b] = AudioDsp.blockLevels(tmp)[0]
    }
    return out
}

/** Somma un brano (canale del mixer, volume, dissolvenze, ducking) nel mix in virgola mobile, a blocchi. */
private fun mixTrack(context: Context, raf: RandomAccessFile, totalFrames: Long, t: MusicTrack, projectUs: Long, duck: FloatArray?, energy: DoubleArray? = null, dsp: BusDsp? = null) {
    val len = t.playUs(projectUs)
    val audio = runCatching {
        decodeAudioRange(context, Uri.parse(t.uri), t.offsetUs, t.offsetUs + len)
    }.getOrNull() ?: return
    val startFrame = t.startUs * OUT_RATE / 1_000_000
    val frames = minOf((audio.size / OUT_CH).toLong(), totalFrames - startFrame, len * OUT_RATE / 1_000_000)
    if (frames <= 0) return
    // Canale del mixer (equalizzatore, compressore, volume, bilanciamento) prima di misurare la voce:
    // una voce messa muta o abbassata non fa abbassare la musica.
    dsp?.process(audio, 0, frames.toInt())
    energy?.let { AudioDsp.addEnergy(it, audio, startFrame, frames) { fi -> t.gainAt(fi * 1_000_000 / OUT_RATE, projectUs) } }
    val blockFrames = 1 shl 14
    val bytes = ByteArray(blockFrames * OUT_CH * 4)
    var done = 0L
    while (done < frames) {
        val n = minOf(blockFrames.toLong(), frames - done).toInt()
        val pos = (startFrame + done) * OUT_CH * 4
        raf.seek(pos)
        raf.readFully(bytes, 0, n * OUT_CH * 4)
        val bb = ByteBuffer.wrap(bytes, 0, n * OUT_CH * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until n) {
            val fi = done + f
            var g = t.gainAt(fi * 1_000_000 / OUT_RATE, projectUs)
            if (duck != null) g *= duck.getOrElse(((startFrame + fi) / AudioDsp.BLOCK).toInt()) { 1f }
            for (c in 0 until OUT_CH) {
                val idx = (f * OUT_CH + c) * 4
                bb.putFloat(idx, bb.getFloat(idx) + audio[(fi * OUT_CH + c).toInt()] * g)
            }
        }
        raf.seek(pos)
        raf.write(bytes, 0, n * OUT_CH * 4)
        done += n
    }
}

/** Scrive un WAV stereo a 48 kHz (16 bit). */
fun writeWavFile(file: File, stereo: FloatArray) {
    BufferedOutputStream(FileOutputStream(file), 1 shl 16).use { out ->
        out.write(wavHeader((stereo.size / OUT_CH).toLong()))
        writePcm16(out, stereo)
    }
}

private fun writePcm16(out: BufferedOutputStream, samples: FloatArray) {
    val chunk = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
    for (v in samples) {
        if (!chunk.hasRemaining()) { out.write(chunk.array(), 0, chunk.position()); chunk.clear() }
        chunk.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
    }
    out.write(chunk.array(), 0, chunk.position())
}

private fun wavHeader(frames: Long): ByteArray {
    val dataBytes = (frames * OUT_CH * 2).toInt()
    val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    h.put("RIFF".toByteArray()); h.putInt(36 + dataBytes)
    h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray())
    h.putInt(16); h.putShort(1); h.putShort(OUT_CH.toShort())
    h.putInt(OUT_RATE); h.putInt(OUT_RATE * OUT_CH * 2)
    h.putShort((OUT_CH * 2).toShort()); h.putShort(16)
    h.put("data".toByteArray()); h.putInt(dataBytes)
    return h.array()
}

/**
 * Decodifica l'audio fra startUs e endUs e lo restituisce stereo a 48 kHz (interleaved).
 * Restituisce null se il video non ha audio.
 */
fun decodeAudioRange(context: Context, uri: Uri, startUs: Long, endUs: Long): FloatArray? {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, uri, null)
    var track = -1
    var format: MediaFormat? = null
    for (i in 0 until extractor.trackCount) {
        val f = extractor.getTrackFormat(i)
        if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
            track = i; format = f; break
        }
    }
    if (track < 0 || format == null) {
        extractor.release(); return null
    }
    extractor.selectTrack(track)
    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    codec.configure(format, null, null, 0)
    codec.start()

    var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var floatPcm = false
    // Accumula stereo alla frequenza originale; il ricampionamento si fa alla fine.
    var data = FloatArray(1 shl 20)
    var size = 0
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    var outputDone = false
    try {
        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val read = extractor.readSampleData(buf, 0)
                    if (read < 0 || extractor.sampleTime > endUs + 100_000) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, read, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val of = codec.outputFormat
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                outIdx >= 0 -> {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    buf.order(ByteOrder.nativeOrder())
                    val count = if (floatPcm) info.size / 4 else info.size / 2
                    val framesInBuf = count / channels
                    val fb = if (floatPcm) buf.asFloatBuffer() else null
                    val sb = if (floatPcm) null else buf.asShortBuffer()
                    for (f in 0 until framesInBuf) {
                        val t = info.presentationTimeUs + f * 1_000_000L / rate
                        if (t < startUs || t >= endUs) continue
                        fun s(c: Int): Float {
                            val idx = f * channels + minOf(c, channels - 1)
                            return fb?.get(idx) ?: (sb!!.get(idx) / 32768f)
                        }
                        if (size + 2 > data.size) data = data.copyOf(data.size * 2)
                        data[size++] = s(0)
                        data[size++] = s(1)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
    } finally {
        codec.stop(); codec.release(); extractor.release()
    }
    val stereo = data.copyOf(size)
    return if (rate == OUT_RATE) stereo else resampleStereo(stereo, rate, OUT_RATE)
}

private fun resampleStereo(input: FloatArray, from: Int, to: Int): FloatArray {
    val inFrames = input.size / 2
    val outFrames = (inFrames.toLong() * to / from).toInt()
    val out = FloatArray(outFrames * 2)
    for (i in 0 until outFrames) {
        val pos = i.toDouble() * from / to
        val i0 = pos.toInt().coerceAtMost(inFrames - 1)
        val i1 = (i0 + 1).coerceAtMost(inFrames - 1)
        val f = (pos - i0).toFloat()
        out[2 * i] = input[2 * i0] * (1 - f) + input[2 * i1] * f
        out[2 * i + 1] = input[2 * i0 + 1] * (1 - f) + input[2 * i1 + 1] * f
    }
    return out
}
