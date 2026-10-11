package org.seedhecode.incidentassistant.ui.screens.incident

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class IncidentCategory(val displayName: String, val assignedDepartment: String) {
    MEDICAL("Medical Emergency", "Medical Response / EMS"),
    FIRE("Fire / Smoke", "Fire & Rescue / Facilities"),
    SECURITY("Security Incident", "Security Operations"),
    MAINTENANCE("Infrastructure / Maintenance", "Facilities & Maintenance"),
    GENERAL("General Assistance", "General Operations")
}

enum class FieldState {
    KNOWN, UNKNOWN, MISSING
}

data class ClarificationQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    var answer: String = ""
)

data class IncidentDraftState(
    val category: IncidentCategory = IncidentCategory.FIRE,
    val location: String = "Building B, Floor 3, Server Corridor",
    val urgency: String = "High",
    
    // Category specific fields
    val medicalPatientCondition: String = "Stable",
    val medicalAffectedPeople: String = "1-3 people",
    
    val fireSmokeVisible: String = "Visible smoke near electrical rack",
    val fireEvacuationStatus: String = "Evacuation in progress",
    
    val securityDescription: String = "",
    val securityPeopleInvolved: String = "Unknown",
    
    val maintenanceIssueType: String = "",
    val maintenanceEquipment: String = "",
    
    val generalDescription: String = "",

    // Clarification state
    val clarificationQuestions: List<ClarificationQuestion> = emptyList(),
    
    // Submission state
    val isSubmitted: Boolean = false,
    val submissionMessage: String = ""
)

class IncidentDraftViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(IncidentDraftState())
    val uiState: StateFlow<IncidentDraftState> = _uiState.asStateFlow()

    init {
        generateClarificationQuestions()
    }

    fun setCategory(newCategory: IncidentCategory) {
        _uiState.update { it.copy(category = newCategory) }
        generateClarificationQuestions()
    }

    fun setLocation(location: String) {
        _uiState.update { it.copy(location = location) }
    }

    fun setUrgency(urgency: String) {
        _uiState.update { it.copy(urgency = urgency) }
    }

    fun updateField(key: String, value: String) {
        _uiState.update { state ->
            when (state.category) {
                IncidentCategory.MEDICAL -> when (key) {
                    "condition" -> state.copy(medicalPatientCondition = value)
                    "affected" -> state.copy(medicalAffectedPeople = value)
                    else -> state
                }
                IncidentCategory.FIRE -> when (key) {
                    "smoke" -> state.copy(fireSmokeVisible = value)
                    "evacuation" -> state.copy(fireEvacuationStatus = value)
                    else -> state
                }
                IncidentCategory.SECURITY -> when (key) {
                    "description" -> state.copy(securityDescription = value)
                    "people" -> state.copy(securityPeopleInvolved = value)
                    else -> state
                }
                IncidentCategory.MAINTENANCE -> when (key) {
                    "issue" -> state.copy(maintenanceIssueType = value)
                    "equipment" -> state.copy(maintenanceEquipment = value)
                    else -> state
                }
                IncidentCategory.GENERAL -> when (key) {
                    "description" -> state.copy(generalDescription = value)
                    else -> state
                }
            }
        }
    }

    fun answerClarification(questionId: String, answer: String) {
        _uiState.update { state ->
            val updated = state.clarificationQuestions.map { q ->
                if (q.id == questionId) q.copy(answer = answer) else q
            }
            state.copy(clarificationQuestions = updated)
        }
    }

    private fun generateClarificationQuestions() {
        val questions = when (_uiState.value.category) {
            IncidentCategory.MEDICAL -> listOf(
                ClarificationQuestion("m1", "Are any patients unconscious or unresponsive?", listOf("Yes", "No", "Unknown")),
                ClarificationQuestion("m2", "Is specialized trauma equipment required?", listOf("Oxygen / AED", "First Aid Only", "Unknown"))
            )
            IncidentCategory.FIRE -> listOf(
                ClarificationQuestion("f1", "Is the fire actively spreading or contained?", listOf("Actively Spreading", "Contained / Small", "Unknown")),
                ClarificationQuestion("f2", "Are sprinkler systems activated?", listOf("Yes", "No", "Unknown"))
            )
            IncidentCategory.SECURITY -> listOf(
                ClarificationQuestion("s1", "Are intruders currently armed or hostile?", listOf("Yes", "No", "Unknown")),
                ClarificationQuestion("s2", "Is restricted data or hardware at immediate risk?", listOf("Yes", "No", "Unknown"))
            )
            IncidentCategory.MAINTENANCE -> listOf(
                ClarificationQuestion("ma1", "Does this outage affect critical power or cooling?", listOf("Yes", "No", "Unknown")),
                ClarificationQuestion("ma2", "Is emergency maintenance personnel dispatched?", listOf("Yes", "No", "Unknown"))
            )
            IncidentCategory.GENERAL -> listOf(
                ClarificationQuestion("g1", "Does this require immediate supervisory intervention?", listOf("Yes", "No", "Unknown"))
            )
        }
        _uiState.update { it.copy(clarificationQuestions = questions) }
    }

    fun submitDemoReport() {
        _uiState.update {
            it.copy(
                isSubmitted = true,
                submissionMessage = "Demo submission completed — no report was transmitted."
            )
        }
    }

    fun resetDraft() {
        _uiState.value = IncidentDraftState()
        generateClarificationQuestions()
    }
}
