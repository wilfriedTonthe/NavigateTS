package ca.ets.navigatets.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import ca.ets.navigatets.R;

/**
 * @author ank-tech
 */
public class ObjectDetectionUtilities {
    public static Bitmap convertImageToBitmap (ImageProxy imageProxy) {
        byte[] nv21 = getBytes(imageProxy);

        // Conversion du format NV21 en Bitmap
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, imageProxy.getWidth(), imageProxy.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()), 100, out);
        byte[] jpegBytes = out.toByteArray();

        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    private static byte [] getBytes(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // Récolte des données Y, U et V dans un tableau NV21
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);
        return nv21;
    }
    public static void showContinueDialog (Context context,String title,String message,Runnable onYes, Runnable onNo) {
        new AlertDialog.Builder(context)
                .setCancelable(false)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("YES", (dialog,which)->onYes.run())
                .setNegativeButton("NO",(dialog,which)->onNo.run())
                .show();

    }
}
