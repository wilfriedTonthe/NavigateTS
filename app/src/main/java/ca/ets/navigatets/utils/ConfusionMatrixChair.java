package ca.ets.navigatets.utils;

/**
 * Matrice de confusion pour la classification binaire chaise occupee / chaise libre.
 *
 * Convention :
 *  - Positif  (P) = chaise occupee
 *  - Negatif  (N) = chaise libre
 *
 * VP : vrai positif  -> chaise occupee (GT) et predite occupee
 * VN : vrai negatif  -> chaise libre (GT) et predite libre
 * FP : faux positif  -> chaise libre (GT) mais predite occupee
 * FN : faux negatif  -> chaise occupee (GT) mais predite libre
 */
public class ConfusionMatrixChair {

    private int vp; // vrais positifs
    private int vn; // vrais negatifs
    private int fp; // faux positifs
    private int fn; // faux negatifs

    /**
     * Ajoute un exemple a la matrice de confusion.
     *
     * @param groundTruthOccupied true si la chaise est vraiment occupee (verite terrain)
     * @param predictedOccupied   true si le modele predit chaise occupee
     */
    public void addSample(boolean groundTruthOccupied, boolean predictedOccupied) {
        if (groundTruthOccupied && predictedOccupied) {
            vp++;
        } else if (!groundTruthOccupied && !predictedOccupied) {
            vn++;
        } else if (!groundTruthOccupied && predictedOccupied) {
            fp++;
        } else if (groundTruthOccupied && !predictedOccupied) {
            fn++;
        }
    }

    public int getVP() { return vp; }
    public int getVN() { return vn; }
    public int getFP() { return fp; }
    public int getFN() { return fn; }

    public int getTotal() { return vp + vn + fp + fn; }

    /**
     * Exactitude globale (accuracy).
     */
    public double getAccuracy() {
        int total = getTotal();
        return total == 0 ? 0.0 : (vp + vn) / (double) total;
    }

    /**
     * Precision pour la classe "chaise occupee" (VP / (VP + FP)).
     */
    public double getPrecisionOccupied() {
        int denom = vp + fp;
        return denom == 0 ? 0.0 : vp / (double) denom;
    }

    /**
     * Rappel (sensibilite) pour la classe "chaise occupee" (VP / (VP + FN)).
     */
    public double getRecallOccupied() {
        int denom = vp + fn;
        return denom == 0 ? 0.0 : vp / (double) denom;
    }

    /**
     * F1-score pour la classe "chaise occupee".
     */
    public double getF1Occupied() {
        double precision = getPrecisionOccupied();
        double recall = getRecallOccupied();
        double denom = precision + recall;
        return denom == 0.0 ? 0.0 : 2.0 * precision * recall / denom;
    }
}
