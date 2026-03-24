package ca.ets.navigatets.ui.navigate

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NavigateViewModel : ViewModel() {
    private val _selectedImageResId = MutableLiveData<Int>()
    val selectedImageResId: LiveData<Int> = _selectedImageResId

    private val _selectedLocationName = MutableLiveData<String>()
    val selectedLocationName: LiveData<String> = _selectedLocationName

    fun setSelectedLocation(name: String, imageResId: Int) {
        _selectedLocationName.value = name
        _selectedImageResId.value = imageResId
    }
}
