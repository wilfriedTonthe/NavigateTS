package ca.ets.navigatets.ui.home

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ca.ets.navigatets.databinding.ListItemLocationBinding
import ca.ets.navigatets.model.Location

class LocationAdapter(private val homeViewModel: HomeViewModel) :
    ListAdapter<Location, LocationAdapter.LocationViewHolder>(LocationDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ListItemLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val location = getItem(position)
        holder.bind(location, homeViewModel) // Pass homeViewModel here
        holder.itemView.setOnClickListener {
            Log.d("AdapterClick", "Clicked: ${location.name}, ImageResId: ${location.floorPlanImageResId}")
            homeViewModel.selectFloorPlan(location.name, location.floorPlanImageResId)
            // The fragment's observer on homeViewModel.selectedLocationName will call notifyDataSetChanged
        }
    }

    class LocationViewHolder(private val binding: ListItemLocationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(location: Location, homeViewModel: HomeViewModel) {
            binding.textViewLocationName.text = location.name
            val isSelected = homeViewModel.isSelectedLocation(location.name)
            Log.d("AdapterBind", "Binding ${location.name}, isSelected: $isSelected, CheckMark ID: ${binding.imageViewCheckMark.id}")
            binding.imageViewCheckMark.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }

    object LocationDiffCallback : DiffUtil.ItemCallback<Location>() {
        override fun areItemsTheSame(oldItem: Location, newItem: Location): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Location, newItem: Location): Boolean {
            return oldItem == newItem
        }
    }
}
