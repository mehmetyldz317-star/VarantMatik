package com.semaatlasi.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements SensorEventListener, SkyView.Listener {
    private static final int REQ_CAMERA=201;
    private static final int REQ_LOCATION=202;

    private FrameLayout root;
    private TextureView cameraView;
    private SkyView skyView;
    private CameraController cameraController;

    private TextView statusText, sensorButton, dirHud, altHud, modeHud;
    private LinearLayout objectCard;
    private TextView objectName, objectSub, objectMetrics, objectVisibility;
    private TextView trackButton, saveButton;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private boolean sensorEnabled=false;
    private float magneticDeclination=0f;
    private double smoothHeading=Double.NaN;
    private double smoothAlt=Double.NaN;

    private LocationManager locationManager;
    private final LocationListener locationListener=new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            applyLocation(location);
            try { locationManager.removeUpdates(this); } catch (Exception ignored) {}
        }
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };

    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs=getSharedPreferences("sema_prefs",MODE_PRIVATE);
        sensorManager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
        rotationSensor=sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        locationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);

        root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        cameraView=new TextureView(this);
        cameraView.setVisibility(View.INVISIBLE);
        root.addView(cameraView,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));

        skyView=new SkyView(this);
        skyView.setListener(this);
        root.addView(skyView,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));

        cameraController=new CameraController(this,cameraView,(on,msg)->runOnUiThread(()->{
            cameraView.setVisibility(on?View.VISIBLE:View.INVISIBLE);
            skyView.setArMode(on);
            modeHud.setText(on?"AR KAMERA":"GÖKYÜZÜ");
            if (!on && "Gökyüzü modu".equals(msg)) modeHud.setText("GÖKYÜZÜ");
            toast(msg);
        }));

        buildTopBar();
        buildHud();
        buildSideControls();
        buildBottomNav();
        buildObjectCard();

        restoreLocation();
        statusText.setText(String.format(Locale.getDefault(),"%d yıldız • dokunarak tanı",skyView.getCatalogSize()));
    }

    private void buildTopBar() {
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10),dp(8),dp(10),dp(8));
        bar.setBackground(glass(0xC90A0F20,18,0x22FFFFFF));

        LinearLayout titleBox=new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title=text("SEMÂ ATLASI",15,Color.WHITE,true);
        statusText=text("Gökyüzü hazırlanıyor…",10,0xFF9DA9C3,false);
        titleBox.addView(title);
        titleBox.addView(statusText);
        bar.addView(titleBox,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));

        sensorButton=button("📡  SENSÖR",11);
        sensorButton.setOnClickListener(v->toggleSensor());
        bar.addView(sensorButton,new LinearLayout.LayoutParams(dp(92),dp(40)));

        TextView search=button("⌕",20);
        search.setOnClickListener(v->showSearch());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(42),dp(40)); sp.leftMargin=dp(7);
        bar.addView(search,sp);

        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,dp(62),Gravity.TOP);
        lp.setMargins(dp(10),dp(9),dp(10),0);
        root.addView(bar,lp);
    }

    private void buildHud() {
        LinearLayout hud=new LinearLayout(this);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER);
        dirHud=hudChip("G • 180°");
        altHud=hudChip("45°");
        modeHud=hudChip("GÖKYÜZÜ");
        hud.addView(dirHud); hud.addView(space(5,1)); hud.addView(altHud); hud.addView(space(5,1)); hud.addView(modeHud);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,dp(34),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        lp.topMargin=dp(73);
        root.addView(hud,lp);
    }

    private void buildSideControls() {
        LinearLayout side=new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        String[] labels={"⌖","＋","−","Aa","AR"};
        for (String s:labels) {
            TextView b=button(s,s.length()>1?11:18);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(47),dp(47)); p.bottomMargin=dp(7);
            side.addView(b,p);
            if ("⌖".equals(s)) b.setOnClickListener(v->requestLocation());
            if ("＋".equals(s)) b.setOnClickListener(v->{skyView.zoom(-8); updateHud();});
            if ("−".equals(s)) b.setOnClickListener(v->{skyView.zoom(8); updateHud();});
            if ("Aa".equals(s)) b.setOnClickListener(v->toast(skyView.toggleLabels()?"Yıldız isimleri açık":"Yıldız isimleri sade"));
            if ("AR".equals(s)) b.setOnClickListener(v->toggleAr());
        }
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(50),FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.END);
        lp.topMargin=dp(120); lp.rightMargin=dp(11);
        root.addView(side,lp);
    }

    private void buildBottomNav() {
        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(5),dp(5),dp(5),dp(5));
        nav.setBackground(glass(0xED080D1D,22,0x22FFFFFF));

        String[][] items={{"✦","Gökyüzü"},{"☾","Bu Gece"},{"⌕","Ara"},{"☆","Kaydedilen"},{"⚙","Ayarlar"}};
        for (String[] it:items) {
            LinearLayout cell=new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
            TextView icon=text(it[0],19,Color.WHITE,false); TextView lab=text(it[1],9,0xFF9DA9C3,false);
            cell.addView(icon);cell.addView(lab);
            cell.setBackground(glass(0x00111111,15,0));
            cell.setClickable(true);cell.setFocusable(true);
            nav.addView(cell,new LinearLayout.LayoutParams(0,dp(55),1f));
            switch(it[1]) {
                case "Gökyüzü" -> cell.setOnClickListener(v->{skyView.clearSelection(); hideObjectCard();});
                case "Bu Gece" -> cell.setOnClickListener(v->showTonight());
                case "Ara" -> cell.setOnClickListener(v->showSearch());
                case "Kaydedilen" -> cell.setOnClickListener(v->showSaved());
                case "Ayarlar" -> cell.setOnClickListener(v->showSettings());
            }
        }
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,dp(66),Gravity.BOTTOM);
        lp.setMargins(dp(9),0,dp(9),dp(8));
        root.addView(nav,lp);
    }

    private void buildObjectCard() {
        objectCard=new LinearLayout(this);
        objectCard.setOrientation(LinearLayout.VERTICAL);
        objectCard.setPadding(dp(14),dp(13),dp(14),dp(12));
        objectCard.setBackground(glass(0xF1080D1E,22,0x22FFFFFF));
        objectCard.setVisibility(View.GONE);

        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView star=text("★",29,Color.WHITE,false);
        head.addView(star,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout nameBox=new LinearLayout(this); nameBox.setOrientation(LinearLayout.VERTICAL);
        objectName=text("Vega",20,Color.WHITE,true);
        objectSub=text("Çalgı Takımyıldızı",11,0xFFA2AEC5,false);
        nameBox.addView(objectName);nameBox.addView(objectSub);
        head.addView(nameBox,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        TextView close=button("×",20); close.setOnClickListener(v->{skyView.clearSelection();hideObjectCard();});
        head.addView(close,new LinearLayout.LayoutParams(dp(36),dp(36)));
        objectCard.addView(head);

        objectMetrics=text("Parlaklık • Uzaklık • Yön • Yükseklik",11,0xFFE5EDFF,false);
        objectMetrics.setPadding(dp(10),dp(9),dp(10),dp(9));
        objectMetrics.setBackground(glass(0x121FFFFFF,13,0x10FFFFFF));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);mp.topMargin=dp(9);
        objectCard.addView(objectMetrics,mp);

        objectVisibility=text("●  Şu anda görünür",11,0xFF9BF1CB,true);
        objectVisibility.setPadding(dp(10),dp(9),dp(10),dp(9));
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);vp.topMargin=dp(7);
        objectCard.addView(objectVisibility,vp);

        LinearLayout actions=new LinearLayout(this);
        trackButton=button("Gökyüzünde Bul",10);
        TextView center=button("Ortala",10);
        saveButton=button("☆ Kaydet",10);
        actions.addView(trackButton,new LinearLayout.LayoutParams(0,dp(42),1.3f));
        LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(0,dp(42),.8f);c.leftMargin=dp(6); actions.addView(center,c);
        LinearLayout.LayoutParams s=new LinearLayout.LayoutParams(0,dp(42),.9f);s.leftMargin=dp(6);actions.addView(saveButton,s);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(42));ap.topMargin=dp(8);
        objectCard.addView(actions,ap);

        trackButton.setOnClickListener(v->{skyView.setTracking(!skyView.isTracking());trackButton.setText(skyView.isTracking()?"Takip Açık":"Gökyüzünde Bul");});
        center.setOnClickListener(v->{skyView.centerOn(skyView.getSelected());sensorEnabled=false;unregisterSensor();sensorButton.setText("📡  SENSÖR");toast("Yıldız ortalandı • manuel mod");});
        saveButton.setOnClickListener(v->toggleFavorite());

        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM);
        lp.setMargins(dp(10),0,dp(10),dp(82));
        root.addView(objectCard,lp);
    }

    private void toggleSensor() {
        if (rotationSensor==null) { toast("Bu telefonda yön sensörü bulunamadı"); return; }
        sensorEnabled=!sensorEnabled;
        skyView.setSensorMode(sensorEnabled);
        if (sensorEnabled) {
            registerSensor();
            sensorButton.setText("✓  SENSÖR");
            statusText.setText("Canlı yön • telefonu gökyüzüne çevir");
        } else {
            unregisterSensor();
            sensorButton.setText("📡  SENSÖR");
            statusText.setText("Manuel gökyüzü • sürükleyerek bak");
        }
    }

    private void registerSensor() {
        if (rotationSensor!=null) sensorManager.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);
    }

    private void unregisterSensor() { try { sensorManager.unregisterListener(this); } catch (Exception ignored) {} }

    @Override public void onSensorChanged(SensorEvent event) {
        if (!sensorEnabled || event.sensor.getType()!=Sensor.TYPE_ROTATION_VECTOR) return;
        float[] r=new float[9],remap=new float[9],ori=new float[3];
        SensorManager.getRotationMatrixFromVector(r,event.values);
        boolean ok=SensorManager.remapCoordinateSystem(r,SensorManager.AXIS_X,SensorManager.AXIS_Z,remap);
        if (!ok) return;
        SensorManager.getOrientation(remap,ori);
        double rawHeading=Math.toDegrees(ori[0]);
        if (rawHeading<0) rawHeading+=360;
        rawHeading=Astronomy.norm360(rawHeading+magneticDeclination);
        double rawAlt=Math.max(-15,Math.min(90,-Math.toDegrees(ori[1])));

        if (Double.isNaN(smoothHeading)) smoothHeading=rawHeading;
        else {
            double delta=Astronomy.norm180(rawHeading-smoothHeading);
            smoothHeading=Astronomy.norm360(smoothHeading+delta*.18);
        }
        if (Double.isNaN(smoothAlt)) smoothAlt=rawAlt;
        else smoothAlt=smoothAlt+(rawAlt-smoothAlt)*.18;

        skyView.setViewDirection(smoothHeading,smoothAlt);
    }

    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {}

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);
            return;
        }
        fetchLocation();
    }

    private void fetchLocation() {
        try {
            Location best=null;
            for (String provider:locationManager.getProviders(true)) {
                Location l=locationManager.getLastKnownLocation(provider);
                if (l!=null && (best==null || l.getTime()>best.getTime())) best=l;
            }
            if (best!=null) applyLocation(best);
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,locationListener,Looper.getMainLooper());
                toast("Konum güncelleniyor…");
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,0,0,locationListener,Looper.getMainLooper());
            }
        } catch (SecurityException e) { toast("Konum izni gerekli"); }
    }

    private void applyLocation(Location location) {
        skyView.setLocation(location.getLatitude(),location.getLongitude());
        GeomagneticField field=new GeomagneticField((float)location.getLatitude(),(float)location.getLongitude(),(float)location.getAltitude(),System.currentTimeMillis());
        magneticDeclination=field.getDeclination();
        prefs.edit().putString("lat",Double.toString(location.getLatitude())).putString("lon",Double.toString(location.getLongitude())).apply();
        statusText.setText(String.format(Locale.getDefault(),"Konum güncel • %.3f, %.3f",location.getLatitude(),location.getLongitude()));
        toast("Gerçek konum kullanılıyor");
    }

    private void restoreLocation() {
        try {
            if (prefs.contains("lat")&&prefs.contains("lon")) {
                double lat=Double.parseDouble(prefs.getString("lat","37.9144"));
                double lon=Double.parseDouble(prefs.getString("lon","40.2306"));
                skyView.setLocation(lat,lon);
                GeomagneticField f=new GeomagneticField((float)lat,(float)lon,0,System.currentTimeMillis());
                magneticDeclination=f.getDeclination();
            }
        } catch (Exception ignored) {}
    }

    private void toggleAr() {
        if (cameraController.isRunning() || skyView.isArMode()) {
            cameraController.stop();
            cameraView.setVisibility(View.INVISIBLE);
            skyView.setArMode(false);
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);
            return;
        }
        cameraView.setVisibility(View.VISIBLE);
        cameraController.start();
        if (!sensorEnabled) toggleSensor();
    }

    private void showSearch() {
        final EditText input=new EditText(this);
        input.setHint("Sirius, Vega, Orion, Jüpiter…");
        input.setSingleLine(true);
        input.setPadding(dp(12),dp(8),dp(12),dp(8));
        AlertDialog d=new AlertDialog.Builder(this)
                .setTitle("Yıldız ve gök cismi ara")
                .setMessage("İsmi yaz; uygulama gökyüzündeki yönüne götürsün.")
                .setView(input)
                .setNegativeButton("Kapat",null)
                .setPositiveButton("Bul",(dialog,which)->{
                    SkyObject o=skyView.findObject(input.getText().toString());
                    if (o==null) toast("Bulunamadı");
                    else { skyView.selectObject(o,true); showObjectCard(o); }
                }).create();
        d.setOnShowListener(x->{input.requestFocus();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);});
        d.show();
    }

    private void showTonight() {
        List<SkyObject> list=skyView.getTonightObjects();
        if (list.isEmpty()) { toast("Şu anda belirgin cisim bulunamadı"); return; }
        int n=Math.min(14,list.size());
        String[] labels=new String[n];
        for (int i=0;i<n;i++) {
            SkyObject o=list.get(i);
            labels[i]=o.displayName()+"   •   "+SkyView.directionName(o.az)+" "+Math.round(o.alt)+"°";
        }
        new AlertDialog.Builder(this).setTitle("Bu Gece • Şimdi Görülebilenler")
                .setItems(labels,(d,which)->{SkyObject o=list.get(which);skyView.selectObject(o,true);showObjectCard(o);})
                .setNegativeButton("Kapat",null).show();
    }

    private Set<String> favoriteNames() {
        return new LinkedHashSet<>(prefs.getStringSet("favorites",new HashSet<>()));
    }

    private void toggleFavorite() {
        SkyObject o=skyView.getSelected(); if (o==null) return;
        Set<String> fav=favoriteNames();
        if (fav.contains(o.name)) fav.remove(o.name); else fav.add(o.name);
        prefs.edit().putStringSet("favorites",fav).apply();
        saveButton.setText(fav.contains(o.name)?"★ Kayıtlı":"☆ Kaydet");
        toast(fav.contains(o.name)?"Kaydedildi":"Kayıttan çıkarıldı");
    }

    private void showSaved() {
        List<String> names=new ArrayList<>(favoriteNames());
        List<SkyObject> list=skyView.getFavoritesByNames(names);
        if (list.isEmpty()) { toast("Henüz kaydedilmiş yıldız yok"); return; }
        String[] labels=new String[list.size()];
        for (int i=0;i<list.size();i++) labels[i]=list.get(i).displayName();
        new AlertDialog.Builder(this).setTitle("Kaydedilenler")
                .setItems(labels,(d,which)->{SkyObject o=list.get(which);skyView.selectObject(o,true);showObjectCard(o);})
                .setNegativeButton("Kapat",null).show();
    }

    private void showSettings() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(6),dp(12),dp(8));
        scroll.addView(box);

        TextView info=text("Pusula kalibrasyonu",12,Color.WHITE,true); box.addView(info);
        TextView desc=text("Yıldızlar kamera görüntüsünde sağa/sola kayıyorsa yön ofsetini düzelt.",10,0xFFA2AEC5,false);
        box.addView(desc);

        LinearLayout cal=new LinearLayout(this);
        TextView minus=button("− 5°",11),reset=button("Sıfırla",11),plus=button("+ 5°",11);
        cal.addView(minus,new LinearLayout.LayoutParams(0,dp(42),1));cal.addView(reset,new LinearLayout.LayoutParams(0,dp(42),1));cal.addView(plus,new LinearLayout.LayoutParams(0,dp(42),1));
        box.addView(cal);
        minus.setOnClickListener(v->{skyView.adjustHeadingOffset(-5);toast("Ofset "+Math.round(skyView.getHeadingOffset())+"°");});
        plus.setOnClickListener(v->{skyView.adjustHeadingOffset(5);toast("Ofset "+Math.round(skyView.getHeadingOffset())+"°");});
        reset.setOnClickListener(v->{skyView.resetHeadingOffset();toast("Kalibrasyon sıfırlandı");});

        TextView c1=settingButton("Takımyıldız çizgileri",skyView.isShowingConstellations());
        TextView c2=settingButton("Takımyıldız adları",skyView.isShowingConstellationNames());
        TextView c3=settingButton("Gezegenler ve Ay",skyView.isShowingPlanets());
        TextView c4=settingButton("Şehir ışığı filtresi",skyView.isCityFilter());
        box.addView(c1);box.addView(c2);box.addView(c3);box.addView(c4);
        c1.setOnClickListener(v->{boolean x=skyView.toggleConstellations();c1.setText(settingText("Takımyıldız çizgileri",x));});
        c2.setOnClickListener(v->{boolean x=skyView.toggleConstellationNames();c2.setText(settingText("Takımyıldız adları",x));});
        c3.setOnClickListener(v->{boolean x=skyView.togglePlanets();c3.setText(settingText("Gezegenler ve Ay",x));});
        c4.setOnClickListener(v->{boolean x=skyView.toggleCityFilter();c4.setText(settingText("Şehir ışığı filtresi",x));});

        TextView cat=text("Katalog: "+skyView.getCatalogSize()+" yıldız\nKonum: "+String.format(Locale.getDefault(),"%.4f, %.4f",skyView.getLat(),skyView.getLon())+"\nHYG Database • 6. kadire kadar çıplak göz kataloğu",10,0xFF98A5BF,false);
        cat.setPadding(0,dp(12),0,0);box.addView(cat);

        new AlertDialog.Builder(this).setTitle("Ayarlar ve Kalibrasyon").setView(scroll)
                .setPositiveButton("Tamam",null).show();
    }

    private TextView settingButton(String name,boolean on) {
        TextView b=button(settingText(name,on),11);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(44));p.topMargin=dp(7);b.setLayoutParams(p);
        return b;
    }
    private String settingText(String name,boolean on){return (on?"✓  ":"○  ")+name;}

    private void showObjectCard(SkyObject o) {
        objectCard.setVisibility(View.VISIBLE);
        objectName.setText(o.displayName());
        objectSub.setText(o.isStar()?skyView.constellationName(o.con)+" Takımyıldızı":o.con);
        String mag=String.format(Locale.getDefault(),"%.2f kadir",o.mag);
        String dist;
        if (o.isMoon()) dist="≈384 bin km";
        else if (o.isStar()&&!Double.isNaN(o.dist)) dist=(o.dist>=1000?Math.round(o.dist)+"":" "+String.format(Locale.getDefault(),"%.1f",o.dist))+" IY";
        else dist="Güneş Sistemi";
        objectMetrics.setText("Parlaklık  "+mag+"    •    Uzaklık  "+dist+"\nYön  "+SkyView.directionName(o.az)+" "+Math.round(o.az)+"°    •    Yükseklik  "+String.format(Locale.getDefault(),"%.1f°",o.alt));

        if (o.alt<0) {
            objectVisibility.setText("●  Ufkun altında • şu anda görünmüyor");
            objectVisibility.setTextColor(0xFFFF9B9B);
        } else if (o.alt>40 && (!o.isStar()||o.mag<2)) {
            objectVisibility.setText("●  Çok iyi konumda • rahat seçilebilir");
            objectVisibility.setTextColor(0xFF9BF1CB);
        } else if (o.alt<12) {
            objectVisibility.setText("●  Ufka yakın • bina ve ışık engelleyebilir");
            objectVisibility.setTextColor(0xFFFFCF87);
        } else {
            objectVisibility.setText("●  Görünür • uygun gökyüzünde seçilebilir");
            objectVisibility.setTextColor(0xFF9BF1CB);
        }
        trackButton.setText(skyView.isTracking()?"Takip Açık":"Gökyüzünde Bul");
        saveButton.setText(favoriteNames().contains(o.name)?"★ Kayıtlı":"☆ Kaydet");
    }

    private void hideObjectCard(){ objectCard.setVisibility(View.GONE); }

    @Override public void onObjectSelected(SkyObject object) { runOnUiThread(()->showObjectCard(object)); }

    @Override public void onDirectionChanged(double heading,double altitude) { runOnUiThread(this::updateHud); }

    private void updateHud() {
        dirHud.setText(SkyView.directionName(skyView.getEffectiveHeading())+" • "+Math.round(skyView.getEffectiveHeading())+"°");
        altHud.setText(Math.round(skyView.getAltitude())+"°");
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode==REQ_CAMERA) {
            if (grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED) toggleAr();
            else toast("AR için kamera izni gerekli");
        } else if (requestCode==REQ_LOCATION) {
            boolean granted=false;for(int g:grantResults)if(g==PackageManager.PERMISSION_GRANTED)granted=true;
            if (granted) fetchLocation(); else toast("Konum verilmezse varsayılan konum kullanılır");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensorEnabled) registerSensor();
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterSensor();
        if (cameraController!=null && cameraController.isRunning()) cameraController.stop();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
    }

    private TextView text(String value,float sp,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView button(String value,float sp) {
        TextView t=text(value,sp,Color.WHITE,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(7),0,dp(7),0);
        t.setBackground(glass(0xCC0B1227,14,0x28FFFFFF));t.setClickable(true);t.setFocusable(true);
        return t;
    }

    private TextView hudChip(String value) {
        TextView t=text(value,10,0xFFDDE8FF,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(10),0,dp(10),0);
        t.setBackground(glass(0x99070C1B,99,0x18FFFFFF));return t;
    }

    private View space(int w,int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(dp(w),dp(h)));return v;}

    private GradientDrawable glass(int color,float radius,int strokeColor) {
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));
        if (strokeColor!=0) g.setStroke(dp(1),strokeColor);return g;
    }

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
