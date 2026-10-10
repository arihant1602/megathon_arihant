package org.seedhecode.incidentassistant.ui.navigation

sealed class Screen(val route: String) {
    object RoleSelection : Screen("role_selection")
    object Home : Screen("home")
    object Report : Screen("report")
    object History : Screen("history")
    object Admin : Screen("admin")
    object IncidentForm : Screen("incident_form")
    object Clarification : Screen("clarification")
    object Review : Screen("review")

    // Dynamic detail routes
    object ReportDetail : Screen("report_detail/{incidentId}") {
        fun createRoute(incidentId: String) = "report_detail/$incidentId"
    }
    object AdminDetail : Screen("admin_detail/{incidentId}") {
        fun createRoute(incidentId: String) = "admin_detail/$incidentId"
    }
}
