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

    public volatile double alt;
    public volatile double az;
    public volatile float x;
    public volatile float y;
    public volatile boolean projectedVisible;

    public SkyObject(String id, String name, String tr, String con,
                     double ra, double dec, double mag, double dist, String type) {
        this.id = id;
        this.name = name;
        this.tr = tr == null ? "" : tr;
        this.con = con == null ? "" : con;
        this.ra = ra;
        this.dec = dec;
        this.mag = mag;
        this.dist = dist;
        this.type = type == null ? "star" : type;
    }

    public String displayName() {
        return tr.isEmpty() || tr.equalsIgnoreCase(name) ? name : name + " • " + tr;
    }

    public boolean isStar() { return "star".equals(type); }
    public boolean isPlanet() { return "planet".equals(type); }
    public boolean isMoon() { return "moon".equals(type); }
}
