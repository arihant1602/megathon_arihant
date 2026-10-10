package org.seedhecode.incidentassistant.ui.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import org.seedhecode.incidentassistant.feature.assistant.IncidentAssistantViewModel

private object Routes {
    const val HOME = "home"; const val REPORT = "report"; const val HISTORY = "history"; const val FORM = "form"
    const val QUESTIONS = "questions"; const val REVIEW = "review"; const val DESK = "desk"; const val DETAIL = "detail/{id}"
    fun detail(id: String) = "detail/$id"
}

private enum class Role { REPORTER, DESK }

/** Sentinel app shell: role choice, then the reporter tabs (home, voice report, history) or the facilities desk. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentinelApp(vm: IncidentAssistantViewModel, onMic: () -> Unit) {
    var role by rememberSaveable { mutableStateOf<Role?>(null) }
    val current = role
    if (current == null) { RoleScreen { role = it }; return }
    val state by vm.state.collectAsState()
    val reports by vm.reports.collectAsState()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val root = if (current == Role.REPORTER) Routes.HOME else Routes.DESK
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); vm.clearNotice() } }

    val title = when (route) {
        Routes.HOME -> "Sentinel AI"; Routes.REPORT -> "Report"; Routes.HISTORY -> "History"; Routes.FORM -> "Incident form"
        Routes.QUESTIONS -> "Questions"; Routes.REVIEW -> "Review and file"; Routes.DESK -> "Sentinel AI"; Routes.DETAIL -> "Report"; else -> "Sentinel AI"
    }
    val topLevel = route in setOf(Routes.HOME, Routes.REPORT, Routes.HISTORY, Routes.DESK)
    fun tab(target: String) = nav.navigate(target) { popUpTo(root) { saveState = true }; launchSingleTop = true; restoreState = true }
    fun start() { tab(Routes.REPORT); onMic() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { if (!topLevel) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    // Labeled actions: icon-only buttons here read as decoration.
                    if (route == Routes.REPORT) TextButton(vm::toggleSpeech, Modifier.heightIn(min = 48.dp)) {
                        Icon(if (state.speakQuestions) Icons.Default.VolumeUp else Icons.Default.VolumeOff, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.speakQuestions) "Voice on" else "Voice off", style = MaterialTheme.typography.labelLarge)
                    }
                    if (topLevel) TextButton({ vm.pause(); role = null }, Modifier.heightIn(min = 48.dp)) {
                        Text("Switch role", style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer))
        },
        bottomBar = {
            if (current == Role.REPORTER && route in setOf(Routes.HOME, Routes.REPORT, Routes.HISTORY)) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    listOf(Triple(Routes.HOME, "Home", Icons.Default.Home), Triple(Routes.REPORT, "Report", Icons.Default.Mic),
                        Triple(Routes.HISTORY, "History", Icons.Default.History)).forEach { (r, label, icon) ->
                        NavigationBarItem(selected = route == r, onClick = { if (route != r) tab(r) },
                            icon = { Icon(icon, null) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = root, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.HOME) { HomeScreen(state, reports, ::start, { nav.navigate(Routes.detail(it)) }, { tab(Routes.HISTORY) }) }
            composable(Routes.REPORT) {
                ReportScreen(state, onMic, vm::send, vm::done, vm::newReport, { nav.navigate(Routes.FORM) }, { nav.navigate(Routes.QUESTIONS) },
                    { nav.navigate(Routes.detail(it)) }, vm::load)
            }
            composable(Routes.HISTORY) { HistoryScreen(reports, { nav.navigate(Routes.detail(it)) }, ::start) }
            composable(Routes.FORM) { FormScreen(state, vm::editField) { nav.navigate(Routes.REVIEW) } }
            composable(Routes.QUESTIONS) { QuestionsScreen(state, vm::send) { nav.popBackStack() } }
            composable(Routes.REVIEW) {
                ReviewScreen(state, vm::done, { nav.popBackStack() }, { nav.navigate(Routes.detail(it)) }) {
                    vm.newReport(); tab(Routes.REPORT)
                }
            }
            composable(Routes.DESK) { DeskScreen(reports) { nav.navigate(Routes.detail(it)) } }
            composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                val report = reports.firstOrNull { it.id == id }
                ReportDetailScreen(report, desk = current == Role.DESK, reading = state.readingReportId == id,
                    onStatus = { vm.setStatus(id, it) }, onListen = { report?.let(vm::toggleReadAloud) })
                DisposableEffect(id) { onDispose { if (vm.state.value.readingReportId == id) report?.let(vm::toggleReadAloud) } }
            }
        }
    }
}

@Composable
private fun RoleScreen(onContinue: (Role) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(Role.REPORTER) }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sentinel AI", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text("Welcome. How are you using this device?", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        SelectionCard(Icons.Default.RecordVoiceOver, "Report an incident",
            "Talk to the assistant. It asks what's missing and files the report.",
            selected == Role.REPORTER) { selected = Role.REPORTER }
        SelectionCard(Icons.Default.Dashboard, "Facilities desk",
            "See filed reports by urgency and update their status.", selected == Role.DESK) { selected = Role.DESK }
        Spacer(Modifier.height(8.dp))
        PillButton("Continue", { onContinue(selected) })
        Text("Speech, classification and questions all run on this device. Reports stay on it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

