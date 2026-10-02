package com.semaatlasi.app;

import java.util.ArrayList;
import java.util.List;

public final class Astronomy {
    private Astronomy() {}

    public static final class AltAz {
        public final double alt;
        public final double az;
        public AltAz(double alt, double az) { this.alt = alt; this.az = az; }
    }

    private static double rad(double d) { return d * Math.PI / 180.0; }
    private static double deg(double r) { return r * 180.0 / Math.PI; }
    public static double norm360(double x) { x %= 360.0; if (x < 0) x += 360.0; return x; }
    public static double norm180(double x) { x = norm360(x); return x > 180.0 ? x - 360.0 : x; }
    private static double clamp(double x, double a, double b) { return Math.max(a, Math.min(b, x)); }

    private static double julianDate(long timeMs) {
        return timeMs / 86400000.0 + 2440587.5;
    }

    private static double gmst(long timeMs) {
        double jd = julianDate(timeMs);
        double t = (jd - 2451545.0) / 36525.0;
        return norm360(280.46061837 + 360.98564736629 * (jd - 2451545.0)
                + 0.000387933 * t * t - t * t * t / 38710000.0);
    }

    public static AltAz altAz(SkyObject o, double lat, double lon, long timeMs) {
        double lst = norm360(gmst(timeMs) + lon);
        double h = rad(norm180(lst - o.ra * 15.0));
        double dec = rad(o.dec);
        double phi = rad(lat);
        double sinAlt = Math.sin(phi) * Math.sin(dec)
                + Math.cos(phi) * Math.cos(dec) * Math.cos(h);
        double alt = Math.asin(clamp(sinAlt, -1, 1));
        double y = -Math.sin(h) * Math.cos(dec);
        double x = Math.sin(dec) * Math.cos(phi)
                - Math.cos(dec) * Math.sin(phi) * Math.cos(h);
        return new AltAz(deg(alt), norm360(deg(Math.atan2(y, x))));
    }

    public static void updateAltAz(List<SkyObject> objects, double lat, double lon, long timeMs) {
        for (SkyObject o : objects) {
            AltAz p = altAz(o, lat, lon, timeMs);
            o.alt = p.alt;
            o.az = p.az;
        }
    }

    private interface Elem { double at(double d); }
    private static Elem c(double v) { return d -> v; }
    private static Elem l(double a, double b) { return d -> a + b * d; }

    private static final class Orbit {
        final Elem n, i, w, a, e, m;
        Orbit(Elem n, Elem i, Elem w, Elem a, Elem e, Elem m) {
            this.n=n; this.i=i; this.w=w; this.a=a; this.e=e; this.m=m;
        }
    }

    private static final class Vec { double x,y,z; Vec(double x,double y,double z){this.x=x;this.y=y;this.z=z;} }
    private static final class Eq { double ra,dec; Eq(double ra,double dec){this.ra=ra;this.dec=dec;} }

    private static double kepler(double mDeg, double e) {
        double m = rad(norm360(mDeg));
        double E = m + e * Math.sin(m) * (1 + e * Math.cos(m));
        for (int k=0;k<6;k++) E -= (E - e*Math.sin(E) - m) / (1 - e*Math.cos(E));
        return E;
    }

    private static Vec helio(Orbit o, double d) {
        double N=rad(o.n.at(d)), I=rad(o.i.at(d)), W=rad(o.w.at(d));
        double a=o.a.at(d), e=o.e.at(d), E=kepler(o.m.at(d),e);
        double xv=a*(Math.cos(E)-e), yv=a*Math.sqrt(1-e*e)*Math.sin(E);
        double v=Math.atan2(yv,xv), r=Math.hypot(xv,yv);
        return new Vec(
                r*(Math.cos(N)*Math.cos(v+W)-Math.sin(N)*Math.sin(v+W)*Math.cos(I)),
                r*(Math.sin(N)*Math.cos(v+W)+Math.cos(N)*Math.sin(v+W)*Math.cos(I)),
                r*Math.sin(v+W)*Math.sin(I));
    }

    private static Vec sun(double d) {
        double w=282.9404 + 4.70935e-5*d;
        double e=.016709 - 1.151e-9*d;
        double E=kepler(356.047 + .9856002585*d,e);
        double x=Math.cos(E)-e, y=Math.sqrt(1-e*e)*Math.sin(E);
        double r=Math.hypot(x,y), v=Math.atan2(y,x), lon=v+rad(w);
        return new Vec(r*Math.cos(lon),r*Math.sin(lon),0);
    }

    private static Eq eqFromEcl(double x,double y,double z,double d) {
        double eps=rad(23.4393 - 3.563e-7*d);
        double ye=y*Math.cos(eps)-z*Math.sin(eps);
        double ze=y*Math.sin(eps)+z*Math.cos(eps);
        return new Eq(norm360(deg(Math.atan2(ye,x)))/15.0,
                deg(Math.atan2(ze,Math.hypot(x,ye))));
    }

    public static List<SkyObject> solarSystem(long timeMs) {
        double d=julianDate(timeMs)-2451543.5;
        Vec sun=sun(d);
        List<SkyObject> out=new ArrayList<>();

        addPlanet(out,"Mercury","Merkür",-0.4,
                new Orbit(l(48.3313,3.24587e-5),l(7.0047,5e-8),l(29.1241,1.01444e-5),c(.387098),l(.205635,5.59e-10),l(168.6562,4.0923344368)),d,sun);
        addPlanet(out,"Venus","Venüs",-4.2,
                new Orbit(l(76.6799,2.4659e-5),l(3.3946,2.75e-8),l(54.891,1.38374e-5),c(.72333),l(.006773,-1.302e-9),l(48.0052,1.6021302244)),d,sun);
        addPlanet(out,"Mars","Mars",-1.5,
                new Orbit(l(49.5574,2.11081e-5),l(1.8497,-1.78e-8),l(286.5016,2.92961e-5),c(1.523688),l(.093405,2.516e-9),l(18.6021,.5240207766)),d,sun);
        addPlanet(out,"Jupiter","Jüpiter",-2.4,
                new Orbit(l(100.4542,2.76854e-5),l(1.303,-1.557e-7),l(273.8777,1.64505e-5),c(5.20256),l(.048498,4.469e-9),l(19.895,.0830853001)),d,sun);
        addPlanet(out,"Saturn","Satürn",0.7,
                new Orbit(l(113.6634,2.3898e-5),l(2.4886,-1.081e-7),l(339.3939,2.97661e-5),c(9.55475),l(.055546,-9.499e-9),l(316.967,.0334442282)),d,sun);

        double N=125.1228-.0529538083*d, I=5.1454, W=318.0634+.1643573223*d;
        double a=60.2666,e=.0549,E=kepler(115.3654+13.0649929509*d,e);
        double xv=a*(Math.cos(E)-e),yv=a*Math.sqrt(1-e*e)*Math.sin(E);
        double v=Math.atan2(yv,xv),r=Math.hypot(xv,yv);
        double Nr=rad(N),Ir=rad(I),Wr=rad(W);
        double x=r*(Math.cos(Nr)*Math.cos(v+Wr)-Math.sin(Nr)*Math.sin(v+Wr)*Math.cos(Ir));
        double y=r*(Math.sin(Nr)*Math.cos(v+Wr)+Math.cos(Nr)*Math.sin(v+Wr)*Math.cos(Ir));
        double z=r*Math.sin(v+Wr)*Math.sin(Ir);
        Eq moon=eqFromEcl(x,y,z,d);
        out.add(new SkyObject("moon","Ay","","Dünya'nın uydusu",moon.ra,moon.dec,-10,384400,"moon"));
        return out;
    }

    private static void addPlanet(List<SkyObject> out,String key,String tr,double mag,Orbit orbit,double d,Vec sun) {
        Vec h=helio(orbit,d);
        Eq q=eqFromEcl(h.x+sun.x,h.y+sun.y,h.z,d);
        out.add(new SkyObject("planet-"+key,key,tr,"Gezegen",q.ra,q.dec,mag,Double.NaN,"planet"));
    }
}
