package vac.ml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OnlineGaussianClassifier {

    private final Map<String, ClassStats> classes = new ConcurrentHashMap<>();
    private int totalSamples;

    private static final double PRIOR_SMOOTH = 1.0;
    private static final double VARIANCE_OFFSET = 0.01;

    public OnlineGaussianClassifier() {
        classes.put("LEGIT", new ClassStats(FeatureVector.featureCount()));
        classes.put("CHEAT", new ClassStats(FeatureVector.featureCount()));
    }

    public void train(FeatureVector fv) {
        if (fv.label == null || fv.label.equals("UNKNOWN")) return;
        ClassStats cs = classes.get(fv.label.toUpperCase());
        if (cs == null) return;

        double[] features = fv.toArray();
        synchronized (cs) {
            cs.count++;
            for (int i = 0; i < features.length; i++) {
                double oldMean = cs.means[i];
                cs.means[i] += (features[i] - oldMean) / cs.count;
                if (cs.count > 1) {
                    cs.varSum[i] += (features[i] - oldMean) * (features[i] - cs.means[i]);
                }
            }
        }
        totalSamples++;
    }

    public double predict(FeatureVector fv) {
        double[] features = fv.toArray();
        ClassStats legit = classes.get("LEGIT");
        ClassStats cheat = classes.get("CHEAT");

        double logLikelyLegit = logLikelihood(features, legit);
        double logLikelyCheat = logLikelihood(features, cheat);

        double prior = (double) (cheat.count + PRIOR_SMOOTH)
                / (legit.count + cheat.count + 2 * PRIOR_SMOOTH);

        double logPosteriorCheat = logLikelyCheat + Math.log(prior);
        double logPosteriorLegit = logLikelyLegit + Math.log(1 - prior);

        double max = Math.max(logPosteriorCheat, logPosteriorLegit);
        double pCheat = Math.exp(logPosteriorCheat - max);
        double pLegit = Math.exp(logPosteriorLegit - max);

        return pCheat / (pCheat + pLegit);
    }

    private double logLikelihood(double[] features, ClassStats cs) {
        double logLik = 0;
        for (int i = 0; i < features.length; i++) {
            double var = cs.count > 1
                    ? cs.varSum[i] / (cs.count - 1) + VARIANCE_OFFSET
                    : VARIANCE_OFFSET;
            double diff = features[i] - cs.means[i];
            logLik += -0.5 * Math.log(2 * Math.PI * var) - (diff * diff) / (2 * var);
        }
        return logLik;
    }

    public int getSampleCount(String label) {
        ClassStats cs = classes.get(label.toUpperCase());
        return cs != null ? cs.count : 0;
    }

    public int getTotalSamples() {
        return totalSamples;
    }

    public boolean isReady() {
        return getSampleCount("LEGIT") >= 5 && getSampleCount("CHEAT") >= 5;
    }

    public void save(File file) {
        StringBuilder json = new StringBuilder();
        json.append("{\"classes\":{");
        boolean first = true;
        for (Map.Entry<String, ClassStats> e : classes.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(e.getKey()).append("\":").append(e.getValue().toJson());
        }
        json.append("}}");
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(json.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load(File file) {
        if (!file.exists()) return;
        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) content.append(line);
            }
            String json = content.toString();
            for (String label : new String[]{"LEGIT", "CHEAT"}) {
                int idx = json.indexOf("\"" + label + "\":{");
                if (idx < 0) continue;
                idx = json.indexOf('{', idx);
                int end = findMatchingBrace(json, idx);
                if (end < 0) continue;
                String classJson = json.substring(idx, end + 1);
                ClassStats cs = classes.get(label);
                if (cs != null) cs.fromJson(classJson);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int findMatchingBrace(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static class ClassStats {
        int count;
        final double[] means;
        final double[] varSum;

        ClassStats(int featureCount) {
            this.means = new double[featureCount];
            this.varSum = new double[featureCount];
            this.count = 0;
        }

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"c\":").append(count);
            sb.append(",\"m\":[");
            for (int i = 0; i < means.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(means[i]);
            }
            sb.append("],\"v\":[");
            for (int i = 0; i < varSum.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(varSum[i]);
            }
            sb.append("]}");
            return sb.toString();
        }

        void fromJson(String json) {
            count = getInt(json, "\"c\":");
            double[] m = getArray(json, "\"m\":");
            double[] v = getArray(json, "\"v\":");
            if (m != null) System.arraycopy(m, 0, means, 0, Math.min(m.length, means.length));
            if (v != null) System.arraycopy(v, 0, varSum, 0, Math.min(v.length, varSum.length));
        }

        private int getInt(String json, String key) {
            int idx = json.indexOf(key);
            if (idx < 0) return 0;
            idx += key.length();
            int end = json.indexOf(',', idx);
            if (end < 0) end = json.indexOf('}', idx);
            if (end < 0) return 0;
            return Integer.parseInt(json.substring(idx, end).trim());
        }

        private double[] getArray(String json, String key) {
            int idx = json.indexOf(key);
            if (idx < 0) return null;
            idx += key.length();
            if (json.charAt(idx) == '[') idx++;
            int end = json.indexOf(']', idx);
            if (end < 0) return null;
            String[] parts = json.substring(idx, end).split(",");
            double[] result = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Double.parseDouble(parts[i].trim());
            }
            return result;
        }
    }
}
