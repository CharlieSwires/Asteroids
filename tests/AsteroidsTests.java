import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import javax.swing.SwingUtilities;

public final class AsteroidsTests {
    public static void main(String[] args) throws Exception {
        Random random=new Random(8729);
        AsteroidsGame game=new AsteroidsGame(random,e->{});
        game.start();
        require(game.rocks.size()==6&&game.lives==3,"initial state");
        Set<String> shapes=new HashSet<>();
        for(Rock rock:game.rocks) {
            require(rock.points.size()>=24&&rock.points.size()%4==0,"four segments per base edge");
            StringBuilder signature=new StringBuilder();
            for(Rock.Point p:rock.points) signature.append(p.x).append(',');
            require(shapes.add(signature.toString()),"repeated rock shape");
        }
        for(int i=0;i<250;i++) {
            require(splitTree(Rock.large(random,50,100,1),random)==16,"five generations per rock");
        }

        AsteroidsGame.Saucer large=new AsteroidsGame.Saucer(false,300,600,0,random);
        AsteroidsGame.Saucer small=new AsteroidsGame.Saucer(true,300,600,0,random);
        for(int i=0;i<400;i++) {
            require(small.reward>large.reward,"saucer scoring");
            double oldHeading=small.heading,oldX=small.x,oldY=small.y;
            small.turn=0;small.advance(1.0/120,random);
            require(Math.abs(AsteroidsGame.near(small.heading-oldHeading,Math.PI*2))<=Math.PI/4+1e-10,"saucer turn");
            close(Math.hypot(AsteroidsGame.near(small.x-oldX,AsteroidsGame.W),
                    AsteroidsGame.near(small.y-oldY,AsteroidsGame.H))*120,small.speed,1e-8,"saucer speed");
            large=new AsteroidsGame.Saucer(false,300,600,0,random);
            small=new AsteroidsGame.Saucer(true,300,600,0,random);
        }

        intercept(100,100,270,100,0,200);
        intercept(920,350,45,350,0,125);
        for(int i=0;i<1500;i++) {
            double a=random.nextDouble()*2*Math.PI;
            intercept(random.nextDouble()*960,random.nextDouble()*720,random.nextDouble()*960,
                    random.nextDouble()*720,Math.cos(a)*330,Math.sin(a)*330);
        }

        // A shot should split the actual rock, then the next wave should start with six new rocks.
        Rock rock=game.rocks.get(0);
        game.shots.add(new AsteroidsGame.Shot(rock.x,rock.y,80,0,1,true));
        game.update(1.0/120,0,false,false);
        require(game.rocks.size()==7&&game.score==20,"actual projectile split");
        game.rocks.clear();
        for(int i=0;i<240;i++) game.update(1.0/120,0,false,false);
        require(game.wave==2&&game.rocks.size()==6,"next wave");

        // Exercise a real saucer firing, then keep advancing the game through a loss.
        game.saucers.add(small);small.fire=0;
        game.update(1.0/120,0,false,false);
        require(game.shots.stream().anyMatch(s->!s.player),"saucer fired");
        game.lives=1;game.safe=0;
        game.shots.add(new AsteroidsGame.Shot(game.x,game.y,0,30,1,false));
        game.update(1.0/120,0,false,false);
        require(game.gameOver&&game.lives==0,"player loss");
        game.start();
        require(!game.gameOver&&game.wave==1&&game.score==0,"restart");

        testShield();
        testRockBounce(false);
        testRockBounce(true);
        for(SoundEffects.Effect e:SoundEffects.Effect.values()) {
            boolean audible=false;
            for(short sample:SoundEffects.synthesize(e)) if(sample!=0) audible=true;
            require(audible,"empty sound "+e);
        }

        SwingUtilities.invokeAndWait(()->{
            Asteroids.VectorPanel panel=new Asteroids.VectorPanel();
            try {
                panel.setSize(960,720);
                draw(panel);
                press(panel,"scores");draw(panel);
                press(panel,"enter");draw(panel);
                require(panel.game.wave==1,"keyboard start from scores");
                press(panel,"enter-up");press(panel,"pause");draw(panel);
                press(panel,"back");draw(panel);
                press(panel,"enter");
                require(panel.game.rocks.size()==6,"keyboard restart");
            } finally {panel.shutdown();}
        });
        System.out.println("PASS: split geometry, opposite spin, speed growth, saucer scoring/flight, wrapped intercepts, waves, restart, Swing rendering and input, sound samples");
    }

    private static void testRockBounce(boolean wraps) {
        AsteroidsGame game=new AsteroidsGame(new Random(22),e->{});
        game.start();
        game.rocks.clear();
        game.x=wraps?945:300;
        game.y=300;
        game.vx=wraps?40:0;
        Rock rock=Rock.large(game.random,AsteroidsGame.wrap(game.x+90,AsteroidsGame.W),game.y,1);
        rock.heading=Math.PI;
        double speed=rock.speed;
        game.rocks.add(rock);
        int steps=0;
        while(Math.cos(rock.heading)<0&&steps++<400) game.update(1.0/120,0,false,false,true);
        require(Math.cos(rock.heading)>0.99&&game.lives==3,"rock ricochets off shield");
        close(rock.speed,speed+2*game.vx,1e-7,"bounce relative to moving shield");
        double dx=AsteroidsGame.near(game.x-rock.x,AsteroidsGame.W),dy=game.y-rock.y;
        require(!rock.touches(dx,dy,dx,dy,AsteroidsGame.SHIELD_RADIUS),"rock separated after bounce");
        game.update(1.0/120,0,false,false,true);
        require(Math.cos(rock.heading)>0.99,"rock stays on its outgoing heading");
    }

    private static void testShield() {
        AsteroidsGame game=new AsteroidsGame(new Random(24),e->{});
        double dt=1.0/120;
        game.start();
        game.safe=0;
        game.shots.add(new AsteroidsGame.Shot(game.x+20,game.y,0,0,1,false));
        game.update(dt,1,true,true,true);
        require(game.shieldActive&&game.lives==3,"shield absorbs a shot at the ring");
        require(game.shots.size()==1&&game.shots.get(0).player,"can thrust and fire with shield up");
        for(int i=1;i<480;i++) game.update(dt,0,false,false,true);
        close(game.shieldRemaining,6,1e-8,"four seconds spent");
        game.update(dt,0,false,false,false);
        require(!game.shieldActive,"release lowers shield");
        close(game.shieldRemaining,6,1e-8,"release saves charge");
        game.rocks.clear();
        game.waveDelay=0;
        game.update(dt,0,false,false,false);
        close(game.shieldRemaining,6,1e-8,"wave change does not refill");
        game.nextSaucer[0]=game.nextSaucer[1]=Double.POSITIVE_INFINITY;
        for(int i=0;i<720;i++) game.update(dt,0,false,false,true);
        close(game.shieldRemaining,0,1e-8,"ten total seconds");
        game.x=AsteroidsGame.W/2.0;game.y=AsteroidsGame.H/2.0;
        game.shots.add(new AsteroidsGame.Shot(game.x,game.y,0,0,1,false));
        game.update(dt,0,false,false,true);
        require(!game.shieldActive&&game.lives==2,"empty shield cannot block a shot");
        close(game.shieldRemaining,10,1e-8,"new ship refills shield");
    }

    private static void press(Asteroids.VectorPanel panel,String action) {
        panel.getActionMap().get(action).actionPerformed(new ActionEvent(panel,0,action));
    }

    private static void draw(Asteroids.VectorPanel panel) {
        BufferedImage image=new BufferedImage(960,720,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try {panel.paint(g);} finally {g.dispose();}
    }

    private static int splitTree(Rock parent,Random random) {
        Rock[] pieces=parent.split(random);
        if(parent.depth==4) {
            require(pieces.length==0,"terminal fragment must disappear");
            return 1;
        }
        require(pieces.length==2,"expected two fragments");
        require(pieces[0].spin*pieces[1].spin<0,"opposite spin");
        close(Math.abs(AsteroidsGame.near(pieces[0].heading-pieces[1].heading,Math.PI*2)),
                Math.PI,1e-10,"opposite heading");
        double parentArea=Rock.area(parent.points);
        close(Rock.area(pieces[0].points)+Rock.area(pieces[1].points),parentArea,1e-7,"split conserves area");
        int shared=0;
        for(Rock.Point a:pieces[0].points) for(Rock.Point b:pieces[1].points) {
            double dx=a.x-b.x,dy=a.y-b.y;
            if(Math.hypot(pieces[0].x-pieces[1].x+Math.cos(parent.angle)*dx-Math.sin(parent.angle)*dy,
                    pieces[0].y-pieces[1].y+Math.sin(parent.angle)*dx+Math.cos(parent.angle)*dy)<1e-7) {
                shared++;
                break;
            }
        }
        require(shared>=6,"matching jagged seam");
        int leaves=0;
        for(Rock child:pieces) {
            require(Rock.area(child.points)>0,"valid fragment area");
            double speedRatio=child.speed/parent.speed;
            require(speedRatio>=1.85&&speedRatio<=2.15,"fragment speed");
            double ratio=Math.abs(child.spin/parent.spin);
            require(ratio>=1.75&&ratio<=2.25,"fragment spin speed");
            leaves+=splitTree(child,random);
        }
        return leaves;
    }

    private static void intercept(double gx,double gy,double sx,double sy,double vx,double vy) {
        double[] b=AsteroidsGame.lead(gx,gy,sx,sy,vx,vy);
        close(Math.hypot(b[0],b[1]),AsteroidsGame.ENEMY_SPEED,1e-9,"bullet speed");
        double best=Double.POSITIVE_INFINITY;
        for(int i=-5;i<=5;i++) for(int j=-5;j<=5;j++) {
            double rx=sx-gx+i*960,ry=sy-gy+j*720;
            double dx=b[0]-vx,dy=b[1]-vy;
            double t=(rx*dx+ry*dy)/(dx*dx+dy*dy);
            if(t>=0&&t<=AsteroidsGame.ENEMY_LIFE)
                best=Math.min(best,Math.hypot(rx-dx*t,ry-dy*t));
        }
        close(best,0,1e-7,"predicted shot arrives at moving ship");
    }

    private static void close(double a,double b,double tolerance,String msg) {
        require(Math.abs(a-b)<=tolerance,msg+": "+a+" vs "+b);
    }
    private static void require(boolean okay,String message) {
        if(!okay) throw new AssertionError(message);
    }
}
