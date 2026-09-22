package com.matrixevreni.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean pageReady = false;

    private static final long REFRESH_MS = 30_000L;
    private static final String[] SYMBOLS = new String[]{
            "XU030.IS",
            "AKBNK.IS","ASELS.IS","ASTOR.IS","BIMAS.IS","DSTKF.IS","EKGYO.IS","ENKAI.IS","EREGL.IS",
            "FROTO.IS","GARAN.IS","GUBRF.IS","ISCTR.IS","KCHOL.IS","KRDMD.IS","MGROS.IS","PETKM.IS",
            "PGSUS.IS","SAHOL.IS","SASA.IS","SISE.IS","TAVHL.IS","TCELL.IS","THYAO.IS","TOASO.IS",
            "TRALT.IS","TTKOM.IS","TUPRS.IS","VAKBN.IS","YKBNK.IS","AEFES.IS","TRMET.IS"
    };

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (pageReady) refreshMarketData();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(2, 7, 4));
        getWindow().setNavigationBarColor(Color.rgb(2, 7, 4));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(2, 7, 4));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.addJavascriptInterface(new MarketBridge(), "AndroidMarket");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                refreshMarketData();
            }
        });

        setContentView(webView);

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html");
        } else {
            webView.restoreState(savedInstanceState);
        }

        handler.postDelayed(poller, REFRESH_MS);
    }

    public class MarketBridge {
        @JavascriptInterface
        public void refresh() {
            refreshMarketData();
        }

        @JavascriptInterface
        public String appVersion() {
            return "2.0.0";
        }
    }

    private void refreshMarketData() {
        executor.execute(() -> {
            try {
                JSONObject payload = fetchYahooQuotes();
                payload.put("source", "Yahoo Finance");
                payload.put("delayed", true);
                payload.put("fetchedAt", System.currentTimeMillis());
                runJs("window.onMarketData && window.onMarketData(" + payload.toString() + ");");
            } catch (Exception e) {
                JSONObject err = new JSONObject();
                try {
                    err.put("message", e.getMessage() == null ? "Veri bağlantısı kurulamadı" : e.getMessage());
                    err.put("at", System.currentTimeMillis());
                } catch (Exception ignored) {}
                runJs("window.onMarketError && window.onMarketError(" + err.toString() + ");");
            }
        });
    }

    private JSONObject fetchYahooQuotes() throws Exception {
        StringBuilder symbolList = new StringBuilder();
        for (int i = 0; i < SYMBOLS.length; i++) {
            if (i > 0) symbolList.append(",");
            symbolList.append(SYMBOLS[i]);
        }

        String endpoint = "https://query1.finance.yahoo.com/v7/finance/quote?symbols=" + symbolList;
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
        connection.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.7");
        connection.setUseCaches(false);

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = readAll(stream);
        connection.disconnect();

        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Piyasa servisi HTTP " + code);
        }

        JSONObject root = new JSONObject(body);
        JSONObject quoteResponse = root.optJSONObject("quoteResponse");
        if (quoteResponse == null) throw new IllegalStateException("Piyasa cevabı okunamadı");

        JSONArray result = quoteResponse.optJSONArray("result");
        if (result == null || result.length() == 0) {
            throw new IllegalStateException("Piyasa verisi boş döndü");
        }

        JSONArray clean = new JSONArray();
        for (int i = 0; i < result.length(); i++) {
            JSONObject q = result.optJSONObject(i);
            if (q == null) continue;

            String rawSymbol = q.optString("symbol", "");
            String symbol = rawSymbol.endsWith(".IS")
                    ? rawSymbol.substring(0, rawSymbol.length() - 3)
                    : rawSymbol;

            JSONObject item = new JSONObject();
            item.put("symbol", symbol);
            item.put("price", numberOrNull(q, "regularMarketPrice"));
            item.put("changePercent", numberOrNull(q, "regularMarketChangePercent"));
            item.put("change", numberOrNull(q, "regularMarketChange"));
            item.put("previousClose", numberOrNull(q, "regularMarketPreviousClose"));
            item.put("open", numberOrNull(q, "regularMarketOpen"));
            item.put("dayHigh", numberOrNull(q, "regularMarketDayHigh"));
            item.put("dayLow", numberOrNull(q, "regularMarketDayLow"));
            item.put("volume", numberOrNull(q, "regularMarketVolume"));
            item.put("marketTime", q.optLong("regularMarketTime", 0));
            item.put("marketState", q.optString("marketState", ""));
            item.put("currency", q.optString("currency", "TRY"));
            clean.put(item);
        }

        JSONObject payload = new JSONObject();
        payload.put("quotes", clean);
        payload.put("count", clean.length());
        return payload;
    }

    private Object numberOrNull(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) return JSONObject.NULL;
        Object value = object.opt(key);
        return value instanceof Number ? value : JSONObject.NULL;
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void runJs(String js) {
        handler.post(() -> {
            if (webView != null && pageReady) webView.evaluateJavascript(js, null);
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
