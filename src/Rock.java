/* Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved. */
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** A vector outline with a shared jagged seam between split fragments. */
final class Rock {
    static final int SPLITS = 4;
    final List<Point> points;
    final double radius, spin;
    double speed;
    final int depth;
    double x, y, angle, heading;

    static final class Point {
        final double x, y;
        Point(double x, double y) { this.x=x; this.y=y; }
    }

    private Rock(List<Point> points,double x,double y,double angle,double heading,
                 double speed,double spin,int depth) {
        this.points=points; this.x=x; this.y=y; this.angle=angle; this.heading=heading;
        this.speed=speed; this.spin=spin; this.depth=depth;
        double r=0;
        for(Point p:points) r=Math.max(r,Math.hypot(p.x,p.y));
        radius=r;
    }

    static Rock large(Random r,double x,double y,double factor) {
        List<Point> samples=new ArrayList<>();
        int count=9+r.nextInt(6);
        double size=48+r.nextDouble()*15;
        for(int i=0;i<count;i++) {
            double a=(i+0.7*r.nextDouble())*2*Math.PI/count;
            double length=size*(0.75+0.25*r.nextDouble());
            samples.add(new Point(Math.cos(a)*length,Math.sin(a)*length*0.84));
        }
        List<Point> outline=detail(hull(samples),r);
        Point center=centroid(outline);
        return new Rock(centered(outline,center),x,y,r.nextDouble()*Math.PI*2,
                r.nextDouble()*Math.PI*2,(19+r.nextDouble()*10)*factor,
                (0.16+r.nextDouble()*0.13)*(r.nextBoolean()?1:-1),0);
    }

    Rock[] split(Random r) {
        if(depth==SPLITS) return new Rock[0];
        List<Point> first=null,second=null,seam=null;
        double best=Double.POSITIVE_INFINITY, total=area(points);
        // Try several cut directions. Concave fragments can have multiple interior
        // chords; only use adjacent crossings and keep a reasonably balanced cut.
        for(int attempt=0;attempt<20;attempt++) {
            double a=r.nextDouble()*2*Math.PI,nx=Math.cos(a),ny=Math.sin(a);
            double offset=(r.nextDouble()-0.5)*radius*0.45;
            List<Crossing> cuts=new ArrayList<>();
            for(int i=0;i<points.size();i++) {
                Point p=points.get(i),q=points.get((i+1)%points.size());
                double dp=p.x*nx+p.y*ny-offset,dq=q.x*nx+q.y*ny-offset;
                if(dp*dq<0) {
                    Point hit=interpolate(p,q,dp/(dp-dq));
                    cuts.add(new Crossing(hit,i,-hit.x*ny+hit.y*nx));
                }
            }
            cuts.sort(Comparator.comparingDouble(c->c.order));
            for(int i=0;i+1<cuts.size();i+=2) {
                Crossing start=cuts.get(i),end=cuts.get(i+1);
                List<Point> left=arc(start,end), right=arc(end,start);
                double error=Math.abs(area(left)/total-0.5);
                if(error>=best || error>0.38) continue;
                List<Point> crack=crack(start,end,r);
                if(crack==null) continue;
                appendSeam(left,right,crack);
                error=Math.abs(area(left)/total-0.5);
                if(error<best && error<=0.38) {
                    best=error;first=left;second=right;seam=crack;
                }
            }
            if(best<0.075) break;
        }
        if(seam==null) {
            // A polygon always has an interior diagonal. This fallback also
            // handles narrow fragments where randomized chords are hard to find.
            for(int i=0;i<points.size()-2;i++) for(int j=i+2;j<points.size();j++) {
                if(i==0&&j==points.size()-1) continue;
                Crossing start=new Crossing(points.get(i),i,0),end=new Crossing(points.get(j),j,0);
                List<Point> left=new ArrayList<>(points.subList(i,j+1));
                List<Point> right=new ArrayList<>(points.subList(j,points.size()));
                right.addAll(points.subList(0,i+1));
                double error=Math.abs(area(left)/total-0.5);
                if(error>=best) continue;
                List<Point> candidate=crack(start,end,r);
                if(candidate==null) continue;
                appendSeam(left,right,candidate);
                error=Math.abs(area(left)/total-0.5);
                if(error<best) {best=error;first=left;second=right;seam=candidate;}
            }
        }
        if(seam==null) {
            // A triangle can be cut from the midpoint of one side to the
            // midpoint of another even though it has no internal diagonals.
            Point p=points.get(0),q=points.get(1),v=points.get(2);
            Crossing start=new Crossing(interpolate(p,q,0.5),0,0);
            Crossing end=new Crossing(interpolate(q,v,0.5),1,0);
            first=arc(start,end);second=arc(end,start);seam=crack(start,end,r);
            if(seam==null) throw new IllegalStateException("Could not split rock outline");
            appendSeam(first,second,seam);
        }
        Point start=seam.get(0),end=seam.get(seam.size()-1);
        double along=Math.atan2(end.y-start.y,end.x-start.x)+angle+Math.PI/2;
        Point c=centroid(first);
        if(cross(start.x,start.y,end.x,end.y,c.x,c.y)<0) along+=Math.PI;
        double sign=r.nextBoolean()?1:-1;
        return new Rock[]{
            fragment(first,along,speed*(1.85+0.3*r.nextDouble()),sign*Math.abs(spin)*(1.75+0.5*r.nextDouble())),
            fragment(second,along+Math.PI,speed*(1.85+0.3*r.nextDouble()),-sign*Math.abs(spin)*(1.75+0.5*r.nextDouble()))
        };
    }

    private static final class Crossing {
        final Point p;
        final int edge;
        final double order;
        Crossing(Point p,int edge,double order){this.p=p;this.edge=edge;this.order=order;}
    }

    private List<Point> arc(Crossing start,Crossing end) {
        List<Point> result=new ArrayList<>();
        result.add(start.p);
        for(int i=(start.edge+1)%points.size();;i=(i+1)%points.size()) {
            result.add(points.get(i));
            if(i==end.edge) break;
        }
        result.add(end.p);
        return result;
    }

    private static void appendSeam(List<Point> first,List<Point> second,List<Point> seam) {
        for(int i=seam.size()-2;i>0;i--) first.add(seam.get(i));
        for(int i=1;i<seam.size()-1;i++) second.add(seam.get(i));
    }

    private List<Point> crack(Crossing start,Crossing end,Random r) {
        Point a=start.p,b=end.p;
        double dx=b.x-a.x,dy=b.y-a.y,length=Math.hypot(dx,dy);
        if(length<1e-7) return null;
        int count=4+r.nextInt(3);
        double[] t=new double[count], bend=new double[count];
        double sign=r.nextBoolean()?1:-1;
        for(int i=0;i<count;i++) {
            t[i]=(i+0.75+0.5*r.nextDouble())/(count+1);
            bend[i]=sign*(0.05+0.09*r.nextDouble())*Math.sin(Math.PI*t[i]);
            sign=-sign;
        }
        for(double scale=1;scale>=1.0/65536;scale*=0.5) {
            List<Point> line=new ArrayList<>();
            line.add(a);
            for(int i=0;i<count;i++)
                line.add(new Point(a.x+dx*t[i]-dy*bend[i]*scale,a.y+dy*t[i]+dx*bend[i]*scale));
            line.add(b);
            if(internal(line)) return line;
        }
        return null;
    }

    private boolean internal(List<Point> line) {
        for(int i=1;i<line.size();i++) {
            Point a=line.get(i-1),b=line.get(i),m=interpolate(a,b,0.5);
            if(!contains(m.x,m.y)) return false;
            for(int j=0;j<points.size();j++) {
                Point p=points.get(j),q=points.get((j+1)%points.size());
                if(i==1&&distance(a.x,a.y,p.x,p.y,q.x,q.y)<1e-8) continue;
                if(i==line.size()-1&&distance(b.x,b.y,p.x,p.y,q.x,q.y)<1e-8) continue;
                if(intersects(a.x,a.y,b.x,b.y,p,q)) return false;
            }
        }
        return true;
    }

    private static Point interpolate(Point p,Point q,double t) {
        return new Point(p.x+(q.x-p.x)*t,p.y+(q.y-p.y)*t);
    }

    private static List<Point> detail(List<Point> hull,Random r) {
        List<Point> result=new ArrayList<>();
        for(int i=0;i<hull.size();i++) {
            Point a=hull.get(i),b=hull.get((i+1)%hull.size());
            result.add(a);
            for(int j=1;j<4;j++) {
                Point p=interpolate(a,b,(j+(r.nextDouble()-0.5)*0.43)/4);
                double inset=0.015+0.13*r.nextDouble();
                result.add(new Point(p.x*(1-inset),p.y*(1-inset)));
            }
        }
        return result;
    }

    private Rock fragment(List<Point> poly,double direction,double speed,double spin) {
        Point c=centroid(poly);
        double ca=Math.cos(angle),sa=Math.sin(angle);
        return new Rock(centered(poly,c),x+c.x*ca-c.y*sa,y+c.x*sa+c.y*ca,
                angle,direction,speed,spin,depth+1);
    }

    private static List<Point> hull(List<Point> in) {
        in.sort(Comparator.comparingDouble(p->p.x));
        List<Point> out=new ArrayList<>();
        for(Point p:in) addHull(out,p,0);
        int floor=out.size()-1;
        for(int i=in.size()-2;i>=0;i--) addHull(out,in.get(i),floor);
        out.remove(out.size()-1);
        return out;
    }

    private static void addHull(List<Point> out,Point p,int floor) {
        while(out.size()>=floor+2) {
            Point a=out.get(out.size()-2),b=out.get(out.size()-1);
            if((b.x-a.x)*(p.y-a.y)-(b.y-a.y)*(p.x-a.x)>0) break;
            out.remove(out.size()-1);
        }
        out.add(p);
    }

    static double area(List<Point> poly) {
        double sum=0;
        for(int i=0;i<poly.size();i++) {
            Point a=poly.get(i),b=poly.get((i+1)%poly.size());
            sum+=a.x*b.y-a.y*b.x;
        }
        return sum/2;
    }

    private static Point centroid(List<Point> poly) {
        double sum=0,x=0,y=0;
        for(int i=0;i<poly.size();i++) {
            Point a=poly.get(i),b=poly.get((i+1)%poly.size());
            double cross=a.x*b.y-a.y*b.x;
            sum+=cross; x+=(a.x+b.x)*cross; y+=(a.y+b.y)*cross;
        }
        return new Point(x/(3*sum),y/(3*sum));
    }

    private static List<Point> centered(List<Point> poly,Point c) {
        List<Point> result=new ArrayList<>();
        for(Point p:poly) result.add(new Point(p.x-c.x,p.y-c.y));
        return result;
    }

    /** Tests a swept point or circle against the filled outline, in world-oriented local coordinates. */
    boolean touches(double x0,double y0,double x1,double y1,double padding) {
        if(Math.hypot(x0,y0)>radius+padding+Math.hypot(x1-x0,y1-y0)) return false;
        double ca=Math.cos(angle),sa=Math.sin(angle);
        double ax=x0*ca+y0*sa, ay=-x0*sa+y0*ca;
        double bx=x1*ca+y1*sa, by=-x1*sa+y1*ca;
        if(contains(ax,ay)) return true;
        Point p=points.get(points.size()-1);
        for(Point q:points) {
            if(intersects(ax,ay,bx,by,p,q)) return true;
            if(padding>0&&(distance(ax,ay,p.x,p.y,q.x,q.y)<=padding
                    ||distance(bx,by,p.x,p.y,q.x,q.y)<=padding
                    ||distance(p.x,p.y,ax,ay,bx,by)<=padding)) return true;
            p=q;
        }
        return false;
    }

    private boolean contains(double x,double y) {
        boolean inside=false;
        Point p=points.get(points.size()-1);
        for(Point q:points) {
            if((p.y>y)!=(q.y>y)&&x<(q.x-p.x)*(y-p.y)/(q.y-p.y)+p.x) inside=!inside;
            p=q;
        }
        return inside;
    }

    private static boolean intersects(double ax,double ay,double bx,double by,Point p,Point q) {
        double u=cross(p.x,p.y,q.x,q.y,ax,ay),v=cross(p.x,p.y,q.x,q.y,bx,by);
        double s=cross(ax,ay,bx,by,p.x,p.y),t=cross(ax,ay,bx,by,q.x,q.y);
        return u*v<=0&&s*t<=0&&Math.max(ax,bx)>=Math.min(p.x,q.x)&&Math.max(p.x,q.x)>=Math.min(ax,bx)
                    &&Math.max(ay,by)>=Math.min(p.y,q.y)&&Math.max(p.y,q.y)>=Math.min(ay,by);
    }

    private static double cross(double ax,double ay,double bx,double by,double x,double y) {
        return (bx-ax)*(y-ay)-(by-ay)*(x-ax);
    }

    static double distance(double x,double y,double ax,double ay,double bx,double by) {
        double dx=bx-ax,dy=by-ay,len=dx*dx+dy*dy;
        double t=len==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/len));
        return Math.hypot(x-ax-t*dx,y-ay-t*dy);
    }
}
