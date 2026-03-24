package ca.ets.navigatets.objectsDetection;

import android.graphics.RectF;

import java.util.Objects;

/**
 * @author ank-tech
 */
public class DetectionResult {


    public RectF boundingBox;
    public String label;
    public float confidence;

    public DetectionResult(RectF boundingBox, String label, float confidence) {
        this.boundingBox = boundingBox;
        this.label = label;
        this.confidence = confidence;
    }
    public RectF getBoundingBox() {
        return boundingBox;
    }

    public String getLabel() {
        return label;
    }

    public float getConfidence() {
        return confidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DetectionResult that = (DetectionResult) o;
        return Double.compare(that.confidence, confidence) == 0 &&
                Objects.equals(boundingBox, that.boundingBox) &&
                Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boundingBox, label, confidence);
    }
}
