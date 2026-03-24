package ca.ets.navigatets.objectsResearch;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.ets.navigatets.ConstantsKt;
import ca.ets.navigatets.BuildConfig;
import ca.ets.navigatets.R;
import ca.ets.navigatets.objectsDetection.DetectionResult;
import ca.ets.navigatets.utils.CameraFrameAnalyzer;
import ca.ets.navigatets.utils.CameraUtils;
import ca.ets.navigatets.utils.DistanceEstimator;
import ca.ets.navigatets.utils.GetDataFromAssets;
import ca.ets.navigatets.utils.ObjectDetectionManager;
import ca.ets.navigatets.utils.ObjectDetectionUtilities;
import ca.ets.navigatets.utils.ToSpeech;

/**
 * @author ank-tech
 */

public class ObjectResearchActivity extends AppCompatActivity {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    HashMap<String, Double[]> itemsSize;
    private ToSpeech toSpeech;
    private ExecutorService cameraExecutor;

    private String searchedObjet;
    private DistanceEstimator distanceEstimator;
    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    private TextView resultView;
    private OverlayView overlayView;
    private static final long ANALYSIS_DURATION_MS = 30_000;
    private long analysisStartTime = 0;
    private boolean isAnalyzing = true;
    private CameraFrameAnalyzer cameraFrameAnalyzer;
    private android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    private ImageCapture imageCapture;
    private boolean photoCaptured = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_object_research);
        // Initialize UI elements
        previewView = findViewById(R.id.preview_view);
        resultView = findViewById(R.id.result);

        overlayView = findViewById(R.id.overlay_view);

        TextView title = findViewById(R.id.title);
        Button showListButton = findViewById(R.id.showListButton);
        Button toggleDetectionButton = findViewById(R.id.toggleDetectionButton);

        //get the label of the searched object
        searchedObjet = getIntent().getStringExtra(ConstantsKt.SEARCHED_OBJECT);

        itemsSize = GetDataFromAssets.loadDataFromAsset(this);
        Log.i(TAG, "onCreate: ObjectResearchActivity "+itemsSize.size());
        title.setText(String.format("Looking for %s",searchedObjet));

        showListButton.setOnClickListener(e->finish());
        setToggleDetectionButton(toggleDetectionButton);
        // Executor for background camera processing
        cameraExecutor = Executors.newSingleThreadExecutor();
        toSpeech = new ToSpeech(this);
        // get focal automatically and initialize the estimator
        float focalLengthInPixel = CameraUtils.getFocalLengthInPixels(this);
        Log.i("CameraUtils", "initializeObjectResearch : focalLengthInPixel: "+focalLengthInPixel);
        distanceEstimator = new DistanceEstimator(focalLengthInPixel);

        // Initialize ObjectResearch
        initializeObjectResearch();
        // Request camera permissions
        getCameraPermissions();
        // Capture automatique d'une seule photo dès ouverture caméra
        resultView.setText("Veuillez tenir le téléphone stable et patienter...");
        try { toSpeech.speakObject("Veuillez tenir le téléphone stable et patienter pendant l'analyse."); } catch (Throwable ignore) {}
        handler.postDelayed(this::capturePhotoAndSendToOpenAI, 2000); // Une seule capture

    }

    private void capturePhotoAndSendToOpenAI() {
        if (photoCaptured || imageCapture == null) return;
        photoCaptured = true; // Bloque toute nouvelle capture tant que la réponse n'est pas reçue
        File outFile = new File(getCacheDir(), "capture_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions output = new ImageCapture.OutputFileOptions.Builder(outFile).build();
        imageCapture.takePicture(output, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                try {
                    Bitmap bmp = BitmapFactory.decodeFile(outFile.getAbsolutePath());
                    if (bmp == null) {
                        onError(new ImageCaptureException(ImageCapture.ERROR_FILE_IO, "Decode failed", null));
                        return;
                    }
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, 1024, 1024 * bmp.getHeight() / bmp.getWidth(), true);
                    String base64 = ca.ets.navigatets.utils.BitmapUtils.bitmapToBase64(scaled);
                    runOnUiThread(() -> resultView.setText("Analyse en cours..."));
                    try { toSpeech.speakObject("Analyse en cours, veuillez patienter."); } catch (Throwable ignore) {}
                    callOpenAiVision(base64);
                } catch (Throwable t) {
                    onError(new ImageCaptureException(ImageCapture.ERROR_UNKNOWN, t.getMessage(), t));
                } finally {
                    outFile.delete();
                }
            }
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> {
                    String msg = "Erreur capture: " + exception.getMessage();
                    resultView.setText(msg);
                    try { toSpeech.speakObject(msg); } catch (Throwable ignore) {}
                });
            }
        });
    }

    private void callOpenAiVision(String jpegBase64) {
        final String apiKey = BuildConfig.OPENAI_API_KEY;
        if (apiKey == null || apiKey.isEmpty()) {
            runOnUiThread(() -> {
                String msg = "Clé OpenAI absente.";
                resultView.setText(msg);
                try { toSpeech.speakObject(msg); } catch (Throwable ignore) {}
            });
            return;
        }
        String prompt = "Dis-moi si l'objet '" + searchedObjet + "' est présent sur cette image. Réponds simplement par oui ou non, puis explique brièvement.";
        try {
            JSONArray content = new JSONArray();
            content.put(new JSONObject().put("type", "text").put("text", prompt));
            String dataUrl = "data:image/jpeg;base64," + jpegBase64;
            JSONObject imageObj = new JSONObject()
                .put("type", "image_url")
                .put("image_url", new JSONObject().put("url", dataUrl));
            content.put(imageObj);
            JSONObject message = new JSONObject().put("role", "user").put("content", content);
            JSONObject body = new JSONObject()
                .put("model", "gpt-4o")
                .put("temperature", 0.2)
                .put("messages", new JSONArray().put(message));

            MediaType mediaType = MediaType.parse("application/json");
            RequestBody requestBody = RequestBody.create(body.toString(), mediaType);
            Request req = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();
            OkHttpClient client = new OkHttpClient();
            client.newCall(req).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    runOnUiThread(() -> {
                        String msg = "Erreur réseau: " + e.getMessage();
                        resultView.setText(msg);
                        try { toSpeech.speakObject(msg); } catch (Throwable ignore) {}
                    });
                }
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    String txt = response.body() != null ? response.body().string() : "";
                    String spoken = "";
                    try {
                        JSONObject root = new JSONObject(txt);
                        if (response.isSuccessful()) {
                            JSONArray choices = root.optJSONArray("choices");
                            if (choices != null && choices.length() > 0) {
                                JSONObject msgObj = choices.getJSONObject(0).getJSONObject("message");
                                spoken = msgObj.optString("content", "");
                                if (spoken.isEmpty()) {
                                    JSONArray parts = msgObj.optJSONArray("content");
                                    if (parts != null && parts.length() > 0) {
                                        StringBuilder sb = new StringBuilder();
                                        for (int i = 0; i < parts.length(); i++) {
                                            JSONObject p = parts.optJSONObject(i);
                                            if (p != null) {
                                                String t = p.optString("text", null);
                                                if (t != null && !t.isEmpty()) sb.append(t);
                                            }
                                        }
                                        spoken = sb.toString();
                                    }
                                }
                            }
                            if (spoken.isEmpty()) spoken = "Aucune réponse.";
                        } else {
                            JSONObject err = root.optJSONObject("error");
                            spoken = err != null ? err.optString("message") : "Erreur API: code " + response.code();
                        }
                    } catch (Throwable t) {
                        spoken = response.isSuccessful() ? "Réponse vide" : "Erreur API: code " + response.code();
                    }
                    final String finalSpoken = spoken;
                    runOnUiThread(() -> {
                        resultView.setText(finalSpoken);
                        try { toSpeech.speakObject(finalSpoken); } catch (Throwable ignore) {}
                    });
                }
            });
        } catch (org.json.JSONException e) {
            runOnUiThread(() -> {
                String msg = "Erreur JSON: " + e.getMessage();
                resultView.setText(msg);
                try { toSpeech.speakObject(msg); } catch (Throwable ignore) {}
            });
        }

    }

    private void initializeObjectResearch() {
        if(itemsSize != null ){
            Log.i(TAG, "initializeObjectResearch: found "+itemsSize.size());
        }
        Log.i(TAG, "initializeObjectResearch: found "+searchedObjet);
        ObjectDetectionManager objectDetectionManager = new ObjectDetectionManager(this);
        float MIN_SCORE = 0.5f;
        cameraFrameAnalyzer = new CameraFrameAnalyzer(MIN_SCORE, objectDetectionManager, (detections, rotatedBitmap) -> {
            boolean found = false;
            for (ca.ets.navigatets.models.Detection detection : detections) {
                String label = detection.label();
                float confidence = detection.confidence();

                if (label.equals(searchedObjet)) {
                    Log.i(TAG, "initializeObjectResearch: found "+detection.label());
                    Double [] realSize = itemsSize.get(label);
                    if (realSize == null) {
                        continue;
                    }
                    RectF rectF = detection.boundingBox();
                    List<DetectionResult> detectionResults = new ArrayList<>();
                    detectionResults.add(new DetectionResult(rectF,label,confidence));
                    float w = rectF.top - rectF.bottom;
                    Log.i(TAG, "initializeObjectResearch: Rec height  "+w);
                    Log.i(TAG, "initializeObjectResearch: Rec height "+rectF.height());
                    Double objetSizeInPixel = (double) Math.max(detection.boundingBox().width(),detection.boundingBox().height());
                    String result = getResult(objetSizeInPixel, realSize[0], label, detection.confidence());
                    overlayView.setModelInputImageSize(rotatedBitmap.getWidth(),rotatedBitmap.getHeight());
                    overlayView.setDetectionResults(detectionResults);
                    runOnUiThread(()-> resultViewHandler(result));
                    isAnalyzing = false;
                    found = true;
                    break;
                }
            }
            // Ne rien afficher ici : attendre la réponse OpenAI pour afficher 'objet non détecté' si besoin
            // if (!found) {
            //     runOnUiThread(() -> handleObjectNotFound());
            // }
        });
    }

    private boolean lastSearchFailed = false;

    private void handleObjectNotFound() {
        String msg = "L'objet que vous recherchez n'est pas sur cet angle. Changez de position et touchez deux fois l'écran pour relancer la recherche.";
        resultView.setText(msg);
        toSpeech.speakObject(msg);
        lastSearchFailed = true;
        previewView.setOnTouchListener(new android.view.View.OnTouchListener() {
            private long lastTapTime = 0;
            private int tapCount = 0;
            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    long now = System.currentTimeMillis();
                    if (now - lastTapTime < 400) {
                        tapCount++;
                    } else {
                        tapCount = 1;
                    }
                    lastTapTime = now;
                    if (tapCount == 2) {
                        // Double tap: relancer la recherche
                        tapCount = 0;
                        lastSearchFailed = false;
                        resultView.setText("");
                        toSpeech.speakObject("Recherche relancée.");
                        startCamera();
                    } else if (tapCount == 1) {
                        // Simple tap: redemander l'objet
                        previewView.postDelayed(() -> {
                            if (tapCount == 1 && lastSearchFailed) {
                                tapCount = 0;
                                toSpeech.speakObject("Quel objet recherchez-vous ?");
                                finish(); // Quitter pour revenir à la page vocale
                            }
                        }, 450);
                    }
                }
                return true;
            }
        });
    }

    private String getResult(Double objectSizeInPixel, Double realSize, String label,float confidence) {
        double distance = distanceEstimator.estimateDistance(realSize, objectSizeInPixel);
        label = label.substring(0, 1).toUpperCase() + label.substring(1);
        if (distance < 1) {
            distance = distance * 100;
            return String.format(Locale.ENGLISH,
                    "%s was detected\nDistance : %.2f cm\nScore : %.2f",
                    label, distance, confidence * 100);
        }
        return String.format(Locale.ENGLISH,
                "%s was detected\nDistance : %.2f m\nScore : %.2f",
                label, distance, confidence * 100);
    }

    private void setToggleDetectionButton(Button toggleDetectionButton) {
        toggleDetectionButton.setOnClickListener(e-> {
            overlayView.clearDetections();
            resultView.setText(R.string.results);
            startCamera();
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
        // Ajout : initialisation de ImageCapture
        imageCapture = new ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build();
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
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy ->{
                    if(!isAnalyzing){
                        imageProxy.close();
                        return;
                    }

                    overlayView.setModelInputImageSize(imageProxy.getWidth(),imageProxy.getHeight());

                    long elapses = System.currentTimeMillis() - analysisStartTime;
                    String title = "Objet Research 🔎🔎🔎️‍️";
                    String message = "Do you want to stop the research ?";
                    if(elapses >= ANALYSIS_DURATION_MS ) {
                        isAnalyzing = false;
                        runOnUiThread(()-> ObjectDetectionUtilities
                                .showContinueDialog(this, title, message, ()->{
                                    isAnalyzing = false;
                                    imageProxy.close();
                                    finish();
                                },()->{
                                    analysisStartTime = System.currentTimeMillis();
                                    isAnalyzing = true;
                                }));
                        imageProxy.close();
                        return;
                    }
                    if(searchedObjet != null){
                        cameraFrameAnalyzer.runObjectDetection(imageProxy);
                    }
                    imageProxy.close();
                });

                // Select back camera as a default
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalysis
                );
            } catch (Exception exc) {
                Log.e(TAG, "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void resultViewHandler(String result) {
        Log.i(TAG, "initializeObjectResearch: resultViewHandler "+result);
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(200);
            }
        }
        toSpeech.speakObject(result);

        // Ajout : message vocal et instructions interactives
        if (result.toLowerCase().contains("oui") || result.toLowerCase().contains("présent") || result.toLowerCase().contains("detected")) {
            String msg = "Recherche terminée. Pour faire une nouvelle recherche, touchez l'écran.";
            toSpeech.speakObject(msg);
            resultView.append("\n\n" + msg);
            resultView.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    finish(); // Retour à la recherche vocale
                }
                return true;
            });
        } else {
            String msg = "Objet non trouvé. Faites un appui long pour relancer l'analyse.";
            toSpeech.speakObject(msg);
            resultView.append("\n\n" + msg);
            resultView.setOnTouchListener(new android.view.View.OnTouchListener() {
                private long downTime = 0;
                @Override
                public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        downTime = System.currentTimeMillis();
                    } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        long duration = System.currentTimeMillis() - downTime;
                        if (duration > 500) {
                            // Appui long : relancer analyse
                            toSpeech.speakObject("Recherche relancée.");
                            resultView.setText("");
                            startCamera();
                        }
                    }
                    return true;
                }
            });
        }
        resultView.setText(result);
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
