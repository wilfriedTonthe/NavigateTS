package ca.ets.navigatets.objectsDetection;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ca.ets.navigatets.R;

/**
 * @author ank-tech
 */
public class _ItemAdapter extends RecyclerView.Adapter<_ItemAdapter.ViewHolder> {
    private final List<String> filteredItemList;

    public _ItemAdapter(List<String> itemList) {
        this.filteredItemList = itemList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.activity_object_detection_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = filteredItemList.get(position);
        holder.titleTextView.setText(item);
    }

    @Override
    public int getItemCount() {
        return filteredItemList.size();
    }
    public String getItem(int position){
        return filteredItemList.get(position);
    }
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        public ViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.item_title);
        }
    }
    public boolean addNewItemOnList(String item, int position){
        if(0<=position && position <= filteredItemList.size() && !filteredItemList.contains(item)) {
            filteredItemList.add(position, item);
            notifyItemInserted(position);
            return true;
        } else {
            Log.e("Adapter", "Position invalid for insertion : " + position);
        }
        return false;
    }
    public void clearList(){
        int size = filteredItemList.size();
        filteredItemList.clear();
        notifyItemRangeRemoved(0,size);
    }
}

