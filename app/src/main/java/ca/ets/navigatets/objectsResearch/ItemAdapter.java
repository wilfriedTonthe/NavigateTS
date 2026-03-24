package ca.ets.navigatets.objectsResearch;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ca.ets.navigatets.R;

/**
 * @author ank-tech
 */
public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {
    private final String [] itemList;
    private static String [] filteredItemList;
    private final OnItemClickListener listener;

    public ItemAdapter(String []itemList, OnItemClickListener listener) {
        this.itemList = itemList;
        this.listener = listener;
        filteredItemList = itemList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.activity_object_detection_item, parent, false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = filteredItemList[position];
        holder.titleTextView.setText(item);
    }

    @Override
    public int getItemCount() {
        return filteredItemList.length;
    }
    public interface OnItemClickListener {
        void onItemClick(String item);
    }
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        public ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.item_title);

            // add click on itemView
            itemView.setOnClickListener(v -> {
                if(listener != null) {
                    int position = getAdapterPosition();
                    String item = filteredItemList[position];
                    listener.onItemClick(item);
                }
            });
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void filter(String searchText){
        if(searchText.isEmpty()){
            filteredItemList = itemList;
        } else {
            List<String> filteredItems = new ArrayList<String >();
            for(String item : itemList){
                if(item.contains(searchText)){
                    filteredItems.add(item);
                }
            }
            filteredItemList = filteredItems.toArray(new String[0]);
        }
        notifyDataSetChanged();
    }
}

