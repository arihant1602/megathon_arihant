package org.seedhecode.incidentassistant.ui.session

import androidx.lifecycle.ViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AppRole { EMPLOYEE, ADMINISTRATOR }

data class AppSessionState(
    val role: AppRole? = null,
    val selectedRole: AppRole? = null,
    val navigationKey: String = UUID.randomUUID().toString(),
    val pendingNavigation: String? = null
)

/** Demo presentation state only. This is not authentication or authorization. */
class AppSessionViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppSessionState())
    val state = _state.asStateFlow()

    // Used by the Activity to ignore a late microphone permission result after navigation.
    var captureScreenVisible: Boolean = false

    fun selectRole(role: AppRole) {
        _state.update { it.copy(selectedRole = role) }
    }

    fun continueWithRole() {
        if (_state.value.role != null) return
        val role = _state.value.selectedRole ?: return
        _state.update { it.copy(role = role, navigationKey = UUID.randomUUID().toString()) }
    }

    fun switchRole() {
        captureScreenVisible = false
        _state.value = AppSessionState()
    }

    fun queueNavigation(route: String): Boolean {
        if (_state.value.pendingNavigation != null) return false
        _state.update { it.copy(pendingNavigation = route) }
        captureScreenVisible = false
        return true
    }

    fun clearPendingNavigation() {
        _state.update { it.copy(pendingNavigation = null) }
    }
}
