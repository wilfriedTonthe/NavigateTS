package ca.ets.navigatets.utils;

/**
 * @author ank-tech
 */
public class DistanceEstimator {
    private final double focalLengthPixels;

    public DistanceEstimator(double focalLengthPixels) {
        this.focalLengthPixels = focalLengthPixels;
    }

    public Double estimateDistance(double realObjectSize,double objectSizeInPixels) {
        return (realObjectSize * focalLengthPixels) /
                (objectSizeInPixels*ObjectDetectionManager.modelInputWidth);
    }
}

