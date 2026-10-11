package org.seedhecode.incidentassistant.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.seedhecode.incidentassistant.demo.DemoActivityItem
import org.seedhecode.incidentassistant.demo.DemoDataProvider
import org.seedhecode.incidentassistant.demo.DemoIncidentStatus

data class HistoryFilterState(
    val searchQuery: String = "",
    val selectedCategory: String? = null,
    val selectedStatus: DemoIncidentStatus? = null
)

class HistoryViewModel : ViewModel() {
    private val _filterState = MutableStateFlow(HistoryFilterState())
    val filterState: StateFlow<HistoryFilterState> = _filterState

    val filteredDemoReports: StateFlow<List<DemoActivityItem>> = combine(
        DemoDataProvider.demoIncidents,
        _filterState
    ) { incidents, filters ->
        incidents.filter { item ->
            val matchesSearch = filters.searchQuery.isBlank() || 
                item.title.contains(filters.searchQuery, ignoreCase = true) ||
                item.id.contains(filters.searchQuery, ignoreCase = true)
            
            val matchesCategory = filters.selectedCategory == null || item.category == filters.selectedCategory
            val matchesStatus = filters.selectedStatus == null || item.status == filters.selectedStatus
            
            matchesSearch && matchesCategory && matchesStatus
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun setCategoryFilter(category: String?) {
        _filterState.update { it.copy(selectedCategory = category) }
    }

    fun setStatusFilter(status: DemoIncidentStatus?) {
        _filterState.update { it.copy(selectedStatus = status) }
    }
}
