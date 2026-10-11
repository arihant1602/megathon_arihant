package org.seedhecode.incidentassistant.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DemoReportSummary(
    val draftCount: Int = 2,
    val submittedCount: Int = 5,
    val pendingReviewCount: Int = 1
)

enum class DemoIncidentStatus(val displayName: String) {
    DRAFT("Draft"),
    SUBMITTED("Submitted (Demo)"),
    PENDING_REVIEW("Pending Review"),
    RESOLVED("Resolved (Demo)")
}

enum class DemoIncidentPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class DemoActivityItem(
    val id: String,
    val title: String,
    val category: String,
    val timestamp: String,
    val status: DemoIncidentStatus,
    val department: String,
    val priority: DemoIncidentPriority,
    val location: String,
    val description: String
)

/** 
 * Centralized, shared source of truth for demo incidents. 
 * Allows Admin status changes to reflect globally in History and Details.
 */
object DemoDataProvider {
    val summary = DemoReportSummary(draftCount = 2, submittedCount = 5, pendingReviewCount = 1)

    private val initialActivities = listOf(
        DemoActivityItem(
            id = "INC-1042",
            title = "Smoke detected in electrical utility corridor",
            category = "Fire / Smoke",
            timestamp = "10 mins ago",
            status = DemoIncidentStatus.PENDING_REVIEW,
            department = "Fire & Rescue",
            priority = DemoIncidentPriority.HIGH,
            location = "Building B, Sector 2",
            description = "Light smoke observed near the main server rack and electrical panel. Power tripped across the wing."
        ),
        DemoActivityItem(
            id = "INC-1041",
            title = "Medical assistance requested at Sector 4 Gate",
            category = "Medical Emergency",
            timestamp = "1 hour ago",
            status = DemoIncidentStatus.SUBMITTED,
            department = "Medical Response",
            priority = DemoIncidentPriority.CRITICAL,
            location = "Sector 4, Main Gate",
            description = "Visitor experiencing shortness of breath. No previous medical history known."
        ),
        DemoActivityItem(
            id = "INC-1040",
            title = "Unauthorized perimeter access attempt",
            category = "Security Incident",
            timestamp = "3 hours ago",
            status = DemoIncidentStatus.RESOLVED,
            department = "Security Operations",
            priority = DemoIncidentPriority.MEDIUM,
            location = "North Perimeter Fence",
            description = "Motion sensors triggered. Security patrol dispatched and secured the area."
        ),
        DemoActivityItem(
            id = "INC-1039",
            title = "HVAC coolant leak in Server Room A",
            category = "Infrastructure / Maintenance",
            timestamp = "1 day ago",
            status = DemoIncidentStatus.DRAFT,
            department = "Facilities & Maintenance",
            priority = DemoIncidentPriority.LOW,
            location = "Server Room A",
            description = "Minor leak detected. Requires scheduled maintenance."
        )
    )

    private val _demoIncidents = MutableStateFlow(initialActivities)
    val demoIncidents: StateFlow<List<DemoActivityItem>> = _demoIncidents.asStateFlow()

    fun updateIncidentStatus(id: String, newStatus: DemoIncidentStatus) {
        _demoIncidents.update { currentList ->
            currentList.map { item ->
                if (item.id == id) item.copy(status = newStatus) else item
            }
        }
    }

    fun getIncidentById(id: String): DemoActivityItem? {
        return _demoIncidents.value.find { it.id == id }
    }

    const val DEMO_LIVE_TRANSCRIPT = "Demo Preview: 'Emergency in building B, third floor. We observe light smoke near the main server rack and electrical panel. Power tripped across the wing.'"
    const val DEMO_CLASSIFICATION_TYPE = "Fire / Smoke Hazard"
    const val DEMO_CLASSIFICATION_URGENCY = "High"
    const val DEMO_ASSIGNED_DEPARTMENT = "Fire & Rescue / Facilities"
}
