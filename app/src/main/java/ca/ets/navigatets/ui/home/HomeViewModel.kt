package ca.ets.navigatets.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ca.ets.navigatets.R // Ensure this import is correct
import ca.ets.navigatets.model.Location

class HomeViewModel : ViewModel() {

    // Original LiveData for text (if still needed, otherwise can be removed)
    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment" // You might want to remove or change this
    }
    val text: LiveData<String> = _text

    // Full list of locations (hardcoded for now)
    private val allLocations = listOf(
        Location("Basement", R.drawable.basement), // Using your image
        Location("Ground Floor", R.drawable.ground_floor),    // Using your image as a placeholder
        Location("2nd Floor", R.drawable.floor_2),   // Using your image as a placeholder
        Location("3rd Floor", R.drawable.floor_3)
    )

    private val _filteredLocations = MutableLiveData<List<Location>>().apply {
        value = allLocations
    }
    val filteredLocations: LiveData<List<Location>> = _filteredLocations

    private val _selectedFloorPlanImageResId = MutableLiveData<Int?>()
    val selectedFloorPlanImageResId: LiveData<Int?> = _selectedFloorPlanImageResId

    // LiveData for the name of the selected location
    private val _selectedLocationName = MutableLiveData<String?>()
    val selectedLocationName: LiveData<String?> = _selectedLocationName

    // LiveData to trigger navigation - now nullable
    private val _navigateToDashboard = MutableLiveData<Boolean?>(null)
    val navigateToDashboard: LiveData<Boolean?> = _navigateToDashboard

    fun searchLocations(query: String?) {
        if (query.isNullOrEmpty()) {
            _filteredLocations.value = allLocations
        } else {
            _filteredLocations.value = allLocations.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
    }

    // Updated to also take locationName and trigger navigation
    fun selectFloorPlan(locationName: String, imageResId: Int) {
        _selectedFloorPlanImageResId.value = imageResId
        _selectedLocationName.value = locationName
        _navigateToDashboard.value = true // Trigger navigation
    }

    // Call this method from the Fragment after navigation has been handled
    fun onNavigationToDashboardHandled() {
        _navigateToDashboard.value = null // Set to null to consume the event
    }

    fun clearSelectedFloorPlan() {
        _selectedFloorPlanImageResId.value = null
        _selectedLocationName.value = null // Clear the selected name as well
        // Optionally, reset navigation trigger if clearing selection should prevent any pending navigation
        _navigateToDashboard.value = null 
    }

    // Helper to check if a location is the currently selected one
    fun isSelectedLocation(locationName: String): Boolean {
        return _selectedLocationName.value == locationName
    }
}
