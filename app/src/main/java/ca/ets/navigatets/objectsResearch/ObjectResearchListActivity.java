package ca.ets.navigatets.objectsResearch;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ca.ets.navigatets.ConstantsKt;
import ca.ets.navigatets.R;
import ca.ets.navigatets.utils.ToSpeech;

/**
 * @author ank-tech
 */
public class ObjectResearchListActivity extends AppCompatActivity implements RecyclerView.OnItemTouchListener {

    private ToSpeech toSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_object_research_list);

        RecyclerView recyclerView = findViewById(R.id.object_research_recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));


        DividerItemDecoration decoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.customer_divider);
        assert dividerDrawable != null;
        decoration.setDrawable(dividerDrawable);
        recyclerView.addItemDecoration(decoration);

        toSpeech = new ToSpeech(this);

        String[] itemList = loadDataFromAsset(this);
        Arrays.sort(itemList);
        ItemAdapter adapter = new ItemAdapter(itemList, item -> {
            toSpeech.speakObject(item);
            Toast.makeText(ObjectResearchListActivity.this, "Click sur " + item, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(ObjectResearchListActivity.this, ObjectResearchActivity.class);
            intent.putExtra(ConstantsKt.SEARCHED_OBJECT,item);
            startActivity(intent);
        });


        SearchView searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                adapter.filter(s);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                adapter.filter(s);
                return true;
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private String [] loadDataFromAsset(Context context) {
        List<String> item = new ArrayList<>();
        try{
            InputStream inputStream = context.getAssets().open("labels.txt");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null){
                item.add(line);
            }
            inputStream.close();
        } catch (IOException e) {
            Log.e(getClass().getName(),"Erreur de lecture du fichier",e);
        }
        return item.toArray(new String[0]);
    }


    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        return false;
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {

    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(toSpeech != null){
            toSpeech.destroy();
        }
    }
}