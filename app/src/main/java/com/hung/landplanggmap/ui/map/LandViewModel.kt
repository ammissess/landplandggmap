package com.hung.landplanggmap.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hung.landplanggmap.data.model.LandParcel
import com.hung.landplanggmap.data.repository.LandRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LandViewModel(
    private val repository: LandRepository = LandRepository()
) : ViewModel() {

    private val _lands = MutableStateFlow<List<LandParcel>>(emptyList())
    val lands: StateFlow<List<LandParcel>> = _lands


    fun fetchLands() {
        viewModelScope.launch {
            _lands.value = repository.getUserLands()
        }
    }
    fun addLand(land: LandParcel) {
        viewModelScope.launch {
            repository.addLand(land)
            fetchLands()
        }
    }
}