package org.seedhecode.incidentassistant.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.seedhecode.incidentassistant.demo.DemoActivityItem
import org.seedhecode.incidentassistant.demo.DemoDataProvider
import org.seedhecode.incidentassistant.demo.DemoIncidentStatus

data class AdminAnalytics(
    val totalIncidents: Int = 0,
    val activeIncidents: Int = 0,
    val resolvedIncidents: Int = 0
)

class AdminViewModel : ViewModel() {
    
    val adminQueue: StateFlow<List<DemoActivityItem>> = DemoDataProvider.demoIncidents

    val analytics: StateFlow<AdminAnalytics> = DemoDataProvider.demoIncidents.map { incidents ->
        AdminAnalytics(
            totalIncidents = incidents.size,
            activeIncidents = incidents.count { it.status == DemoIncidentStatus.SUBMITTED || it.status == DemoIncidentStatus.PENDING_REVIEW },
            resolvedIncidents = incidents.count { it.status == DemoIncidentStatus.RESOLVED }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminAnalytics())

    fun updateIncidentStatus(id: String, newStatus: DemoIncidentStatus) {
        DemoDataProvider.updateIncidentStatus(id, newStatus)
    }
}
