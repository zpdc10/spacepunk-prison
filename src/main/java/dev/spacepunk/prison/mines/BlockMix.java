package dev.spacepunk.prison.mines;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Weighted block composition of a mine. Immutable and thread-safe. */
public final class BlockMix {
    private final Material[] materials;
    private final double[] cumulative;
    private final Map<Material, Double> weights;

    public BlockMix(Map<Material, Double> weights) {
        Map<Material, Double> clean = new LinkedHashMap<>();
        weights.forEach((m, w) -> { if (m != null && m.isBlock() && w != null && w > 0) clean.merge(m, w, Double::sum); });
        if (clean.isEmpty()) clean.put(Material.STONE, 1.0);
        this.weights = Collections.unmodifiableMap(clean);
        this.materials = clean.keySet().toArray(new Material[0]);
        this.cumulative = new double[materials.length];
        double sum = 0;
        for (int i = 0; i < materials.length; i++) {
            sum += clean.get(materials[i]);
            cumulative[i] = sum;
        }
    }

    public Material pick(Random random) {
        double r = random.nextDouble() * cumulative[cumulative.length - 1];
        int lo = 0, hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] > r) hi = mid; else lo = mid + 1;
        }
        return materials[lo];
    }

    /** Raw weights (not normalised), in config order. */
    public Map<Material, Double> weights() { return weights; }

    public double total() { return cumulative[cumulative.length - 1]; }
}
