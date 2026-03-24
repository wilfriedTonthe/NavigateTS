package ca.ets.navigatets.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import androidx.camera.core.ImageProxy;

import java.util.List;

import ca.ets.navigatets.models.Detection;

/**
 * @author ank-tech
 */
public class CameraFrameAnalyzer {
    private final float confidenceThreshold;
    private final ObjectDetectionManager objectDetectionManager;
    private final OnObjectDetectionResultsListener detectionResultsListener;
    private int frameSkipCounter = 0;

    public interface OnObjectDetectionResultsListener{
        void onResult(List<Detection> detections,Bitmap rotatedBitmap);
    }

    public CameraFrameAnalyzer(float confidenceThreshold,
                               ObjectDetectionManager objectDetectionManager,
                               OnObjectDetectionResultsListener detectionResultsListener) {
        this.confidenceThreshold = confidenceThreshold;
        this.objectDetectionManager = objectDetectionManager;
        this.detectionResultsListener = detectionResultsListener;
    }

    public void runObjectDetection(ImageProxy imageProxy) {
        if (frameSkipCounter % 60 == 0) {
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            Matrix matrix = new Matrix();
            matrix.postRotate((float) rotationDegrees);

            Bitmap sourceBitmap = getBitmapFromImageProxy(imageProxy);
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    sourceBitmap,
                    0, 0,
                    imageProxy.getWidth(), imageProxy.getHeight(),
                    matrix,
                    true
            );

            List<Detection> results = objectDetectionManager.detectObjectsInCurrentFrame(
                            rotatedBitmap,
                            confidenceThreshold
                    );

            detectionResultsListener.onResult(results,rotatedBitmap);
        }

        frameSkipCounter++;
        imageProxy.close();
    }
    private Bitmap getBitmapFromImageProxy (ImageProxy imageProxy) {
       return ObjectDetectionUtilities.convertImageToBitmap(imageProxy);
    }
}
