import javax.sound.sampled.*;
import javax.swing.JFrame;
import javax.swing.JLabel;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultipleSoundsSingleChannelTest implements KeyListener {

    private static class Sound {

        public byte[] data;
        public int sampleIndex;

        public Sound(String resource) {

            try (InputStream is = getClass().getResourceAsStream(resource);
                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(is)) {

                AudioFormat format = audioInputStream.getFormat();
                
                System.out.println("format: " + format + " encoding: " + format.getEncoding());
                if (format.getChannels() != 1 || format.getSampleSizeInBits() != 8 || format.getSampleRate() != 22050) {
                    throw new Exception("invalid audio format " + format + " !");
                }
                // int sampleRate = (int) format.getSampleRate();

                int frameSize = format.getFrameSize();
                data = new byte[(int) (frameSize * audioInputStream.getFrameLength())];
                audioInputStream.read(data);
            } 
            catch (UnsupportedAudioFileException | IOException e) {
                e.printStackTrace();
            } 
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        public boolean hasNextSample() {
            return sampleIndex < data.length;
        }

        public int getNextSample() {
            byte sample = 0;
            if (hasNextSample()) {
                sample = data[sampleIndex];
                sampleIndex++;
            }
            return (sample & 0xff);
        }

    }

    private static final int SAMPLE_RATE = 22050;
    private static final int BUFFER_SIZE = 512;

    private SourceDataLine dataLine;
    private boolean[] keyStates;
    
    private Sound sound0;
    private Sound sound1;
    private Sound sound2;
 
    private List<Sound> playingSoundsTmp = new ArrayList<>();  // Sua lista original
    private List<Sound> playingSounds = Collections.synchronizedList(playingSoundsTmp);
 
    public MultipleSoundsSingleChannelTest() {
        try {
            AudioFormat audioFormat = new AudioFormat(SAMPLE_RATE, 8, 1, false, false);
            //DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
            dataLine = (SourceDataLine) AudioSystem.getSourceDataLine(audioFormat);
            dataLine.open(audioFormat, BUFFER_SIZE);
            dataLine.start();
    
            Thread t = new Thread(() -> generateContinuosOutput());
            t.start();
            
            keyStates = new boolean[128]; // Assume MIDI notes range from 0 to 127

            sound0 = new Sound("#0.wav");
            sound1 = new Sound("#1.wav");
            sound2 = new Sound("#2.wav");
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }
    
    public void generateContinuosOutput() {
        List<Sound> removableSounds = new ArrayList<>();
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            for (int i = 0; i < BUFFER_SIZE; i++) {
                synchronized (playingSounds) {
                    int mixedSample = 0;
                    for (Sound sound : playingSounds) {
                        int sample = sound.getNextSample() - 128;
                        mixedSample = Math.max(Math.min(mixedSample + sample, 127), -128);
                            

                        if (!sound.hasNextSample()) {
                            removableSounds.add(sound);
                        }
                    }
                    buffer[i] = (byte) (mixedSample + 128);

                    if (!removableSounds.isEmpty()) {
                        playingSounds.removeAll(removableSounds);
                        removableSounds.clear();
                    }
                }
                //System.out.println("generateContinuosOutput()");
            }
            dataLine.write(buffer, 0, BUFFER_SIZE);
            // System.out.println(dataLine.getMicrosecondPosition());
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int note = e.getKeyCode();
        if (note == 65) {
            sound0.sampleIndex = 0;
            synchronized (playingSounds) {
                //System.out.println("keyPressed(65)");
                if (!playingSounds.contains(sound0)) {
                    playingSounds.add(sound0);
                }
            }
        }
        if (note == 66) {
            sound1.sampleIndex = 0;
            synchronized (playingSounds) {
                //System.out.println("keyPressed(66)");
                if (!playingSounds.contains(sound1)) {
                    playingSounds.add(sound1);
                }
            }
        }
        if (note == 67) {
            sound2.sampleIndex = 0;
            synchronized (playingSounds) {
                //System.out.println("keyPressed(67)");
                if (!playingSounds.contains(sound2)) {
                    playingSounds.add(sound2);
                }
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int note = e.getKeyCode();
        keyStates[note] = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    public static void main(String[] args) {
        MultipleSoundsSingleChannelTest midiPlayer = new MultipleSoundsSingleChannelTest();
        JFrame frame = new JFrame("Playing Multiple Sounds using 1 channel test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 200);
        frame.addKeyListener(midiPlayer);
        frame.setFocusable(true);
        frame.setVisible(true);
        
        JLabel label = new JLabel("press A, B or C at same time", JLabel.CENTER);
        frame.getContentPane().add(label);

        frame.requestFocus();

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                midiPlayer.dataLine.drain();
                midiPlayer.dataLine.close();
                System.exit(0);
            }
        });
    }
}
