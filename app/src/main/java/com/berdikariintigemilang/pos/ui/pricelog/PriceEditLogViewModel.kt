package com.berdikariintigemilang.pos.ui.pricelog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.data.local.PriceEditLogEntity
import com.berdikariintigemilang.pos.data.repository.PriceEditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PriceEditLogViewModel @Inject constructor(
    repository: PriceEditLogRepository
) : ViewModel() {

    /** Daftar log perubahan harga, terbaru di atas. */
    val logs: StateFlow<List<PriceEditLogEntity>> =
        repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
