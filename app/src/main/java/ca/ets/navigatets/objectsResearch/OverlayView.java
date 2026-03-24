package ca.ets.navigatets.objectsResearch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ca.ets.navigatets.objectsDetection.DetectionResult;

/**
 * @author ank-tech
 */

public class OverlayView extends View {

    private final Paint textPaint;
    private final Paint linePaint;
    private final Paint textBgPaint;

    private final List<DetectionResult> detectionResults = new ArrayList<>();

    // Dimensions of the image passed to the model (e.g., rotatedBitmap)
    private float modelInputWidth = 384f;
    private float modelInputHeight = 384f;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        textPaint = new Paint();
        textPaint.setColor(Color.YELLOW);
        textPaint.setTextSize(60f);
        textPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint();
        linePaint.setColor(Color.RED);
        linePaint.setStrokeWidth(8f);

        textBgPaint = new Paint();
        textBgPaint.setColor(Color.argb(160, 0, 0, 0));
        textBgPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Set the actual dimensions of the image passed to the model.
     * This should be the size of the rotated bitmap.
     */
    public void setModelInputImageSize(float width, float height) {
        this.modelInputWidth = width;
        this.modelInputHeight = height;
    }

    /**
     * Set the detection results and trigger re-draw.
     */
    public void setDetectionResults(List<DetectionResult> results) {
        detectionResults.clear();
        detectionResults.addAll(results);
        // Request redraw
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Get the dimensions of the view (what is displayed on screen)
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        // Calculate the scaling factors to map from original image space → view space
        float scaleXToView = viewWidth / modelInputWidth;
        float scaleYToView = viewHeight / modelInputHeight;

        // Draw each detection box (already in original image coordinates)
        for (DetectionResult detectionResult : detectionResults) {
            RectF box = detectionResult.getBoundingBox();

            // Scale bounding box coordinates to the view size
            float left = box.left * scaleXToView;
            float top = box.top * scaleYToView;
            float right = box.right * scaleXToView;
            float bottom = box.bottom * scaleYToView;

            // Draw the bounding box lines
            canvas.drawLine(left, top, right, top, linePaint);
            canvas.drawLine(left, top, left, bottom, linePaint);
            canvas.drawLine(right, top, right, bottom, linePaint);
            canvas.drawLine(left, bottom, right, bottom, linePaint);

            String label = detectionResult.getLabel();
            float confidence = detectionResult.getConfidence();
            if (!label.isEmpty()) {
                String text = label + " (" + String.format(Locale.ENGLISH, "%.1f", confidence * 100) + "%)";
                float textWidth = textPaint.measureText(text);
                float textHeight = textPaint.getTextSize();
                float padding = 8f;
                float bgLeft = left;
                float bgBottom = top - 10;
                float bgTop = bgBottom - textHeight - padding * 2;
                float bgRight = bgLeft + textWidth + padding * 2;
                canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, textBgPaint);
                canvas.drawText(text, bgLeft + padding, bgBottom - padding, textPaint);
            }
        }
    }

    /**
     * Clear all detections and trigger redraw.
     */
    public void clearDetections() {
        detectionResults.clear();
        postInvalidate();
    }
}




