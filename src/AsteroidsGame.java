/* Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved. */
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

final class AsteroidsGame {
    static final int W=960,H=720;
    static final double SHIELD_SECONDS=10, SHIELD_RADIUS=23;
    double shieldRemaining;
    boolean shieldActive;
    private static final int[] ROCK_POINTS={20,40,70,100,150};
    static final double ENEMY_SPEED=440,ENEMY_LIFE=2.1;
    final List<Rock> rocks=new ArrayList<>();
    final List<Shot> shots=new ArrayList<>();
    final List<Saucer> saucers=new ArrayList<>();
    final List<SaturnExplosion> bursts=new ArrayList<>();
    final List<Popup> popups=new ArrayList<>();
    final Random random;
    private final Random particles=new Random();
    private final Consumer<SoundEffects.Effect> audio;
    final double[] nextSaucer=new double[2];
    double x,y,vx,vy,angle,safe,cooldown,waveDelay,beat;
    double thrustSound;
    int wave,score,lives;
    boolean gameOver,thrust,alternateBeat;

    AsteroidsGame(Random random,Consumer<SoundEffects.Effect> audio) {
        this.random=random; this.audio=audio;
    }

    void start() {
        shieldRemaining=SHIELD_SECONDS; bursts.clear(); popups.clear(); alternateBeat=false; wave=0; score=0; lives=3; gameOver=false; beat=0.4; thrustSound=0;
        newWave();
    }

    private void resetShip() {
        x=W/2.0;y=H/2.0;vx=0;vy=0;angle=-Math.PI/2;
        safe=2.5; cooldown=0; thrust=false; shieldActive=false;
    }

    private void newWave() {
        wave++; rocks.clear(); shots.clear(); saucers.clear(); waveDelay=1.7;
        resetShip();
        nextSaucer[0]=8+random.nextDouble()*5;
        nextSaucer[1]=16+random.nextDouble()*6;
        double phase=random.nextDouble()*Math.PI*2;
        for(int i=0;i<6;i++) {
            double a=phase+(i+random.nextDouble()*0.6)*Math.PI/3;
            double spread=0.73+random.nextDouble()*0.24;
            rocks.add(Rock.large(random,W/2.0+Math.cos(a)*W*0.5*spread,
                    H/2.0+Math.sin(a)*H*0.5*spread,Math.min(2.1,Math.pow(1.05,wave-1))));
        }
        if(wave>1) sound(SoundEffects.Effect.LEVEL_UP);
    }

    void update(double dt,int turn,boolean accelerate,boolean fire) {
        update(dt,turn,accelerate,fire,false);
    }

    void update(double dt,int turn,boolean accelerate,boolean fire,boolean shieldHeld) {
        for(Iterator<SaturnExplosion> it=bursts.iterator();it.hasNext();) {
            SaturnExplosion burst=it.next(); burst.update(dt);
            if(burst.getCount()==0) it.remove();
        }
        for(Iterator<Popup> it=popups.iterator();it.hasNext();) {
            if((it.next().life-=dt)<=0) it.remove();
        }
        if(gameOver) return;
        shieldActive=shieldHeld&&shieldRemaining>0;
        if(shieldActive) {
            shieldRemaining=Math.max(0,shieldRemaining-dt);
            if(shieldRemaining<1e-9) shieldRemaining=0;
        }
        safe-=dt; cooldown-=dt;
        angle=wrap(angle+turn*4.8*dt,2*Math.PI);
        thrust=accelerate;
        if(thrust) {
            vx+=Math.cos(angle)*285*dt; vy+=Math.sin(angle)*285*dt;
            double speed=Math.hypot(vx,vy);
            if(speed>330) {vx*=330/speed;vy*=330/speed;}
            if((thrustSound-=dt)<=0){sound(SoundEffects.Effect.THRUST);thrustSound=0.12;}
        } else thrustSound=0;
        double oldX=x,oldY=y;
        x=wrap(x+vx*dt,W);y=wrap(y+vy*dt,H);
        if(fire&&cooldown<=0) {
            double c=Math.cos(angle),s=Math.sin(angle);
            shots.add(new Shot(x+c*16,y+s*16,vx+c*490,vy+s*490,0.95,true));
            cooldown=0.16; sound(SoundEffects.Effect.SHOT);
        }

        for(Rock rock:rocks) {
            double rx=Math.cos(rock.heading)*rock.speed,ry=Math.sin(rock.heading)*rock.speed;
            double beforeX=near(rock.x-oldX,W),beforeY=near(rock.y-oldY,H);
            rock.x=wrap(rock.x+rx*dt,W);
            rock.y=wrap(rock.y+ry*dt,H);
            rock.angle=wrap(rock.angle+rock.spin*dt,Math.PI*2);
            if(shieldActive) bounceRock(rock,beforeX,beforeY,rx,ry,dt);
        }
        if(!rocks.isEmpty()) updateSaucers(dt);
        else saucers.clear();

        for(Iterator<Shot> it=shots.iterator();it.hasNext();) {
            Shot s=it.next();
            double step=Math.min(dt,s.life),dx=s.vx*step,dy=s.vy*step;
            s.life-=dt;
            if(s.player ? playerShot(s,dx,dy) : (shieldActive||safe<=0)&&hits(s,dx,dy,x,y,shieldActive?SHIELD_RADIUS:10)) {
                it.remove();
                if(!s.player) {
                    if(shieldActive) sound(SoundEffects.Effect.SHIELD_HIT);
                    else {loseShip(); if(gameOver) break;}
                }
            } else if(s.life<=0) it.remove();
            else {s.x=wrap(s.x+dx,W);s.y=wrap(s.y+dy,H);}
        }
        if(gameOver) {shots.clear(); return;}
        if(!shieldActive&&safe<=0) {
            for(Rock r:rocks) {
                double dx=near(x-r.x,W),dy=near(y-r.y,H);
                if(r.touches(dx,dy,dx,dy,9)){loseShip();break;}
            }
            if(safe<=0) for(Saucer u:saucers) {
                if(Math.hypot(near(x-u.x,W),near(y-u.y,H))<u.radius()+9){loseShip();break;}
            }
        }
        if(rocks.isEmpty()&&!gameOver&& (waveDelay-=dt)<=0) newWave();
        if(!gameOver&&(beat-=dt)<=0) {
            sound(alternateBeat?SoundEffects.Effect.BEAT_HIGH:SoundEffects.Effect.MARCH);
            alternateBeat=!alternateBeat;
            double remaining=0;
            for(Rock rock:rocks) remaining+=Math.scalb(1.0,-rock.depth);
            beat=0.29+0.47*Math.min(1,remaining/6);
        }
    }

    private void bounceRock(Rock rock,double beforeX,double beforeY,double rx,double ry,double dt) {
        double relativeX=rx-vx,relativeY=ry-vy;
        double dx=beforeX+relativeX*dt,dy=beforeY+relativeY*dt;
        if(!rock.touches(-beforeX,-beforeY,-dx,-dy,SHIELD_RADIUS)) return;

        // Use the side the rock arrived from if it crossed the shield center.
        double nx=dx,ny=dy;
        if(dx*beforeX+dy*beforeY<=0) {nx=beforeX;ny=beforeY;}
        double length=Math.hypot(nx,ny);
        if(length<1e-9) {
            nx=-relativeX;ny=-relativeY;length=Math.hypot(nx,ny);
            if(length<1e-9) {nx=1;ny=0;length=1;}
        }
        nx/=length;ny/=length;
        double approach=relativeX*nx+relativeY*ny;
        if(approach<0) {
            rx-=2*approach*nx;ry-=2*approach*ny;
            rock.speed=Math.hypot(rx,ry);
            rock.heading=Math.atan2(ry,rx);
            sound(SoundEffects.Effect.SHIELD_HIT);
        }

        // Clear the actual outline so the next tick does not reflect it again.
        double low=Math.max(0,dx*nx+dy*ny),high=rock.radius+SHIELD_RADIUS+1;
        if(low<high&&rock.touches(-nx*low,-ny*low,-nx*low,-ny*low,SHIELD_RADIUS)) {
            for(int i=0;i<12;i++) {
                double mid=(low+high)*0.5;
                if(rock.touches(-nx*mid,-ny*mid,-nx*mid,-ny*mid,SHIELD_RADIUS)) low=mid;
                else high=mid;
            }
            low=high+0.25;
        }
        rock.x=wrap(x+nx*low,W);
        rock.y=wrap(y+ny*low,H);
    }

    private boolean playerShot(Shot s,double dx,double dy) {
        for(int i=0;i<rocks.size();i++) {
            Rock r=rocks.get(i);
            double rx=near(s.x-r.x,W),ry=near(s.y-r.y,H);
            if(r.touches(rx,ry,rx+dx,ry+dy,0)) {
                rocks.remove(i);
                for(Rock piece:r.split(random)) {
                    piece.x=wrap(piece.x,W);piece.y=wrap(piece.y,H);rocks.add(piece);
                }
                score+=ROCK_POINTS[r.depth];
                explode(r.x,r.y);
                sound(r.depth>=3?SoundEffects.Effect.ROCK_CHIP:SoundEffects.Effect.EXPLOSION);
                return true;
            }
        }
        for(int i=0;i<saucers.size();i++) {
            Saucer u=saucers.get(i);
            if(hits(s,dx,dy,u.x,u.y,u.radius())) {
                saucers.remove(i); nextSaucer[u.small?1:0]=8+random.nextDouble()*9;
                score+=u.reward; popups.add(new Popup(u.x,u.y,u.reward));
                explode(u.x,u.y); sound(SoundEffects.Effect.BONUS_HIT);
                return true;
            }
        }
        return false;
    }

    private void updateSaucers(double dt) {
        for(int type=0;type<2;type++) {
            if(Double.isFinite(nextSaucer[type])&&(nextSaucer[type]-=dt)<=0) {
                boolean left=random.nextBoolean();
                double ux=left?0:W-0.01,uy=random.nextDouble()*H;
                double heading=(left?0:Math.PI)+(random.nextDouble()-0.5)*Math.PI/3;
                saucers.add(new Saucer(type==1,ux,uy,heading,random));
                nextSaucer[type]=Double.POSITIVE_INFINITY;
                sound(type==1?SoundEffects.Effect.SAUCER_SMALL:SoundEffects.Effect.SAUCER_LARGE);
            }
        }
        for(Iterator<Saucer> it=saucers.iterator();it.hasNext();) {
            Saucer u=it.next();
            u.advance(dt,random);
            if((u.life-=dt)<=0) {
                it.remove(); nextSaucer[u.small?1:0]=7+random.nextDouble()*8; continue;
            }
            u.fire-=dt;u.call-=dt;
            if(u.call<=0) {
                sound(u.small?SoundEffects.Effect.SAUCER_SMALL:SoundEffects.Effect.SAUCER_LARGE);
                u.call=u.small?0.86:1.2;
            }
            if(u.fire<=0) {
                u.fire=(u.small?1.05:1.72)+random.nextDouble()*0.6;
                double[] v=lead(u.x,u.y,x,y,vx,vy);
                shots.add(new Shot(u.x,u.y,v[0],v[1],ENEMY_LIFE,false));
                sound(SoundEffects.Effect.ENEMY_SHOT);
            }
        }
    }

    static double[] lead(double ux,double uy,double x,double y,double vx,double vy) {
        double rx=near(x-ux,W),ry=near(y-uy,H),tx=rx,ty=ry,best=Double.POSITIVE_INFINITY;
        // A wrapped ship has periodic images. Pick the first reachable one during bullet life.
        int nx=(int)Math.ceil((ENEMY_SPEED+Math.abs(vx))*ENEMY_LIFE/W+0.5);
        int ny=(int)Math.ceil((ENEMY_SPEED+Math.abs(vy))*ENEMY_LIFE/H+0.5);
        for(int i=-nx;i<=nx;i++) for(int j=-ny;j<=ny;j++) {
            double ax=rx+i*W,ay=ry+j*H;
            double t=InterceptAim.time(ax,ay,vx,vy,ENEMY_SPEED);
            if(t>=0&&t<=ENEMY_LIFE&&t<best) {
                best=t;tx=ax+vx*t;ty=ay+vy*t;
            }
        }
        return InterceptAim.velocity(0,0,tx,ty,0,0,ENEMY_SPEED);
    }

    private static boolean hits(Shot s,double dx,double dy,double x,double y,double radius) {
        return Rock.distance(0,0,near(s.x-x,W),near(s.y-y,H),
                near(s.x-x,W)+dx,near(s.y-y,H)+dy)<=radius;
    }

    private void loseShip() {
        explode(x,y);sound(SoundEffects.Effect.PLAYER_HIT);
        lives--;
        if(lives==0){gameOver=true;thrust=false;sound(SoundEffects.Effect.GAME_OVER);}
        else {shieldRemaining=SHIELD_SECONDS; resetShip();}
    }

    private void explode(double x,double y) {
        if(bursts.size()>=32) bursts.remove(0);
        bursts.add(new SaturnExplosion(x,y,particles));
    }

    static double wrap(double x,double size){return x-Math.floor(x/size)*size;}
    static double near(double x,double size){return x-Math.floor(x/size+0.5)*size;}
    private void sound(SoundEffects.Effect effect){audio.accept(effect);}

    static final class Shot {
        double x,y,life;
        final double vx,vy;
        final boolean player;
        Shot(double x,double y,double vx,double vy,double life,boolean player) {
            this.x=wrap(x,W);this.y=wrap(y,H);this.vx=vx;this.vy=vy;this.life=life;this.player=player;
        }
    }

    static final class Saucer {
        double x,y,heading,turn,fire,call,life;
        final double speed;
        final boolean small;
        final int reward;
        Saucer(boolean small,double x,double y,double heading,Random r) {
            this.small=small;this.x=x;this.y=y;this.heading=heading;
            speed=small?150:99;
            reward=small?600+25*r.nextInt(25):150+25*r.nextInt(15);
            life=9+r.nextDouble()*4;fire=0.85+r.nextDouble()*0.5;
            turn=1+r.nextDouble()*1.2;call=1;
        }

        double radius(){return small?14:23;}
        void advance(double dt,Random r) {
            if((turn-=dt)<=0) {
                heading=wrap(heading+(r.nextDouble()-0.5)*Math.PI/2,Math.PI*2);
                turn=0.65+r.nextDouble()*1.65;
            }
            x=wrap(x+Math.cos(heading)*speed*dt,W);
            y=wrap(y+Math.sin(heading)*speed*dt,H);
        }
    }

    static final class Popup {
        final double x,y;
        final int value;
        double life=1.15;
        Popup(double x,double y,int value){this.x=x;this.y=y;this.value=value;}
    }
}
