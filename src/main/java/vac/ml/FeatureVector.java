package vac.ml;

import java.util.UUID;

public class FeatureVector {

    public final UUID playerUuid;
    public final long timestamp;
    public final double cps;
    public final double reach;
    public final double aimDeviation;
    public final double yawDelta;
    public final double pitchDelta;
    public final double wallHitRatio;
    public final double aimAngleStddev;
    public final double speed;
    public final double jumpRate;
    public final String label;

    public FeatureVector(UUID playerUuid, long timestamp, double cps, double reach,
                         double aimDeviation, double yawDelta, double pitchDelta,
                         double wallHitRatio, double aimAngleStddev, double speed,
                         double jumpRate, String label) {
        this.playerUuid = playerUuid;
        this.timestamp = timestamp;
        this.cps = cps;
        this.reach = reach;
        this.aimDeviation = aimDeviation;
        this.yawDelta = yawDelta;
        this.pitchDelta = pitchDelta;
        this.wallHitRatio = wallHitRatio;
        this.aimAngleStddev = aimAngleStddev;
        this.speed = speed;
        this.jumpRate = jumpRate;
        this.label = label;
    }

    public double[] toArray() {
        return new double[]{
            cps, reach, aimDeviation, yawDelta, pitchDelta,
            wallHitRatio, aimAngleStddev, speed, jumpRate
        };
    }

    public static int featureCount() {
        return 9;
    }

    public String toJson() {
        return "{\"uuid\":\"" + playerUuid + "\",\"t\":" + timestamp
            + ",\"f\":[" + cps + "," + reach + "," + aimDeviation + ","
            + yawDelta + "," + pitchDelta + "," + wallHitRatio + ","
            + aimAngleStddev + "," + speed + "," + jumpRate + "]"
            + ",\"l\":\"" + (label != null ? label : "UNKNOWN") + "\"}";
    }
}
