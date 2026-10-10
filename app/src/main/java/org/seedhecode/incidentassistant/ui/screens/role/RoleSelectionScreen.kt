package org.seedhecode.incidentassistant.ui.screens.role

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.seedhecode.incidentassistant.ui.session.AppRole

@Composable
fun RoleSelectionScreen(
    selectedRole: AppRole?,
    onSelectRole: (AppRole) -> Unit,
    onContinue: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SentinelMark()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("SENTINEL AI", style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.5.sp, color = colors.primary)
                    Text("ON-DEVICE INCIDENT INTELLIGENCE",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                        color = colors.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Welcome to Sentinel AI.", style = MaterialTheme.typography.displayMedium,
                    color = colors.onBackground)
                Text("Choose how you'd like to continue.", style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant)
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RoleCard(AppRole.EMPLOYEE, "I'm an Employee",
                    "Report incidents, record observations, and manage your reports.",
                    Icons.Default.PersonOutline, selectedRole == AppRole.EMPLOYEE, onSelectRole)
                RoleCard(AppRole.ADMINISTRATOR, "I'm an Administrator",
                    "Review incident activity, manage queues, and monitor departments.",
                    Icons.Default.Shield, selectedRole == AppRole.ADMINISTRATOR, onSelectRole)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onContinue, enabled = selectedRole != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp)) {
                    Text("Continue", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                        modifier = Modifier.size(20.dp))
                }
                Text("Admin Demo · Role selection is for demonstration only; it does not authenticate users.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("Private by design. Built for on-device intelligence.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RoleCard(
    role: AppRole, title: String, description: String, icon: ImageVector,
    selected: Boolean, onSelect: (AppRole) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "role press")
    val background by animateColorAsState(if (selected) colors.primaryContainer else colors.surface,
        label = "role background")
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant,
        label = "role border")
    Surface(
        modifier = Modifier.fillMaxWidth().scale(scale)
            .selectable(selected = selected, role = Role.RadioButton,
                interactionSource = interactionSource, indication = androidx.compose.material.ripple.rememberRipple(),
                onClick = { onSelect(role) })
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $description" +
                    if (role == AppRole.ADMINISTRATOR) " Admin Demo." else ""
            },
        shape = RoundedCornerShape(20.dp), color = background,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border), shadowElevation = if (selected) 2.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceVariant) {
                Icon(icon, contentDescription = null, tint = colors.primary,
                    modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null, modifier = Modifier.size(24.dp))
        }
    }
}

/** Small vector brand mark: a protective outline with a clear central signal. */
@Composable
private fun SentinelMark() {
    val navy = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(40.dp)) {
        val path = Path().apply {
            moveTo(size.width * .5f, size.height * .08f)
            lineTo(size.width * .86f, size.height * .22f)
            lineTo(size.width * .82f, size.height * .58f)
            quadraticBezierTo(size.width * .78f, size.height * .78f, size.width * .5f, size.height * .92f)
            quadraticBezierTo(size.width * .22f, size.height * .78f, size.width * .18f, size.height * .58f)
            lineTo(size.width * .14f, size.height * .22f)
            close()
        }
        drawPath(path, navy, style = Stroke(2.dp.toPx()))
        drawLine(navy, Offset(size.width * .5f, size.height * .3f),
            Offset(size.width * .5f, size.height * .62f), strokeWidth = 3.dp.toPx())
    }
}
