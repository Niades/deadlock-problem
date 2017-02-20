import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDeviceBase;

import javax.sound.sampled.*;

/**
 * The <code>MixerAudioDevice</code> implements an audio device by using the
 * JavaSound API.
 *
 * @since 0.0.8
 * @author Mat McGowan
 */
public class MixerAudioDevice extends AudioDeviceBase {

    private Mixer mixer;
    private SourceDataLine source = null;
    private AudioFormat fmt = null;
    private byte[] byteBuf = new byte[4096];

    public MixerAudioDevice(Mixer mixer) {
        this.mixer = mixer;
    }

    protected void setAudioFormat(AudioFormat fmt0) {
        fmt = fmt0;
    }

    protected AudioFormat getAudioFormat() {
        Decoder decoder = getDecoder();
        fmt = new AudioFormat(decoder.getOutputFrequency(),
                16,
                decoder.getOutputChannels(),
                true,
                false);
        return fmt;
    }

    protected DataLine.Info getSourceLineInfo() {
        AudioFormat fmt = getAudioFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
        return info;
    }

    public void open(AudioFormat fmt) throws JavaLayerException {
        if (!isOpen()) {
            setAudioFormat(fmt);
            openImpl();
            setOpen(true);
        }
    }

    public boolean setLineGain(float gain) {
        if (source != null) {
            FloatControl volControl = (FloatControl) source.getControl(FloatControl.Type.MASTER_GAIN);
            float newGain = Math.min(Math.max(gain, volControl.getMinimum()), volControl.getMaximum());
            volControl.setValue(newGain);
            return true;
        }
        return false;
    }

    public void openImpl()
            throws JavaLayerException {}

    public void createSource() throws JavaLayerException {
        Throwable t = null;
        try {
            Line line = this.mixer.getLine(getSourceLineInfo());
            if (line instanceof SourceDataLine) {
                source = (SourceDataLine) line;
                source.open(fmt);
                source.start();
            }
        } catch (RuntimeException ex) {
            t = ex;
        } catch (LinkageError ex) {
            t = ex;
        } catch (LineUnavailableException ex) {
            t = ex;
        }
        if (source == null) {
            throw new JavaLayerException("cannot obtain source audio line", t);
        }
    }

    public int millisecondsToBytes(AudioFormat fmt, int time) {
        return (int) (time * (fmt.getSampleRate() * fmt.getChannels() * fmt.getSampleSizeInBits()) / 8000.0);
    }

    protected void closeImpl() {
        if (source != null) {
            source.close();
        }
    }

    protected void writeImpl(short[] samples, int offs, int len)
            throws JavaLayerException {
        if (source == null) {
            createSource();
        }

        byte[] b = toByteArray(samples, offs, len);
        source.write(b, 0, len * 2);
    }

    protected byte[] getByteArray(int length) {
        if (byteBuf.length < length) {
            byteBuf = new byte[length + 1024];
        }
        return byteBuf;
    }

    protected byte[] toByteArray(short[] samples, int offs, int len) {
        byte[] b = getByteArray(len * 2);
        int idx = 0;
        short s;
        while (len-- > 0) {
            s = samples[offs++];
            b[idx++] = (byte) s;
            b[idx++] = (byte) (s >>> 8);
        }
        return b;
    }

    protected void flushImpl() {
        if (source != null) {
            source.drain();
        }
    }

    public int getPosition() {
        int pos = 0;
        if (source != null) {
            pos = (int) (source.getMicrosecondPosition() / 1000);
        }
        return pos;
    }
}