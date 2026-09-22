package com.mizandar.matrixevreni;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.util.*;

public class MatrixMarketView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(7319);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Stock> stocks = new ArrayList<>();
    private final ArrayList<Rain> rain = new ArrayList<>();
    private final ArrayList<String> tape = new ArrayList<>();
    private final HashSet<String> favorites = new HashSet<>();
    private int tab = 0;
    private Stock selected = null;
    private long tick = 0;
    private float indexValue = 13242.6f;
    private float indexChange = 0.84f;

    public MatrixMarketView(Context c) {
        super(c);
        setBackgroundColor(Color.rgb(2,7,4));
        stroke.setStyle(Paint.Style.STROKE);
        initStocks();
        for(int i=0;i<55;i++) rain.add(new Rain(rnd.nextFloat(), rnd.nextFloat(), .35f+rnd.nextFloat()*1.1f, 6+rnd.nextInt(12)));
        handler.post(loop);
    }

    private void initStocks(){
        String[] syms={"AKBNK","ASELS","ASTOR","BIMAS","DSTKF","EKGYO","ENKAI","EREGL","FROTO","GARAN","GUBRF","ISCTR","KCHOL","KRDMD","MGROS","PETKM","PGSUS","SAHOL","SASA","SISE","TAVHL","TCELL","THYAO","TOASO","TRALT","TTKOM","TUPRS","VAKBN","YKBNK","AEFES"};
        for(int i=0;i<syms.length;i++){
            Stock s=new Stock(); s.sym=syms[i]; s.price=35+rnd.nextFloat()*330; s.change=-2.5f+rnd.nextFloat()*5f; s.volume=.35f+rnd.nextFloat()*1.8f; s.phase=(float)(i*Math.PI*2/syms.length); s.history=new float[34];
            float v=s.price; for(int k=0;k<s.history.length;k++){v+=(rnd.nextFloat()-.48f)*2.1f;s.history[k]=Math.max(1,v);} stocks.add(s);
        }
    }

    private final Runnable loop=new Runnable(){@Override public void run(){
        tick++;
        for(Stock s:stocks){
            float d=(rnd.nextFloat()-.5f)*.22f; s.price=Math.max(1,s.price+d); s.change+=d*.06f; s.change=Math.max(-9.7f,Math.min(9.7f,s.change));
            if(tick%4==0){System.arraycopy(s.history,1,s.history,0,s.history.length-1);s.history[s.history.length-1]=s.price;}
        }
        indexValue+=(rnd.nextFloat()-.49f)*2.8f; indexChange+=(rnd.nextFloat()-.5f)*.015f;
        if(tick%3==0){Stock s=stocks.get(rnd.nextInt(stocks.size()));tape.add(0,String.format(Locale.US,"%s  %.2f  %s%.2f%%",s.sym,s.price,s.change>=0?"▲ +":"▼ ",Math.abs(s.change)));if(tape.size()>14)tape.remove(tape.size()-1);}
        invalidate(); handler.postDelayed(this,700);
    }};

    @Override protected void onDraw(Canvas c){
        super.onDraw(c); float w=getWidth(),h=getHeight();
        drawRain(c,w,h); drawHeader(c,w);
        if(tab==0)drawUniverse(c,w,h); else if(tab==1)drawTape(c,w,h); else drawFavorites(c,w,h);
        drawBottom(c,w,h); if(selected!=null)drawDetail(c,w,h,selected);
    }

    private void drawRain(Canvas c,float w,float h){
        p.setTypeface(Typeface.MONOSPACE); p.setTextSize(dp(9));
        for(Rain r:rain){float x=r.x*w,y=((r.y+tick*.0025f*r.speed)%1.08f)*h;for(int j=0;j<r.len;j++){p.setColor(Color.argb(Math.max(5,50-j*3),50,255,130));c.drawText(String.valueOf((char)('0'+rnd.nextInt(10))),x,y-j*dp(11),p);}}
        p.setColor(Color.argb(18,45,255,125));for(int i=0;i<10;i++){float y=dp(90)+i*dp(54);c.drawLine(0,y,w,y,p);}
    }

    private void drawHeader(Canvas c,float w){
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setColor(Color.rgb(105,255,160));p.setTextSize(dp(20));c.drawText("MATRIX EVRENİ",dp(18),dp(34),p);
        p.setTypeface(Typeface.MONOSPACE);p.setColor(Color.rgb(100,145,116));p.setTextSize(dp(9));c.drawText("KİŞİSEL PİYASA PANELİ  •  DEMO AKIŞ",dp(18),dp(51),p);
        float top=dp(62);rounded(c,dp(14),top,w-dp(14),top+dp(54),dp(16),Color.argb(205,8,20,13));
        p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(10));p.setColor(Color.rgb(115,165,130));c.drawText("BIST 30",dp(26),top+dp(18),p);
        p.setTextSize(dp(19));p.setColor(Color.WHITE);c.drawText(String.format(Locale.US,"%,.1f",indexValue),dp(26),top+dp(41),p);
        p.setTextAlign(Paint.Align.RIGHT);p.setTextSize(dp(14));p.setColor(indexChange>=0?Color.rgb(65,255,125):Color.rgb(255,90,90));c.drawText(String.format(Locale.US,"%s%.2f%%",indexChange>=0?"▲ +":"▼ ",Math.abs(indexChange)),w-dp(25),top+dp(31),p);
        p.setTextSize(dp(8));p.setColor(Color.rgb(80,125,95));c.drawText("SIMÜLASYON",w-dp(25),top+dp(47),p);
    }

    private void drawUniverse(Canvas c,float w,float h){
        float cx=w/2,cy=dp(355),base=Math.min(w,dp(390))*.34f;
        stroke.setStrokeWidth(dp(1));stroke.setColor(Color.argb(70,70,255,135));for(int r=1;r<=3;r++)c.drawCircle(cx,cy,base*r/3f,stroke);
        for(int i=0;i<24;i++){double a=i*Math.PI/12+tick*.002;float rr=base*.93f,x=cx+(float)Math.cos(a)*rr,y=cy+(float)Math.sin(a)*rr;p.setColor(Color.argb(60,70,255,140));c.drawCircle(x,y,dp(1.4f),p);}
        RadialGradient g=new RadialGradient(cx,cy,dp(58),new int[]{Color.argb(210,90,255,150),Color.argb(80,25,130,75),Color.TRANSPARENT},null,Shader.TileMode.CLAMP);p.setShader(g);c.drawCircle(cx,cy,dp(60),p);p.setShader(null);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(13));p.setColor(Color.WHITE);c.drawText("XU030",cx,cy-dp(3),p);p.setTextSize(dp(9));p.setColor(Color.rgb(110,255,160));c.drawText("CANLI ÇEKİRDEK",cx,cy+dp(14),p);
        float r1=base*.60f,r2=base*.96f;
        for(int i=0;i<stocks.size();i++){Stock s=stocks.get(i);float r=i%2==0?r1:r2;double a=s.phase+tick*.0006*(i%2==0?1:-1);float x=cx+(float)Math.cos(a)*r,y=cy+(float)Math.sin(a)*r*.82f;s.x=x;s.y=y;float rad=dp(14+Math.min(8,s.volume*3));int col=s.change>=0?Color.rgb(45,235,115):Color.rgb(240,75,80);p.setColor(Color.argb(35,Color.red(col),Color.green(col),Color.blue(col)));c.drawCircle(x,y,rad*1.7f,p);p.setColor(Color.argb(225,8,19,13));c.drawCircle(x,y,rad,p);stroke.setStrokeWidth(dp(1.2f));stroke.setColor(col);c.drawCircle(x,y,rad,stroke);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(7.5f));p.setColor(Color.WHITE);c.drawText(s.sym,x,y+dp(2),p);}
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.MONOSPACE);p.setTextSize(dp(8.5f));p.setColor(Color.rgb(84,150,105));c.drawText("Dokun → detay  •  Küre = hacim  •  Renk = yön",dp(18),h-dp(83),p);
    }

    private void drawTape(Canvas c,float w,float h){
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(15));p.setColor(Color.WHITE);c.drawText("PİYASA AKIŞI",dp(18),dp(153),p);
        p.setTypeface(Typeface.MONOSPACE);p.setTextSize(dp(9));p.setColor(Color.rgb(82,145,100));c.drawText("Son hareketler Matrix akışında",dp(18),dp(171),p);
        float y=dp(196);for(int i=0;i<Math.max(10,tape.size());i++){Stock s=stocks.get(i%stocks.size());String t=i<tape.size()?tape.get(i):String.format(Locale.US,"%s  %.2f  %s%.2f%%",s.sym,s.price,s.change>=0?"▲ +":"▼ ",Math.abs(s.change));rounded(c,dp(14),y-dp(16),w-dp(14),y+dp(18),dp(10),Color.argb(150,7,17,11));p.setColor(t.contains("▲")?Color.rgb(74,255,130):Color.rgb(255,92,92));p.setTextSize(dp(11));c.drawText(t,dp(25),y+dp(3),p);y+=dp(40);if(y>h-dp(84))break;}
    }

    private void drawFavorites(Canvas c,float w,float h){
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(15));p.setColor(Color.WHITE);c.drawText("BENİM EVRENİM",dp(18),dp(153),p);
        p.setTypeface(Typeface.MONOSPACE);p.setTextSize(dp(9));p.setColor(Color.rgb(82,145,100));c.drawText("Yıldızladığın hisseler burada",dp(18),dp(171),p);
        int n=0;float y=dp(205);for(Stock s:stocks){if(!favorites.contains(s.sym))continue;drawRow(c,w,y,s);y+=dp(54);n++;}
        if(n==0){p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.rgb(92,135,105));p.setTextSize(dp(11));c.drawText("Henüz favori yok.",w/2,dp(315),p);c.drawText("Evren'de bir hisseye dokunup ★ ekleyebilirsin.",w/2,dp(336),p);}
    }

    private void drawRow(Canvas c,float w,float y,Stock s){
        rounded(c,dp(14),y-dp(27),w-dp(14),y+dp(18),dp(12),Color.argb(185,7,18,11));p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(12));p.setColor(Color.WHITE);c.drawText(s.sym,dp(26),y-dp(5),p);p.setTypeface(Typeface.MONOSPACE);p.setTextSize(dp(9));p.setColor(Color.rgb(95,145,108));c.drawText(String.format(Locale.US,"%.2f TL",s.price),dp(26),y+dp(10),p);p.setTextAlign(Paint.Align.RIGHT);p.setTextSize(dp(11));p.setColor(s.change>=0?Color.rgb(65,255,125):Color.rgb(255,90,90));c.drawText(String.format(Locale.US,"%s%.2f%%",s.change>=0?"+":"",s.change),w-dp(25),y+dp(2),p);
    }

    private void drawBottom(Canvas c,float w,float h){
        float y=h-dp(62);p.setColor(Color.argb(238,4,12,8));c.drawRect(0,y,w,h,p);String[] labs={"EVREN","AKIŞ","FAVORİLER"};for(int i=0;i<3;i++){float cx=w*(i+.5f)/3;if(tab==i)rounded(c,cx-dp(47),y+dp(9),cx+dp(47),h-dp(8),dp(14),Color.argb(180,15,64,34));p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(9));p.setColor(tab==i?Color.rgb(94,255,150):Color.rgb(85,125,98));c.drawText(labs[i],cx,y+dp(36),p);}
    }

    private void drawDetail(Canvas c,float w,float h,Stock s){
        p.setColor(Color.argb(220,0,4,2));c.drawRect(0,0,w,h,p);float l=dp(18),r=w-dp(18),t=dp(125),b=h-dp(92);rounded(c,l,t,r,b,dp(22),Color.rgb(6,17,10));stroke.setStrokeWidth(dp(1));stroke.setColor(Color.rgb(45,145,82));c.drawRoundRect(l,t,r,b,dp(22),dp(22),stroke);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(24));p.setColor(Color.WHITE);c.drawText(s.sym,l+dp(18),t+dp(38),p);p.setTypeface(Typeface.MONOSPACE);p.setTextSize(dp(9));p.setColor(Color.rgb(90,145,106));c.drawText("DEMO PİYASA VERİSİ",l+dp(18),t+dp(55),p);
        p.setTextAlign(Paint.Align.RIGHT);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(22));p.setColor(Color.WHITE);c.drawText(String.format(Locale.US,"%.2f",s.price),r-dp(18),t+dp(38),p);p.setTextSize(dp(13));p.setColor(s.change>=0?Color.rgb(65,255,125):Color.rgb(255,90,90));c.drawText(String.format(Locale.US,"%s%.2f%%",s.change>=0?"▲ +":"▼ ",Math.abs(s.change)),r-dp(18),t+dp(58),p);
        float cl=l+dp(18),cr=r-dp(18),ct=t+dp(88),cb=t+dp(235);rounded(c,cl,ct,cr,cb,dp(14),Color.rgb(4,12,7));float mn=s.history[0],mx=s.history[0];for(float v:s.history){mn=Math.min(mn,v);mx=Math.max(mx,v);}float span=Math.max(.01f,mx-mn);Path path=new Path();for(int i=0;i<s.history.length;i++){float x=cl+dp(10)+(cr-cl-dp(20))*i/(s.history.length-1f),y=cb-dp(12)-(cb-ct-dp(24))*(s.history[i]-mn)/span;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}stroke.setStrokeWidth(dp(2));stroke.setColor(s.change>=0?Color.rgb(70,255,135):Color.rgb(255,88,88));c.drawPath(path,stroke);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(10));p.setColor(Color.rgb(100,150,115));c.drawText("HACİM",l+dp(18),t+dp(270),p);p.setTextSize(dp(16));p.setColor(Color.WHITE);c.drawText(String.format(Locale.US,"%.1f M",s.volume*100),l+dp(18),t+dp(293),p);
        p.setTextAlign(Paint.Align.RIGHT);p.setTextSize(dp(10));p.setColor(Color.rgb(100,150,115));c.drawText("GÜN YÖNÜ",r-dp(18),t+dp(270),p);p.setTextSize(dp(16));p.setColor(s.change>=0?Color.rgb(65,255,125):Color.rgb(255,90,90));c.drawText(s.change>=0?"YUKARI":"AŞAĞI",r-dp(18),t+dp(293),p);
        boolean fav=favorites.contains(s.sym);float by=b-dp(54);rounded(c,l+dp(18),by,r-dp(18),by+dp(38),dp(12),fav?Color.rgb(20,80,42):Color.rgb(10,38,22));p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(11));p.setColor(Color.rgb(105,255,160));c.drawText(fav?"★ FAVORİDEN ÇIKAR":"☆ BENİM EVRENİME EKLE",w/2,by+dp(24),p);p.setTextSize(dp(9));p.setColor(Color.rgb(90,125,100));c.drawText("Kapatmak için kartın dışına dokun",w/2,b+dp(26),p);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;float x=e.getX(),y=e.getY(),w=getWidth(),h=getHeight();
        if(selected!=null){float l=dp(18),r=w-dp(18),t=dp(125),b=h-dp(92),by=b-dp(54);if(x>l+dp(18)&&x<r-dp(18)&&y>by&&y<by+dp(42)){if(favorites.contains(selected.sym))favorites.remove(selected.sym);else favorites.add(selected.sym);invalidate();return true;}if(x<l||x>r||y<t||y>b){selected=null;invalidate();}return true;}
        if(y>h-dp(70)){tab=Math.min(2,(int)(x/(w/3)));invalidate();return true;}
        if(tab==0){for(Stock s:stocks){float dx=x-s.x,dy=y-s.y;if(dx*dx+dy*dy<dp(31)*dp(31)){selected=s;invalidate();return true;}}}
        return true;
    }

    private void rounded(Canvas c,float l,float t,float r,float b,float rad,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(l,t,r,b,rad,rad,p);}
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    static class Stock{String sym;float price,change,volume,phase,x,y;float[] history;}
    static class Rain{float x,y,speed;int len;Rain(float X,float Y,float S,int L){x=X;y=Y;speed=S;len=L;}}
}
