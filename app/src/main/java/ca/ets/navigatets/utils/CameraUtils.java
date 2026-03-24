package ca.ets.navigatets.utils;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;

import java.util.Objects;

/**
 * Utility class to get camera information.
 * It helps calculate the focal length in pixels,
 * which is useful for estimating object distance.
 */
public class CameraUtils {
    /**
     * This method calculates the camera's focal length in pixels.
     * @param context The application context.
     * @return Focal length in pixels, or -1 if something goes wrong.
     */
    public static float getFocalLengthInPixels(Context context) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIdList = manager.getCameraIdList();

            for (String cameraId : cameraIdList) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // Only consider the back-facing camera
                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing == null || lensFacing != CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                // Get the physical focal length (in millimeters)
                float focalLength = Objects.requireNonNull(characteristics.get(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS))[0];
                Log.i("CameraUtils", "focalLength: "+focalLength);
                // Get the physical sensor size (in millimeters)
                SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);

                // Get available output resolutions (in pixels)
                Size[] outputSizes = Objects.requireNonNull(
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ).getOutputSizes(SurfaceTexture.class);

                if (outputSizes == null || outputSizes.length == 0) {
                    continue;
                }

                // Choose the resolution with the largest width (most precise for pixel-level calculations)
                // Assume the first is the largest initially
                Size bestSize = outputSizes[0];
                for (int i = 1; i < outputSizes.length; i++) {
                    if (outputSizes[i].getWidth() > bestSize.getWidth()) {
                        bestSize = outputSizes[i];
                    }
                }


                int imageWidth = bestSize.getWidth();
                Log.i("CameraUtils", "imageWidth: "+imageWidth);
                float sensorSizeWidth = sensorSize.getWidth();
                Log.i("CameraUtils", "sensorSizeWidth: "+sensorSizeWidth);
                // Convert focal length from mm to pixels:
                return (focalLength * imageWidth) / sensorSizeWidth;
            }

        } catch (CameraAccessException e) {
            Log.e("CameraUtils", "Camera access error: " + e.getMessage());
        }

        // Return -1 if something goes wrong
        return -1f;
    }

}
