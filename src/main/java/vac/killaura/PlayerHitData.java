package vac.killaura;

import java.util.*;

public class PlayerHitData {

    private final List<HitRecord> hits;
    private final List<Long> swings;

    public PlayerHitData() {
        this.hits = new ArrayList<>();
        this.swings = new ArrayList<>();
    }

    public void addHit(long time, double reach, double aimDeviation, boolean throughWall) {
        hits.add(new HitRecord(time, reach, aimDeviation, throughWall));
    }

    public void recordSwing(long time) {
        swings.add(time);
    }

    public void purgeOld(long now, long windowMs) {
        hits.removeIf(h -> now - h.time > windowMs);
        swings.removeIf(t -> now - t > windowMs);
    }

    public int getHitCount() {
        return hits.size();
    }

    public int getSwingCount(long since) {
        int count = 0;
        for (long t : swings) {
            if (t >= since) count++;
        }
        return count;
    }

    public double getAverageReach() {
        return hits.stream().mapToDouble(h -> h.reach).average().orElse(0);
    }

    public double getAverageAimDeviation() {
        return hits.stream().mapToDouble(h -> h.aimDeviation).average().orElse(99);
    }

    public int getThroughWallCount() {
        return (int) hits.stream().filter(h -> h.throughWall).count();
    }

    public double getMaxReach() {
        return hits.stream().mapToDouble(h -> h.reach).max().orElse(0);
    }

    public double getMinAimDeviation() {
        return hits.stream().mapToDouble(h -> h.aimDeviation).min().orElse(99);
    }

    private static class HitRecord {
        final long time;
        final double reach;
        final double aimDeviation;
        final boolean throughWall;

        HitRecord(long time, double reach, double aimDeviation, boolean throughWall) {
            this.time = time;
            this.reach = reach;
            this.aimDeviation = aimDeviation;
            this.throughWall = throughWall;
        }
    }
}
