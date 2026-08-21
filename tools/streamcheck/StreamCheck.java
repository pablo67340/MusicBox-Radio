import com.musicbox.client.audio.HttpAudioStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.InputStream;

/**
 * Exercises the mod's real HTTP + MP3 pipeline against each configured station outside of
 * Minecraft, so dead or undecodable URLs can be caught before they ship as defaults.
 */
public final class StreamCheck {

    public static void main(String[] args) {
        for (String entry : args) {
            int split = entry.indexOf('=');
            String label = entry.substring(0, split);
            String url = entry.substring(split + 1);
            System.out.printf("%-26s ", label);
            System.out.flush();
            try {
                System.out.println(check(url));
            } catch (Throwable t) {
                System.out.println("FAIL  " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static String check(String url) throws Exception {
        try (HttpAudioStream http = HttpAudioStream.open(url)) {
            InputStream audio = http.body();
            int metaInt = http.icyMetaInt();

            Bitstream bitstream = new Bitstream(audio);
            Decoder decoder = new Decoder();

            int frames = 0;
            int rate = 0;
            int channels = 0;
            long samples = 0;
            long deadline = System.currentTimeMillis() + 12_000;

            while (frames < 60 && System.currentTimeMillis() < deadline) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    break;
                }
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();
                rate = out.getSampleFrequency();
                channels = out.getChannelCount();
                samples += out.getBufferLength() / Math.max(1, channels);
                frames++;
            }

            if (frames == 0) {
                return "FAIL  connected but decoded no MP3 frames";
            }
            return String.format("OK    %d Hz, %s, %d frames (%.2fs), icy-metaint=%d",
                    rate, channels == 2 ? "stereo" : "mono", frames, samples / (double) rate, metaInt);
        }
    }
}
