package ca.ets.navigatets.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

/**
 * @author ank-tech
 */
public class GetDataFromAssets {

    public static HashMap<String, Double[]> loadDataFromAsset(Context context) {
        HashMap<String, Double[]> items = new HashMap<>();
        try{
            InputStream inputStream = context.getAssets().open("_labels.txt");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null){
                String [] parts = line.split(":");
                if (parts.length == 3) {
                    String label = parts[0];
                    double height = Double.parseDouble(parts[1]);
                    double width = Double.parseDouble(parts[2]);
                    items.put(label, new Double[]{height, width});
                }
            }
            inputStream.close();
        } catch (IOException e) {
            Log.e("ObjectResearchList","Erreur de lecture du fichier",e);
        } catch (NullPointerException ne){
            Log.e("ObjectResearchList","Erreur de conversion",ne);
        }
        return items;
    }
}
