package com.semaatlasi.app;

public final class PointingSolver {
    public static final class Pose {
        public final double heading;
        public final double altitude;
        public final double[] right;
        public final double[] up;
        public final double[] forward;

        Pose(double heading, double altitude, double[] right, double[] up, double[] forward) {
            this.heading = heading;
            this.altitude = altitude;
            this.right = right;
            this.up = up;
            this.forward = forward;
        }
    }

    private double yawOffsetDeg;
    private double altitudeOffsetDeg;

    public PointingSolver(double yawOffsetDeg, double altitudeOffsetDeg) {
        this.yawOffsetDeg = yawOffsetDeg;
        this.altitudeOffsetDeg = altitudeOffsetDeg;
    }

    public double getYawOffsetDeg() { return yawOffsetDeg; }
    public double getAltitudeOffsetDeg() { return altitudeOffsetDeg; }

    public void setOffsets(double yaw, double alt) {
        yawOffsetDeg = clamp(yaw, -45, 45);
        altitudeOffsetDeg = clamp(alt, -30, 30);
    }

    public void reset() {
        yawOffsetDeg = 0;
        altitudeOffsetDeg = 0;
    }

    public void calibrateTo(double targetAz, double targetAlt, double currentHeading, double currentAlt) {
        yawOffsetDeg = clamp(yawOffsetDeg + Astronomy.norm180(targetAz - currentHeading), -45, 45);
        altitudeOffsetDeg = clamp(altitudeOffsetDeg + (targetAlt - currentAlt), -30, 30);
    }

    public Pose solve(float[] rotationMatrix, double magneticDeclinationDeg) {
        // Android rotation matrix maps device axes into magnetic East/North/Up.
        // Back camera optical axis is device -Z.
        double[] rightMag = normalize(new double[]{rotationMatrix[0], rotationMatrix[3], rotationMatrix[6]});
        double[] upMag = normalize(new double[]{rotationMatrix[1], rotationMatrix[4], rotationMatrix[7]});
        double[] forwardMag = normalize(new double[]{-rotationMatrix[2], -rotationMatrix[5], -rotationMatrix[8]});

        double[] right = magneticToTrue(rightMag, magneticDeclinationDeg);
        double[] up = magneticToTrue(upMag, magneticDeclinationDeg);
        double[] forward = magneticToTrue(forwardMag, magneticDeclinationDeg);

        double rawHeading = Astronomy.norm360(Math.toDegrees(Math.atan2(forward[0], forward[1])));
        double rawAlt = Math.toDegrees(Math.asin(clamp(forward[2], -1, 1)));

        double hRad = Math.toRadians(rawHeading);
        double aRad = Math.toRadians(rawAlt);
        double[] zeroRight = normalize(new double[]{Math.cos(hRad), -Math.sin(hRad), 0});
        double[] zeroUp = normalize(new double[]{
                -Math.sin(aRad) * Math.sin(hRad),
                -Math.sin(aRad) * Math.cos(hRad),
                Math.cos(aRad)
        });
        double roll = Math.atan2(dot(right, zeroUp), dot(right, zeroRight));

        double heading = Astronomy.norm360(rawHeading + yawOffsetDeg);
        double altitude = clamp(rawAlt + altitudeOffsetDeg, -89.8, 89.8);
        double H = Math.toRadians(heading);
        double A = Math.toRadians(altitude);

        double[] correctedForward = normalize(new double[]{
                Math.cos(A) * Math.sin(H),
                Math.cos(A) * Math.cos(H),
                Math.sin(A)
        });
        double[] correctedZeroRight = normalize(new double[]{Math.cos(H), -Math.sin(H), 0});
        double[] correctedZeroUp = normalize(new double[]{
                -Math.sin(A) * Math.sin(H),
                -Math.sin(A) * Math.cos(H),
                Math.cos(A)
        });

        double cr = Math.cos(roll), sr = Math.sin(roll);
        double[] correctedRight = normalize(new double[]{
                cr * correctedZeroRight[0] + sr * correctedZeroUp[0],
                cr * correctedZeroRight[1] + sr * correctedZeroUp[1],
                cr * correctedZeroRight[2] + sr * correctedZeroUp[2]
        });
        double[] correctedUp = normalize(cross(correctedRight, correctedForward));

        return new Pose(heading, altitude, correctedRight, correctedUp, correctedForward);
    }

    private static double[] magneticToTrue(double[] v, double declinationDeg) {
        double d = Math.toRadians(declinationDeg);
        double cd = Math.cos(d), sd = Math.sin(d);
        return normalize(new double[]{
                cd * v[0] + sd * v[1],
                -sd * v[0] + cd * v[1],
                v[2]
        });
    }

    private static double dot(double[] a, double[] b) {
        return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1]*b[2]-a[2]*b[1],
                a[2]*b[0]-a[0]*b[2],
                a[0]*b[1]-a[1]*b[0]
        };
    }

    private static double[] normalize(double[] v) {
        double n = Math.sqrt(dot(v,v));
        if (n < 1e-9) return new double[]{0,0,0};
        return new double[]{v[0]/n,v[1]/n,v[2]/n};
    }

    private static double clamp(double x, double a, double b) {
        return Math.max(a, Math.min(b, x));
    }
}
