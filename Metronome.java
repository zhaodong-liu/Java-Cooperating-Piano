import java.awt.*;
import java.awt.geom.AffineTransform;
import javax.swing.*;

public class Metronome {
    private static final int MIN_BPM = 40;
    private static final int MAX_BPM = 240;

    private final JPanel panel;
    private final JSpinner bpmSpinner;
    private final JButton startStopBtn;
    private final PendulumPanel pendulum;
    private Timer driver;
    private long nextBeatTime;
    private int intervalMs;
    private int direction = 1;   // +1 swing right next, –1 swing left next

    public Metronome() {
        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Metronome(40-240 BPM)"));

        JPanel ctrl = new JPanel();
        ctrl.add(new JLabel("BPM:"));
        bpmSpinner = new JSpinner(new SpinnerNumberModel(120, MIN_BPM, MAX_BPM, 1));
        startStopBtn = new JButton("Start");
        ctrl.add(bpmSpinner);
        ctrl.add(startStopBtn);
        panel.add(ctrl, BorderLayout.NORTH);

        bpmSpinner.addChangeListener(e -> {
            int bpm = (Integer) bpmSpinner.getValue();
            intervalMs = 60000 / bpm;
            nextBeatTime = System.currentTimeMillis() + intervalMs;
        });

        pendulum = new PendulumPanel(200, 260, bpmSpinner);
        panel.add(pendulum, BorderLayout.CENTER);

        startStopBtn.addActionListener(e -> toggle());
    }

    public JPanel getPanel() {
        return panel;
    }

    private void toggle() {
        if (driver == null) {
            direction    = 1;
            int bpm      = (Integer) bpmSpinner.getValue();
            intervalMs   = 60000 / bpm;
            nextBeatTime = System.currentTimeMillis();
            tick();

            driver = new Timer(10, ev -> {
                long now = System.currentTimeMillis();
                if (now >= nextBeatTime) tick();
                pendulum.update(now, nextBeatTime, intervalMs, direction, (Integer)bpmSpinner.getValue());
            });
            driver.start();
            startStopBtn.setText("Stop");
        } else {
            driver.stop();
            driver = null;
            pendulum.reset();
            startStopBtn.setText("Start");
        }
    }

    private void tick() {
        new Thread(ToneGenerator::beepSound).start();
        nextBeatTime += intervalMs;
        direction = -direction;
    }

    private static class PendulumPanel extends JPanel {
        private final int w, h;
        private final JSpinner spinner;
        private long nextBeat, interval;
        private int direction, bpm;
        private double angle;  
        private double weightFrac;

        PendulumPanel(int width, int height, JSpinner spinner) {
            this.w = width;
            this.h = height;
            this.spinner = spinner;
            setPreferredSize(new Dimension(w, h));
        }

        void update(long now, long nextBeat, long interval, int direction, int bpm) {
            this.nextBeat  = nextBeat;
            this.interval  = interval;
            this.direction = direction;
            this.bpm       = bpm;

            long lastBeat = nextBeat - interval;
            double t = (now - lastBeat) / (double)interval;
            t = Math.min(1.0, Math.max(0.0, t));

            double maxAng = Math.toRadians(30) * (MIN_BPM / (double)bpm);

            int lastDir = -direction;
            angle = 2 * lastDir * maxAng * Math.cos(Math.PI * t);

            weightFrac = (MAX_BPM - bpm) / (double)(MAX_BPM - MIN_BPM);
            weightFrac = Math.min(1.0, Math.max(0.0, weightFrac));

            repaint();
        }

        void reset() {
            angle = 0;
            weightFrac = 0;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D)g.create();

            int topY = 20, botY = h - 20;

            // Draw housing
            Polygon house = new Polygon(
                new int[]{w/2-60, w/2+60, w/2+30, w/2-30},
                new int[]{botY, botY, topY, topY}, 4);
            g2.setColor(Color.BLACK);
            g2.fill(house);
            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(2));
            g2.draw(house);

            // Draw scale
            int scaleX = w/2, scaleTop = topY+20, scaleBot = botY-20;
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1));
            for (int b = MIN_BPM; b <= MAX_BPM; b += 10) {
                double frac = (b - MIN_BPM) / (double)(MAX_BPM - MIN_BPM);
                int y = scaleBot - (int)((scaleBot-scaleTop)*frac);
                g2.drawLine(scaleX-5, y, scaleX+5, y);
            }

            // Draw pivot
            int cx = w/2, cy = botY;
            g2.setColor(Color.LIGHT_GRAY);
            g2.fillOval(cx-6, cy-6, 12, 12);

            // Draw pendulum
            AffineTransform old = g2.getTransform();
            g2.translate(cx, cy);
            g2.rotate(angle);

            // Rod
            int rodLen = botY - topY - 20;
            g2.setColor(Color.GRAY);
            g2.setStroke(new BasicStroke(4));
            g2.drawLine(0, 0, 0, -rodLen);

            // Weight
            int weightW = 20, weightH = 12;
            int minPos = -20, maxPos = -rodLen + 20;
            int wy = minPos + (int)((maxPos-minPos)*weightFrac) - weightH/2;
            g2.setColor(Color.RED);
            g2.fillRect(-weightW/2, wy, weightW, weightH);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRect(-weightW/2, wy, weightW, weightH);

            g2.setTransform(old);
            g2.dispose();
        }
    }
}