package com.berdikariintigemilang.pos.ui.shift

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.berdikariintigemilang.pos.core.network.ApiResult
import com.berdikariintigemilang.pos.data.remote.ZReportDto
import com.berdikariintigemilang.pos.data.repository.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ZReportUiState(
    val loading: Boolean = true,
    val report: ZReportDto? = null,
    val error: String? = null
)

@HiltViewModel
class ZReportViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val shiftId: Long = savedStateHandle.get<String>("shiftId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(ZReportUiState())
    val state: StateFlow<ZReportUiState> = _state

    init {
        viewModelScope.launch {
            when (val res = shiftRepository.zReport(shiftId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, report = res.data) }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }
}
