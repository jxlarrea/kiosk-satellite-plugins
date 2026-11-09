// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.nio.ByteBuffer;
import java.util.Collections;
import me.jxl.kiosk.plugins.AudioOutput;
import me.jxl.kiosk.plugins.PluginPlayer;

/**
 * Plays one address at a time through the kiosk's audio output. Android's MediaExtractor reads the file over HTTPS,
 * MediaCodec decodes it to 16-bit PCM and the PCM goes to the output Kiosk Satellite opens, so the echo canceller, the
 * media volume and the assistant's duck apply to it. Decoding runs on a thread of its own.
 */
final class StreamPlayer {
    interface Listener {
        /** The first frames of a play are on their way, from positionMs. */
        void onStarted(long positionMs);

        /** The track played to its end. */
        void onEnded();

        void onError(String message);
    }

    private static final long TIMEOUT_US = 10_000;
    private final PluginPlayer player;
    private final Listener listener;
    private final Object lock = new Object();
    private Thread thread;
    private volatile boolean running;
    private AudioOutput output;
    private int outputRate;
    private int outputChannels;
    private volatile int rate = 44100;
    private volatile long startMs;
    /** The last position the output reported, for after it closed. */
    private volatile long lastPositionMs;
    private final long[] timestamp = new long[2];

    /** KS closed the output because the pick moved on: not an error to report. */
    private static final class OutputClosed extends RuntimeException {
        OutputClosed() { super("The audio output closed"); }
    }

    StreamPlayer(PluginPlayer player, Listener listener) {
        this.player = player;
        this.listener = listener;
    }

    /** Play url from positionMs, replacing whatever plays. */
    void play(String url, long positionMs) {
        stop();
        synchronized (lock) {
            startMs = positionMs;
            lastPositionMs = positionMs;
            running = true;
            Thread next = new Thread(() -> decode(url, positionMs), "hello-world-archive");
            next.setDaemon(true);
            thread = next;
            next.start();
        }
    }

    /** Stop and drop what is queued on the output. */
    void stop() {
        Thread current;
        synchronized (lock) {
            running = false;
            current = thread;
            thread = null;
        }
        if (current != null) {
            current.interrupt();
            try {
                current.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (lock) {
            if (output != null) output.flush();
        }
    }

    /** Release the output for good. */
    void close() {
        stop();
        synchronized (lock) {
            if (output != null) output.close();
            output = null;
        }
    }

    /**
     * Where playback is: the start plus what the output reports heard, moved on to now. Once the output closes, as
     * when the pick moves on, the last position it reported.
     */
    long positionMs() {
        synchronized (lock) {
            if (output == null || !output.getTimestamp(timestamp)) return lastPositionMs;
            long frames = timestamp[0] + (System.nanoTime() - timestamp[1]) * rate / 1_000_000_000L;
            lastPositionMs = startMs + Math.max(0, frames) * 1000 / rate;
            return lastPositionMs;
        }
    }

    private void decode(String url, long positionMs) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(url, Collections.singletonMap("User-Agent", "KioskSatelliteHelloWorld/1.1 (plugin example)"));
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; format = candidate; break; }
            }
            if (track < 0) throw new IllegalStateException("The file has no audio");
            extractor.selectTrack(track);
            if (positionMs > 0) extractor.seekTo(positionMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            // The seek lands on a frame boundary, which is where the position starts from.
            long landed = Math.max(0, extractor.getSampleTime() / 1000);
            startMs = landed;
            lastPositionMs = landed;
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean started = false;
            while (running) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (in >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(in);
                        int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat decoded = codec.getOutputFormat();
                    open(decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE), decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                } else if (out >= 0) {
                    ByteBuffer pcm = codec.getOutputBuffer(out);
                    if (pcm != null && info.size > 0) {
                        if (output == null) open(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                        if (!started) { started = true; listener.onStarted(landed); }
                        pcm.position(info.offset);
                        pcm.limit(info.offset + info.size);
                        write(pcm);
                    }
                    codec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        drain();
                        if (running) listener.onEnded();
                        return;
                    }
                }
            }
        } catch (OutputClosed closed) {
            // The player stopped being the pick. The demo hears onDeactivate.
        } catch (Exception error) {
            if (running) listener.onError(error.getMessage() != null ? error.getMessage() : error.toString());
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (RuntimeException ignored) { }
                codec.release();
            }
            extractor.release();
        }
    }

    /** Keep one output while the format stays, so the next track follows without a new stream. */
    private void open(int sampleRate, int channels) {
        if (channels < 1 || channels > 2) throw new IllegalStateException("Only mono and stereo files play");
        synchronized (lock) {
            rate = sampleRate;
            if (output != null && outputRate == sampleRate && outputChannels == channels) return;
            output = player.openAudio(sampleRate, channels, PluginPlayer.ENCODING_PCM_16);
            outputRate = sampleRate;
            outputChannels = channels;
        }
    }

    private void write(ByteBuffer pcm) {
        int frameBytes = outputChannels * 2;
        while (running && pcm.remaining() >= frameBytes) {
            int taken = output.write(pcm, pcm.remaining() / frameBytes, 500);
            if (taken < 0) throw new OutputClosed();
        }
    }

    /** Wait until what is queued has been heard, so the end of a track is not cut off. */
    private void drain() {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (running && System.nanoTime() < deadline) {
            synchronized (lock) {
                if (output == null || !output.getTimestamp(timestamp)) return;
                long heard = timestamp[0] + (System.nanoTime() - timestamp[1]) * rate / 1_000_000_000L;
                if (heard >= output.framesWritten()) return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException stopped) {
                return;
            }
        }
    }
}
