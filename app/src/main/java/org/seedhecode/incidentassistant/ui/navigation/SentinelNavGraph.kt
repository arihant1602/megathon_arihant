package org.seedhecode.incidentassistant.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import org.seedhecode.incidentassistant.feature.report.CaptureState
import org.seedhecode.incidentassistant.feature.report.ReportViewModel
import org.seedhecode.incidentassistant.ui.components.SentinelBottomNavigation
import org.seedhecode.incidentassistant.ui.components.SentinelTopBar
import org.seedhecode.incidentassistant.ui.screens.admin.*
import org.seedhecode.incidentassistant.ui.screens.clarification.ClarificationScreen
import org.seedhecode.incidentassistant.ui.screens.history.*
import org.seedhecode.incidentassistant.ui.screens.home.HomeScreen
import org.seedhecode.incidentassistant.ui.screens.incident.*
import org.seedhecode.incidentassistant.ui.screens.review.ReviewScreen
import org.seedhecode.incidentassistant.ui.screens.role.RoleSelectionScreen
import org.seedhecode.incidentassistant.ui.screens.voice.VoiceReportingScreen
import org.seedhecode.incidentassistant.ui.session.AppRole
import org.seedhecode.incidentassistant.ui.session.AppSessionViewModel

private const val BACK = "__back"
private const val SWITCH_ROLE = "__switch_role"
private const val TAB_PREFIX = "__tab:"

@Composable
fun SentinelNavGraph(
    viewModel: ReportViewModel,
    sessionViewModel: AppSessionViewModel,
    onRequestPermission: () -> Unit,
    permissionRationale: Boolean,
    settingsRequired: Boolean,
    onDismissDialog: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val session by sessionViewModel.state.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    // Activity-scoped owners stay outside the role-keyed navigation tree.
    val draft: IncidentDraftViewModel = viewModel()
    val history: HistoryViewModel = viewModel()
    val admin: AdminViewModel = viewModel()

    // A new controller and graph per role session also discard saved tab back stacks.
    // The key lives in the ViewModel, so configuration changes restore the current graph.
    key(session.navigationKey) {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val role = session.role
        val root = when (role) {
            AppRole.EMPLOYEE -> Screen.Home.route
            AppRole.ADMINISTRATOR -> Screen.Admin.route
            null -> Screen.RoleSelection.route
        }
        val waiting = session.pendingNavigation != null
        val saving = uiState.captureState == CaptureState.SAVING
        var navigationError by remember { mutableStateOf<String?>(null) }

        fun performNavigation(target: String) {
            sessionViewModel.captureScreenVisible = false
            onDismissDialog()
            when {
                target == SWITCH_ROLE -> sessionViewModel.switchRole()
                target == BACK -> navController.popBackStack()
                target.startsWith(TAB_PREFIX) -> navController.navigate(target.removePrefix(TAB_PREFIX)) {
                    popUpTo(root) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                else -> navController.navigate(target) { launchSingleTop = true }
            }
        }

        fun requestNavigation(target: String) {
            // Read live ViewModel state, rather than waiting for recomposition after a tap.
            if (sessionViewModel.state.value.pendingNavigation != null) return
            viewModel.stopPlayback()
            val capture = viewModel.uiState.value.captureState
            if (capture == CaptureState.IDLE) {
                performNavigation(target)
            } else if (sessionViewModel.queueNavigation(target)) {
                if (capture != CaptureState.SAVING) viewModel.forceStopCapture()
            }
        }

        SideEffect {
            sessionViewModel.captureScreenVisible = role == AppRole.EMPLOYEE &&
                route == Screen.Report.route && !waiting
        }
        LaunchedEffect(session.pendingNavigation, uiState.captureState) {
            val target = session.pendingNavigation
            if (target != null && uiState.captureState == CaptureState.IDLE) {
                // IDLE is emitted by the existing onSaved/onError completion callbacks.
                sessionViewModel.clearPendingNavigation()
                if (uiState.errorMessage != null) navigationError = uiState.errorMessage
                else performNavigation(target)
            }
        }

        val title = when (route) {
            Screen.Home.route -> "Sentinel AI"
            Screen.Report.route -> "Voice Reporting"
            Screen.History.route -> if (role == AppRole.ADMINISTRATOR) "Admin Demo · History" else "History"
            Screen.Admin.route -> "Admin Demo"
            Screen.IncidentForm.route -> "Incident Form"
            Screen.Clarification.route -> "Clarification"
            Screen.Review.route -> "Review & Confirm"
            Screen.AdminDetail.route -> "Admin Demo · Details"
            Screen.ReportDetail.route -> "Report Details"
            else -> "Sentinel AI"
        }
        Scaffold(
            topBar = {
                if (role != null && route != Screen.Admin.route) {
                    SentinelTopBar(title, { requestNavigation(SWITCH_ROLE) }, !waiting)
                }
            },
            bottomBar = {
                if (role != null && route in listOf(root, Screen.Report.route, Screen.History.route)) {
                    SentinelBottomNavigation(route, role, !waiting) { target ->
                        if (target != route) requestNavigation(TAB_PREFIX + target)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                NavHost(navController, startDestination = root, modifier = Modifier.fillMaxSize()) {
                    if (role == null) {
                        composable(Screen.RoleSelection.route) {
                            RoleSelectionScreen(session.selectedRole, sessionViewModel::selectRole,
                                sessionViewModel::continueWithRole)
                        }
                    }
                    if (role == AppRole.EMPLOYEE) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                { requestNavigation(Screen.Report.route) },
                                { requestNavigation(TAB_PREFIX + Screen.History.route) },
                                { requestNavigation(Screen.IncidentForm.route) }
                            )
                        }
                        composable(Screen.Report.route) {
                            VoiceReportingScreen(
                                uiState = uiState,
                                navigationPending = waiting,
                                onStartCapture = { if (!waiting) onRequestPermission() },
                                onStopCapture = {
                                    if (viewModel.uiState.value.captureState == CaptureState.RECORDING)
                                        viewModel.stopCapture()
                                },
                                onPlay = { viewModel.play(it) }, onDelete = { viewModel.delete(it) },
                                onRequestPermission = onRequestPermission,
                                permissionRationale = permissionRationale, settingsRequired = settingsRequired,
                                onDismissDialog = onDismissDialog, onOpenSettings = onOpenSettings,
                                onNavigateToForm = { requestNavigation(Screen.IncidentForm.route) }
                            )
                        }
                        composable(Screen.IncidentForm.route) {
                            IncidentFormScreen(draft) { requestNavigation(Screen.Clarification.route) }
                        }
                        composable(Screen.Clarification.route) {
                            ClarificationScreen(draft) { requestNavigation(Screen.Review.route) }
                        }
                        composable(Screen.Review.route) {
                            ReviewScreen(draft,
                                onEditForm = { navController.popBackStack(Screen.IncidentForm.route, false) },
                                onHome = {
                                    draft.resetDraft()
                                    navController.navigate(root) { popUpTo(root); launchSingleTop = true }
                                })
                        }
                    }
                    if (role == AppRole.ADMINISTRATOR) {
                        composable(Screen.Admin.route) {
                            AdminScreen(
                                viewModel = admin,
                                onSwitchRole = { requestNavigation(SWITCH_ROLE) },
                                navigationEnabled = !waiting
                            ) { requestNavigation(Screen.AdminDetail.createRoute(it)) }
                        }
                        composable(Screen.AdminDetail.route,
                            arguments = listOf(navArgument("incidentId") { type = NavType.StringType })) { detail ->
                            AdminDetailScreen(detail.arguments?.getString("incidentId").orEmpty(), admin) {
                                requestNavigation(BACK)
                            }
                        }
                    }
                    if (role != null) {
                        composable(Screen.History.route) {
                            HistoryScreen(history, uiState.recordings, uiState.playingFile,
                                uiState.captureState == CaptureState.IDLE,
                                { viewModel.play(it) }, { viewModel.delete(it) },
                                { requestNavigation(Screen.ReportDetail.createRoute(it)) })
                        }
                        composable(Screen.ReportDetail.route,
                            arguments = listOf(navArgument("incidentId") { type = NavType.StringType })) { detail ->
                            ReportDetailScreen(detail.arguments?.getString("incidentId").orEmpty()) {
                                requestNavigation(BACK)
                            }
                        }
                    }
                }
                // Registered after NavHost so this handler takes precedence while capturing/saving.
                BackHandler(enabled = role != null && (route == Screen.Report.route || waiting || saving)) {
                    if (!waiting) requestNavigation(BACK)
                }
                if (waiting) {
                    Surface(Modifier.align(Alignment.Center).padding(24.dp),
                        shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
                        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("Saving your recording…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        navigationError?.let { error ->
            AlertDialog(onDismissRequest = { navigationError = null },
                title = { Text("Recording could not be saved") }, text = { Text(error) },
                confirmButton = { TextButton(onClick = { navigationError = null }) { Text("OK") } })
        }
    }
}
