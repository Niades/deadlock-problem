import javazoom.jl.decoder.JavaLayerException;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.util.Scanner;

/**
 * Created by daniil on 20.02.17.
 */
public class Program {
    public static void main(String[] args) throws JavaLayerException, IOException, InterruptedException {
        System.out.println("Program starting");
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        Mixer.Info selected = mixers[0];
        for(Mixer.Info m : mixers) {
            if(m.getName().contains("Dante")) {
                selected = m;
                break;
            }
        }
        System.out.println("Selected mixer: " + selected.getName());
        Mixer mixer = AudioSystem.getMixer(selected);
        MixerAudioDevice device = new MixerAudioDevice(mixer);
        FileInputStream mp3File = new FileInputStream("test.mp3");
        PausablePlayer pp = new PausablePlayer(mp3File, device);
        pp.play();
        Thread.sleep(500);
        System.out.println("Started playing");
        pp.stop();
        System.out.println("Closing player");
        pp.close();
        System.out.println("Done.");
    }
}
