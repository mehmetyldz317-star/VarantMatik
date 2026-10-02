package com.semaatlasi.app;

import java.util.ArrayList;
import java.util.List;

import io.github.cosinekitty.astronomy.Aberration;
import io.github.cosinekitty.astronomy.Body;
import io.github.cosinekitty.astronomy.EquatorEpoch;
import io.github.cosinekitty.astronomy.Equatorial;
import io.github.cosinekitty.astronomy.Observer;
import io.github.cosinekitty.astronomy.Refraction;
import io.github.cosinekitty.astronomy.Time;
import io.github.cosinekitty.astronomy.Topocentric;

public final class AstroEngineBridge {
    private AstroEngineBridge() {}

    private static final Body[] BODIES = {
            Body.Mercury, Body.Venus, Body.Mars, Body.Jupiter,
            Body.Saturn, Body.Uranus, Body.Neptune, Body.Moon
    };

    public static List<SkyObject> solarSystem(long timeMs, double lat, double lon, double heightMeters) {
        List<SkyObject> out = new ArrayList<>();
        Time time = Time.fromMillisecondsSince1970(timeMs);
        Observer observer = new Observer(lat, lon, heightMeters);

        for (Body body : BODIES) {
            Equatorial eqDate = io.github.cosinekitty.astronomy.Astronomy.equator(
                    body, time, observer, EquatorEpoch.OfDate, Aberration.Corrected);
            Topocentric hor = io.github.cosinekitty.astronomy.Astronomy.horizon(
                    time, observer, eqDate.getRa(), eqDate.getDec(), Refraction.Normal);

            String name;
            double mag;
            String type = body == Body.Moon ? "moon" : "planet";
            switch (body) {
                case Mercury -> { name = "Merkür"; mag = -0.4; }
                case Venus -> { name = "Venüs"; mag = -4.2; }
                case Mars -> { name = "Mars"; mag = -1.5; }
                case Jupiter -> { name = "Jüpiter"; mag = -2.4; }
                case Saturn -> { name = "Satürn"; mag = 0.7; }
                case Uranus -> { name = "Uranüs"; mag = 5.7; }
                case Neptune -> { name = "Neptün"; mag = 7.8; }
                case Moon -> { name = "Ay"; mag = -10.0; }
                default -> { name = body.toString(); mag = 2.0; }
            }

            double dist = body == Body.Moon ? 384400.0 : Double.NaN;
            SkyObject o = new SkyObject("ae-" + body, name, "", body == Body.Moon ? "Dünya'nın uydusu" : "Gezegen",
                    eqDate.getRa(), eqDate.getDec(), mag, dist, type);
            o.az = hor.getAzimuth();
            o.alt = hor.getAltitude();
            out.add(o);
        }
        return out;
    }
}
