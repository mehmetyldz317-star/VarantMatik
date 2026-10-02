package com.semaatlasi.app;

public class SkyObject {
    public final String id;
    public final String name;
    public final String tr;
    public final String con;
    public final double ra;
    public final double dec;
    public final double mag;
    public final double dist;
    public final String type;
    public final double xPc, yPc, zPc;
    public final double vxPcYr, vyPcYr, vzPcYr;

    public volatile double alt;
    public volatile double az;
    public volatile float x;
    public volatile float y;
    public volatile boolean projectedVisible;

    public SkyObject(String id, String name, String tr, String con,
                     double ra, double dec, double mag, double dist, String type) {
        this(id,name,tr,con,ra,dec,mag,dist,type,
                Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN);
    }

    public SkyObject(String id, String name, String tr, String con,
                     double ra, double dec, double mag, double dist, String type,
                     double xPc, double yPc, double zPc,
                     double vxPcYr, double vyPcYr, double vzPcYr) {
        this.id = id;
        this.name = name;
        this.tr = tr == null ? "" : tr;
        this.con = con == null ? "" : con;
        this.ra = ra;
        this.dec = dec;
        this.mag = mag;
        this.dist = dist;
        this.type = type == null ? "star" : type;
        this.xPc=xPc; this.yPc=yPc; this.zPc=zPc;
        this.vxPcYr=vxPcYr; this.vyPcYr=vyPcYr; this.vzPcYr=vzPcYr;
    }

    public boolean hasSpaceMotion() {
        return Double.isFinite(xPc) && Double.isFinite(yPc) && Double.isFinite(zPc)
                && Double.isFinite(vxPcYr) && Double.isFinite(vyPcYr) && Double.isFinite(vzPcYr);
    }

    public String displayName() {
        return tr.isEmpty() || tr.equalsIgnoreCase(name) ? name : name + " • " + tr;
    }

    public boolean isStar() { return "star".equals(type); }
    public boolean isPlanet() { return "planet".equals(type); }
    public boolean isMoon() { return "moon".equals(type); }
}
