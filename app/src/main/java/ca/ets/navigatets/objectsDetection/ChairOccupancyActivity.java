package ca.ets.navigatets.objectsDetection;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
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

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import ca.ets.navigatets.R;
import ca.ets.navigatets.models.Detection;
import ca.ets.navigatets.objectsResearch.OverlayView;
import ca.ets.navigatets.utils.CameraFrameAnalyzer;
import ca.ets.navigatets.utils.CameraUtils;
import ca.ets.navigatets.utils.ObjectDetectionManager;
import ca.ets.navigatets.utils.ToSpeech;

public class ChairOccupancyActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 1011;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView previewView;
    private TextView statusText;
    private View statusBadge;
    private OverlayView overlayView;
    private View statusContainer;

    private CameraFrameAnalyzer cameraFrameAnalyzer;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private boolean isAnalyzing = false;
    private ToSpeech toSpeech;
    // Stabilization fields
    private final Deque<Boolean> occHistory = new ArrayDeque<>();
    private static final int HISTORY_MAX = 8;
    private int holdCounter = 0;
    private static final int HOLD_MIN_FRAMES = 3;
    private Boolean stableState = null; // null until first stable
    // Tuning constants
    private static final float MIN_SCORE = 0.30f;           // model confidence threshold
    private static final float PERSON_IOU_THRESHOLD = 0.10f; // IoU threshold for person-chair overlap
    private static final float OBJECT_IOU_THRESHOLD = 0.06f; // IoU threshold for object-chair overlap (lower: small objects)
    private static final float OBJECT_IOM_THRESHOLD = 0.35f; // IoM threshold: intersection over min(areaA, areaB)
    // Label set for chairs (expandable depending on model labels)
    private static final Set<String> CHAIR_LIKE = new HashSet<>(Arrays.asList(
            "chair","couch","sofa","bench","seat","stool"
    ));
    // Objects that should NOT make a chair "occupied" (furniture typically in front of chairs)
    private static final Set<String> IGNORED_OBJECTS = new HashSet<>(Arrays.asList(
            "table","dining table","desk","coffee table","counter","keyboard","laptop","monitor","tv","television"
    ));
    // Distance estimation
    private float focalLengthPixels = -1f;
    private static final double DEFAULT_CHAIR_WIDTH_M = 0.45; // 45cm approx.
    // Count & speech debounce
    private int lastOccupiedCount = -1;
    private int lastFreeCount = -1;
    private long lastNoChairAnnouncementTime = 0L;
    private static final long NO_CHAIR_COOLDOWN_MS = 4000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chair_occupancy);

        previewView = findViewById(R.id.preview_view);
        statusText = findViewById(R.id.tv_status);
        statusBadge = findViewById(R.id.status_badge);
        overlayView = findViewById(R.id.overlay_view);
        statusContainer = findViewById(R.id.status_container);
        toSpeech = new ToSpeech(this);

        initializeObjectDetection();
        getCameraPermissions();
    }

    private void initializeObjectDetection() {
        ObjectDetectionManager odm = new ObjectDetectionManager(this);
        cameraFrameAnalyzer = new CameraFrameAnalyzer(MIN_SCORE, odm, (detections, bitmap) -> {
            // Separate chairs and other objects
            List<RectF> persons = new ArrayList<>();
            List<RectF> chairs = new ArrayList<>();
            List<RectF> occupyingObjects = new ArrayList<>(); // Objects that can occupy a chair (not tables)
            List<Detection> chairDetections = new ArrayList<>();
            for (Detection d : detections) {
                String lbl = d.label().toLowerCase();
                if (lbl.contains("person") || lbl.contains("people")) {
                    persons.add(new RectF(d.boundingBox()));
                } else {
                    boolean isChair = lbl.contains("chair");
                    if (!isChair) {
                        for (String c : CHAIR_LIKE) { if (lbl.contains(c)) { isChair = true; break; } }
                    }
                    if (isChair) {
                        chairs.add(new RectF(d.boundingBox()));
                        chairDetections.add(d);
                    } else {
                        // Check if this object should be ignored (tables, desks, etc.)
                        boolean shouldIgnore = false;
                        for (String ignored : IGNORED_OBJECTS) {
                            if (lbl.contains(ignored)) {
                                shouldIgnore = true;
                                break;
                            }
                        }
                        // Only add objects that can actually occupy a chair
                        if (!shouldIgnore) {
                            occupyingObjects.add(new RectF(d.boundingBox()));
                        }
                    }
                }
            }

            // If no chairs detected, guide the user (debounced)
            long now = System.currentTimeMillis();
            if (chairs.isEmpty()) {
                // Hide status UI when no chairs detected
                runOnUiThread(() -> statusContainer.setVisibility(View.GONE));
                if (now - lastNoChairAnnouncementTime > NO_CHAIR_COOLDOWN_MS) {
                    lastNoChairAnnouncementTime = now;
                    runOnUiThread(() -> toSpeech.speakObject(getString(R.string.point_to_chair)));
                }
            }

            // Determine occupancy per chair and build overlay labels with distance
            int occupiedCount = 0;
            int freeCount = 0;
            List<DetectionResult> results = new ArrayList<>();

            // Ensure focal length is initialized
            if (focalLengthPixels <= 0) {
                focalLengthPixels = CameraUtils.getFocalLengthInPixels(this);
            }

            for (int i = 0; i < chairDetections.size(); i++) {
                Detection d = chairDetections.get(i);
                RectF chair = new RectF(d.boundingBox());
                boolean chairOccupied = false;

                // 1) Person overlap with chair seat region
                for (RectF person : persons) {
                    float overlap = iou(chair, person);
                    if (overlap > PERSON_IOU_THRESHOLD || isPersonBottomInsideChairSeat(person, chair)) {
                        chairOccupied = true;
                        break;
                    }
                }

                // 2) Any other object overlap with chair seat region (IoU/IoM + positional checks)
                // But ignore tables and similar furniture that are typically in front of chairs
                if (!chairOccupied) {
                    for (int j = 0; j < occupyingObjects.size(); j++) {
                        RectF obj = occupyingObjects.get(j);
                        float overlap = iou(chair, obj);
                        float overlapMin = iom(chair, obj);
                        if (overlap > OBJECT_IOU_THRESHOLD
                                || overlapMin > OBJECT_IOM_THRESHOLD
                                || isObjectBottomCenterInsideChair(obj, chair)
                                || isObjectCenterInsideChair(obj, chair)) {
                            chairOccupied = true;
                            break;
                        }
                    }
                }
                 if (chairOccupied) occupiedCount++; else freeCount++;

                // Distance estimation (approx): use bbox width in pixels
                double distanceM = estimateDistanceMeters(DEFAULT_CHAIR_WIDTH_M, chair.width(), focalLengthPixels);
                String labelText = (chairOccupied ? getString(R.string.chair_occupied) : getString(R.string.chair_free))
                        + String.format(java.util.Locale.ENGLISH, " (%.2f m)", distanceM > 0 ? distanceM : -1.0);
                results.add(new DetectionResult(chair, labelText, d.confidence()));
            }

            // Global occupancy state (any chair occupied?)
            boolean anyOccupied = occupiedCount > 0;

            // Update UI immediately when we have chairs (no stabilization for UI)
            if (!chairs.isEmpty()) {
                runOnUiThread(() -> {
                    statusContainer.setVisibility(View.VISIBLE);
                    setStatusUI(anyOccupied);
                });
            }

            // Update temporal history
            if (occHistory.size() == HISTORY_MAX) occHistory.removeFirst();
            occHistory.addLast(anyOccupied);
            int countTrue = 0;
            for (boolean v : occHistory) if (v) countTrue++;
            boolean majority = countTrue >= Math.ceil(occHistory.size() * 0.6);

            // Debounce state changes
            boolean current = majority;
            if (stableState == null || current != stableState) {
                holdCounter++;
                if (holdCounter >= HOLD_MIN_FRAMES) {
                    stableState = current;
                    holdCounter = 0;
                    boolean finalState = stableState;
                    runOnUiThread(() -> {
                        // Keep UI in sync if chairs are present
                        setStatusUI(finalState);
                        // Speak on change for both states
                        toSpeech.speakObject(getString(finalState ? R.string.chair_occupied : R.string.chair_free));
                    });
                }
            } else {
                holdCounter = 0;
            }

            // Announce directional guidance for free chairs
            if (occupiedCount != lastOccupiedCount || freeCount != lastFreeCount) {
                lastOccupiedCount = occupiedCount; lastFreeCount = freeCount;
                int totalChairs = occupiedCount + freeCount;
                
                if (totalChairs > 0) {
                    String directionMsg = buildDirectionalMessage(results, freeCount, occupiedCount, totalChairs, bitmap.getWidth());
                    runOnUiThread(() -> toSpeech.speakObject(directionMsg));
                }
            }

            // Update overlay visualization (always on UI thread)
            runOnUiThread(() -> {
                overlayView.setModelInputImageSize(bitmap.getWidth(), bitmap.getHeight());
                overlayView.setDetectionResults(results);
            });
        });
    }

    private void updateStatus(boolean occupied) {
        isAnalyzing = true;
        int color = occupied ? 0xAAE53935 /* red */ : 0xAA43A047 /* green */;
        statusBadge.setBackgroundColor(color);
        statusText.setText(occupied ? R.string.chair_occupied : R.string.chair_free);
        if (occupied) {
            toSpeech.speakObject(getString(R.string.chair_occupied));
        }
    }

    private void setStatusUI(boolean occupied) {
        int color = occupied ? 0xAAE53935 /* red */ : 0xAA43A047 /* green */;
        statusBadge.setBackgroundColor(color);
        statusText.setText(occupied ? R.string.chair_occupied : R.string.chair_free);
    }

    private boolean isChairOccupied(List<Detection> detections) {
        // Find any chair and person with sufficient overlap
        for (Detection d1 : detections) {
            String l1 = d1.label().toLowerCase();
            boolean isChair = l1.contains("chair");
            if (!isChair) {
                for (String c : CHAIR_LIKE) {
                    if (l1.contains(c)) { isChair = true; break; }
                }
            }
            if (!isChair) continue;
            RectF chair = new RectF(d1.boundingBox());
            for (Detection d2 : detections) {
                String l2 = d2.label().toLowerCase();
                if (!(l2.contains("person") || l2.contains("people"))) continue;
                RectF person = new RectF(d2.boundingBox());
                if (iou(chair, person) > 0.1f || isPersonBottomInsideChairSeat(person, chair)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPersonBottomInsideChairSeat(RectF person, RectF chair) {
        float px = (person.left + person.right) / 2f;
        float py = person.bottom;
        float seatTop = chair.top + (chair.bottom - chair.top) * 0.3f;
        return px >= chair.left && px <= chair.right && py >= seatTop && py <= chair.bottom;
    }

    private float iou(RectF a, RectF b) {
        float interLeft = Math.max(a.left, b.left);
        float interTop = Math.max(a.top, b.top);
        float interRight = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);
        float interW = Math.max(0, interRight - interLeft);
        float interH = Math.max(0, interBottom - interTop);
        float interArea = interW * interH;
        if (interArea <= 0) return 0f;
        float areaA = Math.max(0, a.right - a.left) * Math.max(0, a.bottom - a.top);
        float areaB = Math.max(0, b.right - b.left) * Math.max(0, b.bottom - b.top);
        return interArea / (areaA + areaB - interArea + 1e-6f);
    }

    private double estimateDistanceMeters(double realWidthMeters, float bboxWidthPixels, float focalPixels) {
        if (focalPixels <= 0 || bboxWidthPixels <= 0) return -1.0;
        return (realWidthMeters * focalPixels) / bboxWidthPixels;
    }

    private boolean intersects(RectF a, RectF b) {
        float interLeft = Math.max(a.left, b.left);
        float interTop = Math.max(a.top, b.top);
        float interRight = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);
        float interW = Math.max(0, interRight - interLeft);
        float interH = Math.max(0, interBottom - interTop);
        return interW > 0 && interH > 0;
    }

    private boolean isObjectBottomCenterInsideChair(RectF object, RectF chair) {
        float cx = (object.left + object.right) / 2f;
        float cy = object.bottom;
        return cx >= chair.left && cx <= chair.right && cy >= chair.top && cy <= chair.bottom;
    }

    private boolean isObjectCenterInsideChair(RectF object, RectF chair) {
        float cx = (object.left + object.right) / 2f;
        float cy = (object.top + object.bottom) / 2f;
        return cx >= chair.left && cx <= chair.right && cy >= chair.top && cy <= chair.bottom;
    }

    /**
     * Build a directional message to guide the user to free chairs.
     * Determines if chairs are to the left, right, or center of the frame.
     */
    private String buildDirectionalMessage(List<DetectionResult> results, int freeCount, int occupiedCount, int totalChairs, int imageWidth) {
        // Case 1: Only one chair
        if (totalChairs == 1) {
            DetectionResult chair = results.get(0);
            boolean isFree = chair.label.contains(getString(R.string.chair_free));
            String direction = getDirection(chair.boundingBox, imageWidth);
            
            if (isFree) {
                switch (direction) {
                    case "left": return getString(R.string.chair_left);
                    case "right": return getString(R.string.chair_right);
                    default: return getString(R.string.chair_front);
                }
            } else {
                return getString(R.string.chair_front_occupied);
            }
        }
        
        // Case 2: Multiple chairs
        StringBuilder msg = new StringBuilder();
        msg.append(getString(R.string.multiple_chairs_hint, totalChairs, freeCount, occupiedCount));
        
        if (freeCount > 0) {
            // Find the closest free chair and its direction
            DetectionResult closestFree = null;
            float maxArea = 0; // Larger area = closer chair
            
            for (DetectionResult r : results) {
                if (r.label.contains(getString(R.string.chair_free))) {
                    float area = r.boundingBox.width() * r.boundingBox.height();
                    if (area > maxArea) {
                        maxArea = area;
                        closestFree = r;
                    }
                }
            }
            
            if (closestFree != null) {
                String direction = getDirection(closestFree.boundingBox, imageWidth);
                msg.append(" ");
                switch (direction) {
                    case "left": 
                        msg.append(getString(R.string.go_left_for_free));
                        break;
                    case "right": 
                        msg.append(getString(R.string.go_right_for_free));
                        break;
                    default: 
                        msg.append(getString(R.string.go_front_for_free));
                        break;
                }
            }
        }
        
        return msg.toString();
    }
    
    /**
     * Determine if a bounding box is on the left, right, or center of the image.
     */
    private String getDirection(RectF box, int imageWidth) {
        float centerX = (box.left + box.right) / 2f;
        float leftThreshold = imageWidth / 3f;
        float rightThreshold = imageWidth * 2f / 3f;
        
        if (centerX < leftThreshold) {
            return "left";
        } else if (centerX > rightThreshold) {
            return "right";
        } else {
            return "center";
        }
    }

    private float iom(RectF a, RectF b) {
        float interLeft = Math.max(a.left, b.left);
        float interTop = Math.max(a.top, b.top);
        float interRight = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);
        float interW = Math.max(0, interRight - interLeft);
        float interH = Math.max(0, interBottom - interTop);
        float interArea = interW * interH;
        if (interArea <= 0) return 0f;
        float areaA = Math.max(0, a.right - a.left) * Math.max(0, a.bottom - a.top);
        float areaB = Math.max(0, b.right - b.left) * Math.max(0, b.bottom - b.top);
        float minArea = Math.max(1e-6f, Math.min(areaA, areaB));
        return interArea / minArea;
    }

    private void getCameraPermissions() {
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy -> {
                    if (!isAnalyzing) {
                        imageProxy.close();
                        return;
                    }
                    cameraFrameAnalyzer.runObjectDetection(imageProxy);
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                // start after bind
                isAnalyzing = true;

                // small delayed toast to guide
                new Handler(Looper.getMainLooper()).postDelayed(() ->
                        Toast.makeText(this, R.string.chair_detection_hint, Toast.LENGTH_SHORT).show(), 800);
            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toSpeech != null) toSpeech.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
