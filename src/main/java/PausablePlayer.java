import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.Player;

import java.io.InputStream;

/**
 * Created by daniil on 13.01.17.
 */
public class PausablePlayer {

    private final static int NOTSTARTED = 0;
    private final static int PLAYING = 1;
    private final static int PAUSED = 2;
    private final static int FINISHED = 3;

    // the player actually doing all the work
    private final Player player;

    // locking object used to communicate with player thread
    private final Object playerLock = new Object();

    // status variable what player thread is doing/supposed to do
    private int playerStatus = NOTSTARTED;

    private PlaybackListener listener;

    public PausablePlayer(final InputStream inputStream, PlaybackListener listener, final AudioDevice audioDevice) throws JavaLayerException {
        this.player = new Player(inputStream, audioDevice);
        this.listener = listener;
    }

    public PausablePlayer(final InputStream inputStream, final AudioDevice audioDevice) throws JavaLayerException {
        this.player = new Player(inputStream, audioDevice);
    }

    public boolean isPlaying() {
        return playerStatus == PLAYING;
    }

    /**
     * Starts playback (resumes if paused)
     */
    public void play() throws JavaLayerException {
        synchronized (playerLock) {
            switch (playerStatus) {
                case NOTSTARTED:
                    final Runnable r = new Runnable() {
                        public void run() {
                            playInternal();
                        }
                    };
                    final Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setPriority(Thread.MAX_PRIORITY);
                    playerStatus = PLAYING;
                    t.start();
                    break;
                case PAUSED:
                    resume();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Pauses playback. Returns true if new state is PAUSED.
     */
    public boolean pause() {
        synchronized (playerLock) {
            if (playerStatus == PLAYING) {
                playerStatus = PAUSED;
            }
            return playerStatus == PAUSED;
        }
    }

    /**
     * Resumes playback. Returns true if the new state is PLAYING.
     */
    public boolean resume() {
        synchronized (playerLock) {
            if (playerStatus == PAUSED) {
                playerStatus = PLAYING;
                playerLock.notifyAll();
            }
            return playerStatus == PLAYING;
        }
    }

    /**
     * Stops playback. If not playing, does nothing
     */
    public void stop() {
        synchronized (playerLock) {
            playerStatus = FINISHED;
            playerLock.notifyAll();
        }
    }

    private void playInternal() {
        while (playerStatus != FINISHED) {
            try {
                if (!player.play(1)) {
                    this.listener.playbackFinished(
                            new PlaybackEvent(
                                    this,
                                    PlaybackEvent.EventType.Instances.Stopped,
                                    0
                            )
                    );
                    break;
                }
            } catch (final JavaLayerException e) {
                break;
            }
            // check if paused or terminated
            synchronized (playerLock) {
                while (playerStatus == PAUSED) {
                    try {
                        playerLock.wait();
                    } catch (final InterruptedException e) {
                        // terminate player
                        break;
                    }
                }
            }
        }
        close();
    }

    /**
     * Closes the player, regardless of current state.
     */
    public void close() {
        synchronized (playerLock) {
            playerStatus = FINISHED;
        }
        try {
            player.close();
        } catch (final Exception e) {
            // ignore, we are terminating anyway
        }
    }

    public static class PlaybackEvent
    {
        public PausablePlayer source;
        public EventType eventType;
        public int frameIndex;

        public PlaybackEvent
                (
                        PausablePlayer source,
                        EventType eventType,
                        int frameIndex
                )
        {
            this.source = source;
            this.eventType = eventType;
            this.frameIndex = frameIndex;
        }

        public static class EventType
        {
            public String name;

            public EventType(String name)
            {
                this.name = name;
            }

            public static class Instances
            {
                public static EventType Started = new EventType("Started");
                public static EventType Stopped = new EventType("Stopped");
            }
        }
    }

    public static abstract class PlaybackListener
    {
        public void playbackStarted(PlaybackEvent event){}
        public void playbackFinished(PlaybackEvent event){}
    }
}