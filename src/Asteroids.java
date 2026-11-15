/* Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved. */
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Random;
import javax.swing.*;

public final class Asteroids {
    private Asteroids() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(()->{
            JFrame window=new JFrame("Asteroids");
            VectorPanel panel=new VectorPanel();
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.setContentPane(panel);
            window.pack(); window.setLocationRelativeTo(null);
            window.addWindowFocusListener(new WindowAdapter() {
                @Override public void windowLostFocus(WindowEvent e){panel.clearKeys();}
            });
            window.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e){panel.shutdown();}
            });
            window.setVisible(true);
            panel.requestFocusInWindow();
        });
    }

    static final class VectorPanel extends JPanel {
        private static final long serialVersionUID=1L;
        private static final int W=AsteroidsGame.W,H=AsteroidsGame.H;
        private static final Color ICE=new Color(202,241,255), ROCK=new Color(228,236,239),
                RED=new Color(255,114,114), GOLD=new Color(255,211,115), DIM=new Color(133,158,168);
        private static final double STEP=1.0/120;
        private enum Screen { TITLE, PLAY, OVER, SCORES }
        private Screen screen=Screen.TITLE;
        private final SoundEffects sound=new SoundEffects();
        final AsteroidsGame game=new AsteroidsGame(new Random(),sound::play);
        private final HighScores scores=new HighScores(Paths.get(System.getProperty("user.home"),".asteroids-scores.bin"));
        private final Timer timer;
        private long last=System.nanoTime();
        private double timeOwed,overTime;
        private boolean shieldHeld;
        private final KeyEventDispatcher shieldKeys=this::dispatchShieldKey;
        private boolean left,right,thrust,fire,paused,muted,enterDown,scorePrompted,scoreLoadFailed;
        private String scoreMessage="";

        VectorPanel() {
            setPreferredSize(new Dimension(960,720));
            setBackground(Color.BLACK);
            setFocusable(true);
            try {scores.load();} catch(IOException ex){scoreMessage="Could not read saved scores.";scoreLoadFailed=true;}
            bindPair(KeyEvent.VK_LEFT, "left", ()->left=true, ()->left=false);
            bindPair(KeyEvent.VK_RIGHT, "right", ()->right=true, ()->right=false);
            bindPair(KeyEvent.VK_UP, "thrust", ()->thrust=true, ()->thrust=false);
            bindPair(KeyEvent.VK_SPACE, "fire", ()->fire=true, ()->fire=false);
            bindPair(KeyEvent.VK_ENTER, "enter", ()->{if(!enterDown){enterDown=true;enter();}}, ()->enterDown=false);
            bind(KeyEvent.VK_H,true,"scores",()->{if(screen!=Screen.PLAY) {clearKeys();screen=Screen.SCORES;}});
            bind(KeyEvent.VK_M,true,"mute",()->{muted=!muted;sound.toggleMute();});
            bind(KeyEvent.VK_P,true,"pause",()->{if(screen==Screen.PLAY){paused=!paused;clearKeys();}});
            bind(KeyEvent.VK_ESCAPE,true,"back",()->{clearKeys();paused=false;screen=Screen.TITLE;});
            timer=new Timer(1000/60,e->tick());
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(shieldKeys);
            timer.start();
        }

        void shutdown(){
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(shieldKeys);
            timer.stop();sound.close();
        }
        private boolean dispatchShieldKey(KeyEvent e) {
            Window window=SwingUtilities.getWindowAncestor(this);
            if(window==null || !window.isFocused() || e.getKeyCode()!=KeyEvent.VK_CONTROL
                    || e.getKeyLocation()!=KeyEvent.KEY_LOCATION_RIGHT) return false;
            if(e.getID()==KeyEvent.KEY_PRESSED) shieldHeld=screen==Screen.PLAY&&!paused;
            else if(e.getID()==KeyEvent.KEY_RELEASED) shieldHeld=false;
            return true;
        }
        void clearKeys(){left=right=thrust=fire=enterDown=shieldHeld=false;}
        private void bindPair(int key,String name,Runnable pressed,Runnable released) {
            bind(key,false,name,pressed);bind(key,true,name+"-up",released);
        }
        private void bind(int key,boolean release,String name,Runnable action) {
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key,0,release),name);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key,InputEvent.CTRL_DOWN_MASK,release),name);
            getActionMap().put(name,new AbstractAction() {
                private static final long serialVersionUID=1L;
                @Override public void actionPerformed(ActionEvent e){action.run();}
            });
        }

        private void enter() {
            if(screen==Screen.OVER) {showScores();return;}
            if(screen==Screen.PLAY) return;
            left=right=thrust=fire=false; paused=false; scorePrompted=false;
            game.start(); screen=Screen.PLAY;
        }

        private void tick() {
            long now=System.nanoTime();
            double elapsed=Math.min(0.05,(now-last)/1e9);
            last=now;
            if((screen==Screen.PLAY&&!paused) || screen==Screen.OVER) {
                timeOwed+=elapsed;
                while(timeOwed>=STEP) {
                    timeOwed-=STEP;
                    game.update(STEP,(right?1:0)-(left?1:0),thrust,fire,shieldHeld);
                    if(game.gameOver&&screen==Screen.PLAY) {
                        screen=Screen.OVER;overTime=0;clearKeys();
                    }
                }
                if(screen==Screen.OVER&&(overTime+=elapsed)>1.35) showScores();
            } else timeOwed=0;
            repaint();
        }

        private void showScores() {
            screen=Screen.SCORES;clearKeys();
            if(!scorePrompted&&game.score>0&&scores.qualifies(game.score)) {
                scorePrompted=true;
                timer.stop();
                try {
                    String name=JOptionPane.showInputDialog(this,"New high score: "+game.score+"\nEnter your name (up to 20 characters):","PLAYER");
                    if(name!=null&&scores.add(game.score,name)&&!scoreLoadFailed) {
                        try {scores.save();scoreMessage="";} catch(IOException ex){scoreMessage="Could not save your score.";}
                    }
                } finally {
                    clearKeys();requestFocusInWindow();last=System.nanoTime();timeOwed=0;timer.start();
                }
            }
        }

        @Override protected void paintComponent(Graphics raw) {
            super.paintComponent(raw);
            Graphics2D g=(Graphics2D)raw.create();
            double scale=Math.min(getWidth()/(double)W,getHeight()/(double)H);
            if(scale<=0){g.dispose();return;}
            g.translate((getWidth()-W*scale)/2,(getHeight()-H*scale)/2);
            g.scale(scale,scale);g.clipRect(0,0,W,H);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.65f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            if(screen==Screen.TITLE) title(g);
            else if(screen==Screen.SCORES) scoreTable(g);
            else {
                scene(g);
                if(paused) banner(g,"PAUSED","Press P to resume");
                else if(screen==Screen.OVER) banner(g,"GAME OVER",String.format("%,d POINTS",game.score));
                else if(game.rocks.isEmpty()) banner(g,"WAVE CLEAR","Next wave incoming");
            }
            String audio=muted?"MUTED":sound.status().equals("READY")?"SOUND ON":"SOUND "+sound.status();
            label(g,audio+"  [M]",12,18,H-17,DIM);
            g.dispose();
        }

        private void scene(Graphics2D g) {
            for(SaturnExplosion p:game.bursts) p.draw(g);
            g.setColor(ROCK);
            for(Rock rock:game.rocks) {
                Path2D path=new Path2D.Double();
                boolean first=true;
                for(Rock.Point p:rock.points) {
                    if(first)path.moveTo(p.x,p.y); else path.lineTo(p.x,p.y);
                    first=false;
                }
                path.closePath();
                wrapped(g,rock.x,rock.y,rock.radius+2,rock.angle,h->h.draw(path));
            }
            for(AsteroidsGame.Saucer u:game.saucers) {
                g.setColor(u.small?GOLD:RED);
                wrapped(g,u.x,u.y,u.radius()+2,0,h->saucer(h,u.radius()));
            }
            for(AsteroidsGame.Shot s:game.shots) {
                g.setColor(s.player?ICE:RED);
                double length=Math.hypot(s.vx,s.vy);
                wrapped(g,s.x,s.y,8,0,h->h.draw(new Line2D.Double(0,0,-s.vx*6/length,-s.vy*6/length)));
            }
            if(!game.gameOver&&(game.shieldActive||game.safe<=0||paused||((int)(game.safe*9)&1)==0)) {
                g.setColor(ICE);
                wrapped(g,game.x,game.y,24,game.angle,h->ship(h,game.thrust));
                if(game.shieldActive) {
                    double r=AsteroidsGame.SHIELD_RADIUS;
                    g.setColor(new Color(100,225,255));
                    wrapped(g,game.x,game.y,r+2,0,h->h.draw(new Ellipse2D.Double(-r,-r,2*r,2*r)));
                }
            }
            for(AsteroidsGame.Popup popup:game.popups) {
                label(g,"+"+popup.value,15,(int)Math.max(5,Math.min(W-65,popup.x+8)),
                        (int)Math.max(22,popup.y-18-(1.15-popup.life)*32),GOLD);
            }
            label(g,String.format("SCORE  %,d",game.score),20,24,36,ICE);
            centered(g,"WAVE "+game.wave,18,36,ICE);
            String best=String.format("BEST  %,d",Math.max(scores.best(),game.score));
            g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,18));
            label(g,best,18,W-24-g.getFontMetrics().stringWidth(best),36,ICE);
            String charge=String.format(java.util.Locale.ROOT,"SHIELD  %.1fs  [RIGHT CTRL]",
                    Math.ceil(game.shieldRemaining*10)/10);
            g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,14));
            label(g,charge,14,W-24-g.getFontMetrics().stringWidth(charge),61,game.shieldActive?ICE:DIM);
            g.setColor(ICE);
            for(int i=0;i<game.lives;i++) {
                Graphics2D icon=(Graphics2D)g.create();
                icon.translate(30+i*24,60);icon.rotate(-Math.PI/2);icon.scale(0.55,0.55);
                ship(icon,false);icon.dispose();
            }
        }

        private static void wrapped(Graphics2D g,double x,double y,double extent,double angle,
                                    java.util.function.Consumer<Graphics2D> draw) {
            int firstX=x>W-extent?-1:0,lastX=x<extent?1:0;
            int firstY=y>H-extent?-1:0,lastY=y<extent?1:0;
            for(int i=firstX;i<=lastX;i++) for(int j=firstY;j<=lastY;j++) {
                Graphics2D h=(Graphics2D)g.create();
                h.translate(x+i*W,y+j*H);h.rotate(angle);
                draw.accept(h);h.dispose();
            }
        }

        private static void ship(Graphics2D g,boolean flame) {
            path(g,16,0,-11,-9,-7,0,-11,9,16,0);
            if(flame) {
                g.setColor(GOLD);
                path(g,-9,-4,-20,0,-9,4);
            }
        }

        private static void saucer(Graphics2D g,double r) {
            path(g,-r,0,-0.61*r,-0.3*r,0.61*r,-0.3*r,r,0,0.55*r,0.31*r,-0.55*r,0.31*r,-r,0,r,0);
            path(g,-0.43*r,-0.3*r,-0.24*r,-0.63*r,0.24*r,-0.63*r,0.43*r,-0.3*r);
        }

        private static void path(Graphics2D g,double... xy) {
            Path2D p=new Path2D.Double();
            p.moveTo(xy[0],xy[1]);
            for(int i=2;i<xy.length;i+=2) p.lineTo(xy[i],xy[i+1]);
            g.draw(p);
        }

        private void title(Graphics2D g) {
            centered(g,"V E C T O R   A R C A D E",17,152,DIM);
            centered(g,"ASTEROIDS",75,254,ICE);
            g.setColor(ICE);
            Graphics2D icon=(Graphics2D)g.create();
            icon.translate(W/2.0,314);icon.rotate(-Math.PI/2);icon.scale(1.55,1.55);ship(icon,true);icon.dispose();
            centered(g,"ENTER  START GAME",24,409,GOLD);
            centered(g,"LEFT / RIGHT   TURN      UP   THRUST      SPACE   FIRE",16,472,ROCK);
            centered(g,"RIGHT CTRL   SHIELD",15,499,ICE);
            centered(g,"H  HIGH SCORES     P  PAUSE     M  SOUND     ESC  TITLE",15,528,DIM);
            centered(g,"Split the rocks. Watch for saucers that lead their shots.",15,564,DIM);
        }

        private void scoreTable(Graphics2D g) {
            centered(g,"HIGH SCORES",50,143,ICE);
            int y=216,n=1;
            for(HighScores.Entry entry:scores.entries()) {
                label(g,String.format("%2d.  %-20s  %9s",n++,entry.name,String.format("%,d",entry.score)),
                        19,278,y,ROCK);
                y+=32;
            }
            if(n==1) centered(g,"No scores yet.",19,244,DIM);
            centered(g,"ENTER  NEW GAME      ESC  TITLE",18,604,GOLD);
            if(!scoreMessage.isEmpty()) centered(g,scoreMessage,14,647,RED);
        }

        private static void banner(Graphics2D g,String title,String detail) {
            g.setColor(new Color(0,0,0,215));g.fillRoundRect(W/2-220,H/2-60,440,120,12,12);
            centered(g,title,39,H/2-1,ICE);
            centered(g,detail,16,H/2+31,GOLD);
        }

        private static void label(Graphics2D g,String text,int size,int x,int y,Color color) {
            g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,size));g.setColor(color);g.drawString(text,x,y);
        }

        private static void centered(Graphics2D g,String text,int size,int y,Color color) {
            g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,size));g.setColor(color);
            g.drawString(text,(W-g.getFontMetrics().stringWidth(text))/2,y);
        }
    }
}
