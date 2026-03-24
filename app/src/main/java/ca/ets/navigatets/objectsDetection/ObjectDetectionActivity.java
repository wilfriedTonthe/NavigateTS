package ca.ets.navigatets.objectsDetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.ets.navigatets.R;
import ca.ets.navigatets.models.Detection;
import ca.ets.navigatets.utils.CameraFrameAnalyzer;
import ca.ets.navigatets.utils.ObjectDetectionManager;
import ca.ets.navigatets.utils.ObjectDetectionUtilities;
import ca.ets.navigatets.utils.ToSpeech;

/**
 * @author ank-tech
 */

public class ObjectDetectionActivity extends AppCompatActivity {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private ToSpeech toSpeech;
    private ExecutorService cameraExecutor;
    private RecyclerView recyclerView;
    private boolean isReading;
    _ItemAdapter objectDetectedAdapter;
    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private static final long ANALYSIS_DURATION_MS = 15_000;
    private long analysisStartTime = 0;
    private boolean isAnalyzing = true;
    Button toggleDetectionButton;
    private CameraFrameAnalyzer cameraFrameAnalyzer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_object_detection);
        // Initialize UI elements
        previewView = findViewById(R.id.preview_view);
        toggleDetectionButton = findViewById(R.id.toggleDetectionButton);
        Button readDetectedObjectButton = findViewById(R.id.readDetectedObjectList);
        readDetectedObjectButton.setVisibility(View.GONE);
        // Executor for background camera processing
        cameraExecutor = Executors.newSingleThreadExecutor();
        toSpeech = new ToSpeech(this);
        isReading = false;

        initializeRecyclerView();
        // read detected object list action
        setReadDetectedObjectButtonAction(readDetectedObjectButton);
        // toggle detection button listener
        setToggleButtonAction(toggleDetectionButton, readDetectedObjectButton);
        // Initialize ObjectDetection
        initializeObjectDetection();
        // Request camera permissions and start camera
        getCameraPermissions();
    }

    private void initializeObjectDetection() {
        ObjectDetectionManager objectDetectionManager = new ObjectDetectionManager(this);
        float MIN_SCORE = 0.5f;
        cameraFrameAnalyzer =  new CameraFrameAnalyzer(MIN_SCORE,objectDetectionManager,(detections, rotatedBitmap) ->{
            // Run object detection on the bitmap
            for(Detection detection : detections){
                String label = detection.label();
                String result = label.substring(0, 1).toUpperCase() + label.substring(1);
                runOnUiThread(() -> {
                    updateRecyclerViewVisibility();
                    new Handler(Looper.getMainLooper()).postDelayed(()->{
                        if(objectDetectedAdapter.addNewItemOnList(result, 0))
                            recyclerView.smoothScrollToPosition(0);
                    }, 2000);
                });
            }
        });
    }

    private void initializeRecyclerView() {
        recyclerView = findViewById(R.id.objectDetectedView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        DividerItemDecoration decoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.customer_divider);
        assert dividerDrawable != null;
        decoration.setDrawable(dividerDrawable);
        recyclerView.addItemDecoration(decoration);

        List<String> a = new ArrayList<>();
        objectDetectedAdapter = new _ItemAdapter(a);
        recyclerView.setAdapter(objectDetectedAdapter);

        updateRecyclerViewVisibility();
    }

    private void updateRecyclerViewVisibility() {
        if(objectDetectedAdapter.getItemCount() == 0){
            recyclerView.setVisibility(View.GONE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void setReadDetectedObjectButtonAction(Button readDetectedobjectbutton) {
        readDetectedobjectbutton.setOnClickListener(v->{
            isReading = !isReading;
            if(!isReading) {
                stopReading(readDetectedobjectbutton);
                return;
            }
            Handler scrollHandler = new Handler(Looper.getMainLooper());
            Runnable scrollRunnable = new Runnable() {
                final int [] currentIndex =  {0};
                @Override
                public void run() {
                    if(currentIndex[0] >= objectDetectedAdapter.getItemCount() || !isReading){
                        stopReading(readDetectedobjectbutton);
                        return;
                    }
                    recyclerView.smoothScrollToPosition(currentIndex[0]);
                    String item = objectDetectedAdapter.getItem(currentIndex[0]);
                    toSpeech.speakObject(item);
                    scrollHandler.postDelayed(this,2000);
                    currentIndex[0]++;
                }
            };
            isReading = true;
            readDetectedobjectbutton.setText(R.string.stop_reading);
            scrollHandler.postDelayed(scrollRunnable,1000);
        });
    }

    private void stopReading(Button readDetectedobjectbutton) {
        recyclerView.smoothScrollToPosition(0);
        readDetectedobjectbutton.setText(R.string.read_list);
        toSpeech.getTextToSpeech().stop();
        isReading = false;
    }

    private void setToggleButtonAction(Button toggleDetectionButton, Button readDetectedObjectButton) {
        toggleDetectionButton.setOnClickListener( v-> {
            if(isAnalyzing){
                if(objectDetectedAdapter.getItemCount() != 0){
                    isReading = false;
                    isAnalyzing = false;
                    toggleDetectionButton.setText(R.string.start_detection);
                    readDetectedObjectButton.setVisibility(View.VISIBLE);
                    readDetectedObjectButton.callOnClick();
                } else {
                    finish();
                }
            } else {
                toggleDetectionButton.setText(R.string.stop_detection);
                readDetectedObjectButton.setVisibility(View.GONE);
                objectDetectedAdapter.clearList();
                startCamera();
            }
        });
    }

    private void getCameraPermissions() {
        if (allPermissionsGranted()) {
            startCamera();
            Log.i("THIS","Camera Started");
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            );
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                analysisStartTime = System.currentTimeMillis();
                isAnalyzing = true;
                // Image analysis
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        // ... (Configure image analysis settings if needed) ...
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy ->{
                    if(!isAnalyzing){
                        imageProxy.close();
                        return;
                    }
                    long elapse = System.currentTimeMillis() - analysisStartTime;
                    String title = "Objet Research 🔎🔎🔎️‍️";
                    String message = "Do you want to stop the detection ?";
                    if (elapse >= ANALYSIS_DURATION_MS) {
                        isAnalyzing = false;
                        runOnUiThread(()-> ObjectDetectionUtilities
                                .showContinueDialog(this,title,message, ()->{
                                    if(objectDetectedAdapter.getItemCount() == 0){
                                        finish();
                                    } else {
                                        imageProxy.close();
                                        isAnalyzing = true;
                                        toggleDetectionButton.callOnClick();
                                    }
                                },()->{
                                    analysisStartTime = System.currentTimeMillis();
                                    isAnalyzing = true;}));
                        imageProxy.close();
                        return;
                    }
                    cameraFrameAnalyzer.runObjectDetection(imageProxy);
                    imageProxy.close();
                });

                // Select back camera as a default
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis
                );
            } catch (Exception exc) {
                Log.e(TAG, "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this, permission
            ) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(
                        this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT
                ).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();

        // Release Text-To-Speech Resources
        if(toSpeech != null){
            toSpeech.destroy();
        }
    }
}
