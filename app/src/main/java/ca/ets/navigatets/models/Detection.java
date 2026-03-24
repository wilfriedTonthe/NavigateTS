package ca.ets.navigatets.models;

import android.graphics.RectF;

/**
 * @author ank-tech
 */
public class Detection {
    private final String label;
    private final float confidence;
    private final RectF boundingBox;

    public Detection(String label, float confidence, RectF boundingBox) {
        this.label = label;
        this.confidence = confidence;
        this.boundingBox = new RectF(boundingBox);
    }

    public String label() { return label; }
    public float confidence() { return confidence; }
    public RectF boundingBox() { return new RectF(boundingBox); }
}
