package com.semaatlasi.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SkyView extends View {
    public interface Listener {
        void onObjectSelected(SkyObject object);
        void onDirectionChanged(double heading, double altitude);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<SkyObject> stars;
    private List<SkyObject> solar = new ArrayList<>();
    private final Map<String, SkyObject> named = new HashMap<>();
    private final ScaleGestureDetector scaleDetector;

    private Listener listener;
    private double lat = 37.9144;
    private double lon = 40.2306;
    private double heading = 180;
    private double altitude = 45;
    private double headingOffset = 0;
    private double fov = 72;
    private double arHorizontalFov = 58;
    private double arVerticalFov = 74;
    private boolean sensorMode = false;
    private boolean hasSensorPose = false;
    private double[] sensorRight = new double[]{1,0,0};
    private double[] sensorUp = new double[]{0,0,1};
    private double[] sensorForward = new double[]{0,1,0};
    private boolean showLabels = false;
    private boolean showConstellations = true;
    private boolean showConstellationNames = true;
    private boolean showPlanets = true;
    private boolean cityFilter = true;
    private boolean arMode = false;
    private boolean tracking = false;
    private long lastAstroUpdate = 0;
    private long lastSolarRefresh = 0;
    private SkyObject selected;

    private float downX, downY, lastX, lastY;
    private boolean moved;

    private static final String[][] CONST_LINES = {
            {"Betelgeuse","Bellatrix"},{"Bellatrix","Mintaka"},{"Mintaka","Alnilam"},{"Alnilam","Alnitak"},
            {"Alnitak","Saiph"},{"Saiph","Rigel"},{"Rigel","Bellatrix"},{"Betelgeuse","Alnitak"},
            {"Dubhe","Merak"},{"Merak","Phecda"},{"Phecda","Alioth"},{"Alioth","Mizar"},{"Mizar","Alkaid"},{"Dubhe","Alioth"},
            {"Caph","Schedar"},{"Schedar","Ruchbah"},
            {"Vega","Sheliak"},{"Sheliak","Sulafat"},{"Sulafat","Vega"},
            {"Deneb","Sadr"},{"Sadr","Gienah"},{"Sadr","Albireo"},
            {"Alpheratz","Mirach"},{"Mirach","Almach"},
            {"Markab","Scheat"},{"Scheat","Alpheratz"},{"Alpheratz","Algenib"},{"Algenib","Markab"}
    };

    private static final Map<String,String> CON_NAMES = new HashMap<>();
    static {
        CON_NAMES.put("Ori","Orion"); CON_NAMES.put("UMa","Büyük Ayı"); CON_NAMES.put("UMi","Küçük Ayı");
        CON_NAMES.put("Lyr","Çalgı"); CON_NAMES.put("Cyg","Kuğu"); CON_NAMES.put("And","Andromeda");
        CON_NAMES.put("Peg","Pegasus"); CON_NAMES.put("Cas","Kraliçe"); CON_NAMES.put("CMa","Büyük Köpek");
        CON_NAMES.put("CMi","Küçük Köpek"); CON_NAMES.put("Tau","Boğa"); CON_NAMES.put("Gem","İkizler");
        CON_NAMES.put("Leo","Aslan"); CON_NAMES.put("Vir","Başak"); CON_NAMES.put("Sco","Akrep");
        CON_NAMES.put("Sgr","Yay"); CON_NAMES.put("Aql","Kartal"); CON_NAMES.put("Aur","Arabacı");
        CON_NAMES.put("Per","Perseus"); CON_NAMES.put("Boo","Çoban"); CON_NAMES.put("Ari","Koç");
        CON_NAMES.put("Her","Herkül"); CON_NAMES.put("Lib","Terazi"); CON_NAMES.put("CrB","Kuzey Tacı");
    }

    public SkyView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        stars = StarCatalog.load(context);
        rebuildNameIndex();
        solar = Astronomy.solarSystem(System.currentTimeMillis());
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                fov = Math.max(28, Math.min(110, fov / detector.getScaleFactor()));
                invalidate();
                return true;
            }
        });
    }

    private void rebuildNameIndex() {
        named.clear();
        for (SkyObject s : stars) {
            if (!s.name.isEmpty()) named.put(s.name.toLowerCase(Locale.ROOT), s);
            if (!s.tr.isEmpty()) named.put(s.tr.toLowerCase(new Locale("tr","TR")), s);
        }
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public int getCatalogSize() { return stars.size(); }
    public void setLocation(double lat, double lon) {
        this.lat = lat; this.lon = lon; lastAstroUpdate = 0; invalidate();
    }
    public double getLat(){ return lat; }
    public double getLon(){ return lon; }

    public void setSensorMode(boolean enabled) { sensorMode = enabled; }
    public boolean isSensorMode(){ return sensorMode; }

    public void setViewDirection(double heading, double altitude) {
        if (!sensorMode) return;
        this.heading = Astronomy.norm360(heading);
        this.altitude = Math.max(-89.8, Math.min(89.8, altitude));
        if (listener != null) listener.onDirectionChanged(getEffectiveHeading(), this.altitude);
        invalidate();
    }

    public void setSensorPose(double heading, double altitude, double[] right, double[] up, double[] forward) {
        if (!sensorMode) return;
        this.heading = Astronomy.norm360(heading);
        this.altitude = Math.max(-89.8, Math.min(89.8, altitude));
        this.sensorRight = right.clone();
        this.sensorUp = up.clone();
        this.sensorForward = forward.clone();
        this.hasSensorPose = true;
        if (listener != null) listener.onDirectionChanged(this.heading, this.altitude);
        invalidate();
    }

    public void setCameraFov(double horizontalDeg, double verticalDeg) {
        if (horizontalDeg > 15 && horizontalDeg < 140) arHorizontalFov = horizontalDeg;
        if (verticalDeg > 15 && verticalDeg < 140) arVerticalFov = verticalDeg;
        invalidate();
    }

    public double getEffectiveHeading(){ return sensorMode ? Astronomy.norm360(heading) : Astronomy.norm360(heading + headingOffset); }
    public double getAltitude(){ return altitude; }
    public double getFov(){ return fov; }
    public double getHeadingOffset(){ return headingOffset; }

    public void adjustHeadingOffset(double delta) { headingOffset += delta; invalidate(); }
    public void resetHeadingOffset() { headingOffset = 0; invalidate(); }
    public void zoom(double delta) { fov = Math.max(28, Math.min(110, fov + delta)); invalidate(); }

    public boolean toggleLabels(){ showLabels = !showLabels; invalidate(); return showLabels; }
    public boolean toggleConstellations(){ showConstellations = !showConstellations; invalidate(); return showConstellations; }
    public boolean toggleConstellationNames(){ showConstellationNames = !showConstellationNames; invalidate(); return showConstellationNames; }
    public boolean togglePlanets(){ showPlanets = !showPlanets; invalidate(); return showPlanets; }
    public boolean toggleCityFilter(){ cityFilter = !cityFilter; invalidate(); return cityFilter; }
    public boolean isCityFilter(){ return cityFilter; }
    public boolean isShowingConstellations(){ return showConstellations; }
    public boolean isShowingConstellationNames(){ return showConstellationNames; }
    public boolean isShowingPlanets(){ return showPlanets; }

    public void setArMode(boolean ar) { arMode = ar; invalidate(); }
    public boolean isArMode(){ return arMode; }

    public void setTracking(boolean value){ tracking = value; invalidate(); }
    public boolean isTracking(){ return tracking; }
    public SkyObject getSelected(){ return selected; }

    public void clearSelection(){ selected = null; tracking = false; invalidate(); }

    public void centerOn(SkyObject o) {
        if (o == null) return;
        heading = Astronomy.norm360(o.az - headingOffset);
        altitude = o.alt;
        sensorMode = false;
        invalidate();
    }

    public SkyObject findObject(String query) {
        if (query == null) return null;
        String q = query.trim().toLowerCase(new Locale("tr","TR"));
        if (q.isEmpty()) return null;
        SkyObject exact = named.get(q);
        if (exact != null) return exact;
        for (SkyObject o : combinedObjects()) {
            if (o.name.toLowerCase(new Locale("tr","TR")).contains(q)
                    || o.tr.toLowerCase(new Locale("tr","TR")).contains(q)
                    || constellationName(o.con).toLowerCase(new Locale("tr","TR")).contains(q)) return o;
        }
        return null;
    }

    public List<SkyObject> getTonightObjects() {
        refreshAstronomy();
        List<SkyObject> out = new ArrayList<>();
        for (SkyObject o : combinedObjects()) {
            if (o.alt <= 8) continue;
            if (o.isStar() && o.mag > 3.2) continue;
            out.add(o);
        }
        out.sort(Comparator.comparingDouble(o -> o.mag));
        if (out.size() > 20) return new ArrayList<>(out.subList(0,20));
        return out;
    }

    public List<SkyObject> getFavoritesByNames(List<String> names) {
        List<SkyObject> out = new ArrayList<>();
        for (String n : names) {
            SkyObject o = findObject(n);
            if (o != null) out.add(o);
        }
        return out;
    }

    public void selectObject(SkyObject o, boolean track) {
        if (o == null) return;
        refreshAstronomy();
        selected = o;
        tracking = track;
        if (listener != null) listener.onObjectSelected(o);
        invalidate();
    }

    public String constellationName(String code) {
        return CON_NAMES.getOrDefault(code, code == null ? "" : code);
    }

    public static String directionName(double az) {
        String[] d={"K","KD","D","GD","G","GB","B","KB"};
        int i=(int)Math.round(Astronomy.norm360(az)/45.0)%8;
        return d[i];
    }

    private List<SkyObject> combinedObjects() {
        List<SkyObject> all = new ArrayList<>(stars.size()+8);
        all.addAll(stars);
        if (showPlanets) all.addAll(solar);
        return all;
    }

    private void refreshAstronomy() {
        long now=System.currentTimeMillis();
        if (now-lastSolarRefresh>60000) {
            String selectedId = selected == null ? null : selected.id;
            solar = AstroEngineBridge.solarSystem(now, lat, lon, 0.0);
            if (selectedId != null && !selectedId.startsWith("s") && !selectedId.startsWith("h")) {
                for (SkyObject o:solar) if (o.id.equals(selectedId)) { selected=o; break; }
            }
            lastSolarRefresh=now;
        }
        if (now-lastAstroUpdate>900) {
            Astronomy.updateAltAz(stars,lat,lon,now);
            lastAstroUpdate=now;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int w=getWidth(), h=getHeight();
        if (w<=0 || h<=0) return;

        if (!arMode) {
            paint.setShader(new LinearGradient(0,h,0,0,
                    new int[]{Color.rgb(18,27,58),Color.rgb(6,14,31),Color.rgb(2,4,10)},
                    new float[]{0,.48f,1}, Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint);
            paint.setShader(null);
            drawMilkyWay(canvas,w,h);
        } else {
            canvas.drawColor(Color.TRANSPARENT);
        }

        refreshAstronomy();
        drawHorizonAndGrid(canvas,w,h);

        List<SkyObject> all=combinedObjects();
        for (SkyObject o:all) project(o,w,h);

        if (showConstellations) drawConstellationLines(canvas,w,h);
        for (SkyObject o:all) drawObject(canvas,o);
        if (showConstellationNames) drawConstellationNames(canvas);

        drawReticle(canvas,w,h);
        if (tracking && selected != null) drawTracking(canvas,w,h,selected);

        postInvalidateOnAnimation();
    }

    private void drawMilkyWay(Canvas canvas,int w,int h) {
        paint.setShader(new LinearGradient(0,0,w,h,
                new int[]{Color.TRANSPARENT,Color.argb(22,150,165,220),Color.argb(34,190,195,235),Color.argb(18,120,145,210),Color.TRANSPARENT},
                null,Shader.TileMode.CLAMP));
        canvas.save();
        canvas.rotate(-22,w/2f,h*.45f);
        canvas.drawRect(-w*.2f,h*.36f,w*1.2f,h*.51f,paint);
        canvas.restore();
        paint.setShader(null);
    }

    private void drawHorizonAndGrid(Canvas canvas,int w,int h) {
        if (sensorMode && hasSensorPose) return;
        float cy=h*.50f;
        linePaint.setStrokeWidth(1f);
        linePaint.setColor(Color.argb(18,210,230,255));
        for (int alt=-60;alt<=90;alt+=15) {
            float y=(float)(cy-((alt-altitude)/fov)*h);
            if (y>0 && y<h) canvas.drawLine(0,y,w,y,linePaint);
        }
        float horizon=(float)(cy-((0-altitude)/fov)*h);
        if (horizon>0 && horizon<h) {
            linePaint.setColor(Color.argb(45,160,205,255));
            canvas.drawLine(0,horizon,w,horizon,linePaint);
            if (!arMode) {
                paint.setColor(Color.argb(26,25,36,70));
                canvas.drawRect(0,horizon,w,h,paint);
            }
        }
    }

    private void project(SkyObject o,int w,int h) {
        if (sensorMode && hasSensorPose) {
            double az=Math.toRadians(o.az);
            double alt=Math.toRadians(o.alt);
            double[] v=new double[]{
                    Math.cos(alt)*Math.sin(az),
                    Math.cos(alt)*Math.cos(az),
                    Math.sin(alt)
            };
            double xc=dot(v,sensorRight);
            double yc=dot(v,sensorUp);
            double zc=dot(v,sensorForward);

            double hfov=arMode?arHorizontalFov:fov*((double)w/h);
            double vfov=arMode?arVerticalFov:fov;
            double fx=(w/2.0)/Math.tan(Math.toRadians(hfov/2.0));
            double fy=(h/2.0)/Math.tan(Math.toRadians(vfov/2.0));

            if (zc <= 0.015) {
                o.x=Float.NaN; o.y=Float.NaN; o.projectedVisible=false; return;
            }
            o.x=(float)(w/2.0 + fx*(xc/zc));
            o.y=(float)(h/2.0 - fy*(yc/zc));
            o.projectedVisible=o.alt>-8 && o.x>-60 && o.x<w+60 && o.y>-60 && o.y<h+60;
            return;
        }

        double da=Astronomy.norm180(o.az-getEffectiveHeading());
        double dv=o.alt-altitude;
        double hfov=fov*((double)w/h);
        o.x=(float)(w/2.0+(da/hfov)*w);
        o.y=(float)(h*.50-(dv/fov)*h);
        o.projectedVisible=Math.abs(da)<hfov*.60 && Math.abs(dv)<fov*.64 && o.alt>-8;
    }

    private static double dot(double[] a,double[] b) {
        return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
    }

    private static double angularSeparationDeg(double az1,double alt1,double az2,double alt2) {
        double a1=Math.toRadians(alt1), a2=Math.toRadians(alt2);
        double dz=Math.toRadians(Astronomy.norm180(az1-az2));
        double cos=Math.sin(a1)*Math.sin(a2)+Math.cos(a1)*Math.cos(a2)*Math.cos(dz);
        return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cos))));
    }

    public SkyObject identifyAtCenter(double maxAngleDeg) {
        refreshAstronomy();
        SkyObject best=null;
        double bestAngle=maxAngleDeg;
        for (SkyObject o:combinedObjects()) {
            if (o.alt<0) continue;
            if (o.isStar() && o.mag>(cityFilter?4.5:6.0)) continue;
            double angle;
            if (sensorMode && hasSensorPose) {
                double az=Math.toRadians(o.az), alt=Math.toRadians(o.alt);
                double[] v=new double[]{Math.cos(alt)*Math.sin(az),Math.cos(alt)*Math.cos(az),Math.sin(alt)};
                angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,dot(v,sensorForward)))));
            } else {
                angle=angularSeparationDeg(o.az,o.alt,getEffectiveHeading(),altitude);
            }
            if (angle<bestAngle) { bestAngle=angle; best=o; }
        }
        if (best!=null) selectObject(best,false);
        return best;
    }

    private void drawObject(Canvas canvas,SkyObject o) {
        if (!o.projectedVisible) return;
        if (o.isStar() && o.mag > (cityFilter ? 4.5 : 6.0)) return;
        boolean sel=selected!=null && selected.id.equals(o.id);

        if (o.isStar()) {
            float r=(float)Math.max(.8,4.9-(o.mag+1.5)*.76);
            paint.setShader(new RadialGradient(o.x,o.y,Math.max(3,r*4.8f),
                    new int[]{Color.WHITE,Color.argb(110,205,228,255),Color.TRANSPARENT},
                    new float[]{0,.25f,1},Shader.TileMode.CLAMP));
            canvas.drawCircle(o.x,o.y,Math.max(3,r*4.8f),paint);
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(o.x,o.y,sel?r+1:r,paint);
        } else if (o.isMoon()) {
            paint.setTextSize(28f); paint.setColor(Color.rgb(255,236,184));
            canvas.drawText("☾",o.x-8,o.y+9,paint);
        } else {
            paint.setColor(Color.rgb(255,216,145));
            canvas.drawCircle(o.x,o.y,6f,paint);
        }

        if (showLabels || sel || !o.isStar() || o.mag<.25) {
            paint.setTextSize(sel?27f:24f);
            paint.setFakeBoldText(sel);
            paint.setColor(o.isStar()?Color.argb(225,238,245,255):Color.rgb(255,226,160));
            canvas.drawText(o.name,o.x+10,o.y-8,paint);
            paint.setFakeBoldText(false);
        }
        if (sel) {
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(2f);
            linePaint.setColor(Color.rgb(198,238,255));
            canvas.drawCircle(o.x,o.y,14,linePaint);
            linePaint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawConstellationLines(Canvas canvas,int w,int h) {
        linePaint.setStrokeWidth(1.4f);
        linePaint.setColor(arMode?Color.argb(90,160,220,255):Color.argb(55,150,210,255));
        for (String[] pair:CONST_LINES) {
            SkyObject a=findNamed(pair[0]),b=findNamed(pair[1]);
            if (a==null||b==null||a.alt<-8||b.alt<-8) continue;
            project(a,w,h); project(b,w,h);
            if (a.projectedVisible||b.projectedVisible) canvas.drawLine(a.x,a.y,b.x,b.y,linePaint);
        }
    }

    private SkyObject findNamed(String name) {
        SkyObject o=named.get(name.toLowerCase(Locale.ROOT));
        if (o!=null) return o;
        for (SkyObject s:stars) if (s.name.equalsIgnoreCase(name)) return s;
        return null;
    }

    private void drawConstellationNames(Canvas canvas) {
        Map<String,float[]> groups=new HashMap<>();
        for (SkyObject s:stars) {
            if (!s.projectedVisible || s.mag>4.0 || s.con.isEmpty()) continue;
            float[] g=groups.computeIfAbsent(s.con,k->new float[3]);
            g[0]+=s.x; g[1]+=s.y; g[2]+=1;
        }
        paint.setTextSize(20f); paint.setColor(Color.argb(105,165,195,230));
        for (Map.Entry<String,float[]> e:groups.entrySet()) {
            float[] g=e.getValue(); if (g[2]<2) continue;
            canvas.drawText(constellationName(e.getKey()),g[0]/g[2]+7,g[1]/g[2]+7,paint);
        }
    }

    private void drawReticle(Canvas canvas,int w,int h) {
        float cx=w/2f,cy=h*.50f;
        linePaint.setColor(Color.argb(95,255,255,255));linePaint.setStrokeWidth(1f);
        canvas.drawLine(cx-24,cy,cx-8,cy,linePaint);canvas.drawLine(cx+8,cy,cx+24,cy,linePaint);
        canvas.drawLine(cx,cy-24,cx,cy-8,linePaint);canvas.drawLine(cx,cy+8,cx,cy+24,linePaint);
        linePaint.setStyle(Paint.Style.STROKE);canvas.drawCircle(cx,cy,8,linePaint);linePaint.setStyle(Paint.Style.FILL);
    }

    private void drawTracking(Canvas canvas,int w,int h,SkyObject o) {
        project(o,w,h);
        float cx=w/2f,cy=h*.50f;
        double da=Astronomy.norm180(o.az-getEffectiveHeading());
        double dv=o.alt-altitude;
        float tx=(float)(cx+Math.signum(da)*Math.min(Math.abs(da)*7,w*.34));
        float ty=(float)(cy-Math.signum(dv)*Math.min(Math.abs(dv)*7,h*.28));
        linePaint.setColor(Color.rgb(197,239,255));linePaint.setStrokeWidth(3f);
        canvas.drawLine(cx,cy,tx,ty,linePaint);
        paint.setColor(Color.rgb(197,239,255));canvas.drawCircle(tx,ty,6,paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=lastX=event.getX(); downY=lastY=event.getY(); moved=false; return true;
            case MotionEvent.ACTION_MOVE:
                float dx=event.getX()-lastX,dy=event.getY()-lastY;
                if (Math.hypot(event.getX()-downX,event.getY()-downY)>12) moved=true;
                if (!sensorMode && !scaleDetector.isInProgress()) {
                    heading=Astronomy.norm360(heading-dx*.18);
                    altitude=Math.max(-15,Math.min(90,altitude+dy*.14));
                    if (listener!=null) listener.onDirectionChanged(getEffectiveHeading(),altitude);
                    invalidate();
                }
                lastX=event.getX();lastY=event.getY();return true;
            case MotionEvent.ACTION_UP:
                if (!moved && !scaleDetector.isInProgress()) selectNearest(event.getX(),event.getY());
                return true;
        }
        return true;
    }

    private void selectNearest(float x,float y) {
        SkyObject best=null; double bd=34;
        for (SkyObject o:combinedObjects()) {
            if (!o.projectedVisible) continue;
            if (o.isStar()&&o.mag>(cityFilter?4.5:6.0)) continue;
            double d=Math.hypot(o.x-x,o.y-y);
            if (d<bd){bd=d;best=o;}
        }
        if (best!=null) selectObject(best,false);
    }
}
