package ca.ets.navigatets.evaluation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

import ca.ets.navigatets.R;
import ca.ets.navigatets.models.Detection;
import ca.ets.navigatets.utils.ObjectDetectionManager;

/**
 * Activité d'évaluation du modèle de détection de chaises.
 * Charge des images de test, exécute le modèle TFLite, compare aux annotations GT,
 * et calcule la matrice de confusion + précision + rappel.
 */
public class ChairEvaluationActivity extends AppCompatActivity {

    private static final float MIN_SCORE = 0.30f;
    private static final float IOU_THRESHOLD = 0.5f;
    private static final Set<String> CHAIR_LABELS = new HashSet<>(Arrays.asList(
            "chair", "couch", "sofa", "bench", "seat", "stool"
    ));

    private TextView tvResults;
    private ProgressBar progressBar;
    private Button btnRun;

    private ObjectDetectionManager odm;

    // Compteurs pour la matrice de confusion
    private int truePositives = 0;   // VP
    private int trueNegatives = 0;   // VN
    private int falsePositives = 0;  // FP
    private int falseNegatives = 0;  // FN

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chair_evaluation);

        tvResults = findViewById(R.id.tv_results);
        progressBar = findViewById(R.id.progress_eval);
        btnRun = findViewById(R.id.btn_run_evaluation);

        odm = new ObjectDetectionManager(this);

        btnRun.setOnClickListener(v -> runEvaluation());
    }

    private void runEvaluation() {
        btnRun.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvResults.setText("Évaluation en cours...\n");

        // Reset counters
        truePositives = 0;
        trueNegatives = 0;
        falsePositives = 0;
        falseNegatives = 0;

        Executors.newSingleThreadExecutor().execute(() -> {
            StringBuilder log = new StringBuilder();
            try {
                // Lister automatiquement les images dans test_images/
                String[] allFiles = getAssets().list("test_images");
                List<String> imageFiles = new ArrayList<>();
                if (allFiles != null) {
                    for (String f : allFiles) {
                        String lower = f.toLowerCase();
                        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
                            imageFiles.add(f);
                        }
                    }
                }

                if (imageFiles.isEmpty()) {
                    log.append("[ERREUR] Aucune image trouvée dans assets/test_images/\n");
                } else {
                    log.append("=== Détection sur ").append(imageFiles.size()).append(" image(s) ===\n\n");

                    int totalChairsDetected = 0;
                    int totalOccupied = 0;
                    int totalFree = 0;
                    int totalPersons = 0;

                    for (int i = 0; i < imageFiles.size(); i++) {
                        String fileName = imageFiles.get(i);
                        log.append("Image ").append(i + 1).append(": ").append(fileName).append("\n");

                        // Charger l'image depuis assets/test_images/
                        Bitmap bitmap = loadBitmapFromAssets("test_images/" + fileName);
                        if (bitmap == null) {
                            log.append("  [ERREUR] Impossible de charger l'image\n\n");
                            continue;
                        }

                        log.append("  Taille: ").append(bitmap.getWidth()).append("x").append(bitmap.getHeight()).append("\n");

                        // Exécuter le modèle
                        List<Detection> detections = odm.detectObjectsInCurrentFrame(bitmap, MIN_SCORE);

                        // Filtrer les chaises et personnes détectées
                        List<RectF> chairs = new ArrayList<>();
                        List<Float> chairScores = new ArrayList<>();
                        List<RectF> persons = new ArrayList<>();

                        for (Detection d : detections) {
                            String lbl = d.label().toLowerCase();
                            if (lbl.contains("person") || lbl.contains("people")) {
                                persons.add(new RectF(d.boundingBox()));
                            } else {
                                boolean isChair = false;
                                for (String c : CHAIR_LABELS) {
                                    if (lbl.contains(c)) {
                                        isChair = true;
                                        break;
                                    }
                                }
                                if (isChair) {
                                    chairs.add(new RectF(d.boundingBox()));
                                    chairScores.add(d.confidence());
                                }
                            }
                        }

                        log.append("  Personnes détectées: ").append(persons.size()).append("\n");
                        log.append("  Chaises détectées: ").append(chairs.size()).append("\n");

                        totalPersons += persons.size();
                        totalChairsDetected += chairs.size();

                        // Analyser l'occupation de chaque chaise
                        for (int c = 0; c < chairs.size(); c++) {
                            RectF chairBox = chairs.get(c);
                            float score = chairScores.get(c);
                            boolean occupied = isChairOccupied(chairBox, persons);

                            if (occupied) {
                                totalOccupied++;
                                log.append("    Chaise ").append(c + 1).append(": OCCUPÉE (conf: ")
                                        .append(String.format(Locale.FRENCH, "%.1f%%", score * 100)).append(")\n");
                            } else {
                                totalFree++;
                                log.append("    Chaise ").append(c + 1).append(": LIBRE (conf: ")
                                        .append(String.format(Locale.FRENCH, "%.1f%%", score * 100)).append(")\n");
                            }
                        }

                        log.append("\n");
                    }

                    // Résumé global
                    log.append("=== RÉSUMÉ GLOBAL ===\n");
                    log.append("Images analysées: ").append(imageFiles.size()).append("\n");
                    log.append("Total personnes détectées: ").append(totalPersons).append("\n");
                    log.append("Total chaises détectées: ").append(totalChairsDetected).append("\n");
                    log.append("  - Occupées: ").append(totalOccupied).append("\n");
                    log.append("  - Libres: ").append(totalFree).append("\n");

                    if (totalChairsDetected > 0) {
                        double occupancyRate = (double) totalOccupied / totalChairsDetected * 100;
                        log.append(String.format(Locale.FRENCH, "\nTaux d'occupation: %.1f%%\n", occupancyRate));
                    }
                }

            } catch (Exception e) {
                log.append("\n[ERREUR] ").append(e.getMessage());
                e.printStackTrace();
            }

            String finalLog = log.toString();
            new Handler(Looper.getMainLooper()).post(() -> {
                tvResults.setText(finalLog);
                progressBar.setVisibility(View.GONE);
                btnRun.setEnabled(true);
            });
        });
    }

    private boolean isChairOccupied(RectF chair, List<RectF> persons) {
        for (RectF person : persons) {
            if (iou(chair, person) > 0.10f || isPersonBottomInsideChairSeat(person, chair)) {
                return true;
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
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        return interArea / (areaA + areaB - interArea + 1e-6f);
    }

    private String loadAssetFile(String fileName) throws Exception {
        InputStream is = getAssets().open(fileName);
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();
        return new String(buffer, StandardCharsets.UTF_8);
    }

    private Bitmap loadBitmapFromAssets(String fileName) {
        try {
            InputStream is = getAssets().open(fileName);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }
}
