package ca.ets.navigatets.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ank-tech
 */
public class ObjectDetectionManager {
    public static final int modelInputWidth = 640;
    public static final int modelInputHeight = 640;
    private final ObjectDetector objectDetector;

    public ObjectDetectionManager(Context context) {
        objectDetector = setupObjectDetector(context, "__model.tflite");
    }

    public ObjectDetectionManager(Context context, String modelAssetFileName) {
        objectDetector = setupObjectDetector(context, modelAssetFileName);
    }

    private ObjectDetector setupObjectDetector(Context context, String modelAssetFileName) {
      ObjectDetectorOptions options =
                ObjectDetectorOptions.builder()
                        .setMaxResults(5)
                        .setScoreThreshold(0.25f)
                        .build();
      try{
          return ObjectDetector.createFromFileAndOptions(context, modelAssetFileName, options);
      } catch (IOException e) {
          throw new RuntimeException(e);
      }
    }
    public List<ca.ets.navigatets.models.Detection> detectObjectsInCurrentFrame(Bitmap bitmap, float confidenceThreshold) {

        // Save the original image dimensions for later scaling
        int origW = bitmap.getWidth();
        int origH = bitmap.getHeight();


        // Resize the bitmap to the model's expected input size
        // This prevents TensorFlow Lite from doing implicit resizing that could distort coordinates
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputWidth, modelInputHeight, true);

        // Conversion to TensorImage and inference
        TensorImage tensorImage = TensorImage.fromBitmap(resizedBitmap);
        List<Detection> results = objectDetector.detect(tensorImage);

        // Prepare a list to store the final scaled detections
        List<ca.ets.navigatets.models.Detection> detections = new ArrayList<>();

        // Loop through each detection result
        for (Detection tfDet : results) {
            if (tfDet.getCategories().isEmpty()) continue;

            float score = tfDet.getCategories().get(0).getScore();
            // Skip detections below our confidence threshold
            if (score < confidenceThreshold) continue;

            String label = tfDet.getCategories().get(0).getLabel();
            RectF box = tfDet.getBoundingBox();

            // Scale bounding box coordinates back to original image size
            scaleToOriginalImageSize((float) origW, (float) modelInputWidth,(float) modelInputHeight, (float) origH, box);

            detections.add(new ca.ets.navigatets.models.Detection(label, score, box));
        }

        return detections;
    }

    private static void scaleToOriginalImageSize(float origW, float modelInputWidth,float modelInputHeight, float origH, RectF box) {
        float scaleX = origW / modelInputWidth;
        float scaleY = origH / modelInputHeight;

        box.left   = box.left   * scaleX;
        box.right  = box.right  * scaleX;
        box.top    = box.top    * scaleY;
        box.bottom = box.bottom * scaleY;
    }

}
