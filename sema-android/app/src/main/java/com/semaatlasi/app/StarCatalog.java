package com.semaatlasi.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StarCatalog {
    private StarCatalog() {}

    public static List<SkyObject> load(Context context) {
        List<SkyObject> result = read(context, "stars.dat");
        if (result.size() < 100) {
            result = read(context, "fallback_stars.dat");
        }
        result.sort(Comparator.comparingDouble(o -> o.mag));
        return result;
    }

    private static List<SkyObject> read(Context context, String asset) {
        List<SkyObject> list = new ArrayList<>();
        try (InputStream in = context.getAssets().open(asset);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split("\t", -1);
                if (p.length < 8) continue;
                try {
                    double x=p.length>8 && !p[8].isEmpty()?Double.parseDouble(p[8]):Double.NaN;
                    double y=p.length>9 && !p[9].isEmpty()?Double.parseDouble(p[9]):Double.NaN;
                    double z=p.length>10 && !p[10].isEmpty()?Double.parseDouble(p[10]):Double.NaN;
                    double vx=p.length>11 && !p[11].isEmpty()?Double.parseDouble(p[11]):Double.NaN;
                    double vy=p.length>12 && !p[12].isEmpty()?Double.parseDouble(p[12]):Double.NaN;
                    double vz=p.length>13 && !p[13].isEmpty()?Double.parseDouble(p[13]):Double.NaN;
                    list.add(new SkyObject(
                            p[0], p[1], p[2], p[3],
                            Double.parseDouble(p[4]),
                            Double.parseDouble(p[5]),
                            Double.parseDouble(p[6]),
                            p[7].isEmpty() ? Double.NaN : Double.parseDouble(p[7]),
                            "star",x,y,z,vx,vy,vz
                    ));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception ignored) {}
        return list;
    }
}
