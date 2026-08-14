package com.paperweight.os.ui.dashboard.vault

// Mirrors views/Vault.tsx: track/collection pricing, collection track
// management (add/remove/reorder), artwork upload, highlight toggle, and
// access tokens. Studio's modal-based editors become inline expandable
// panels here, matching the rest of this app's no-modal-system convention.
// "Access control" and "Add to vault" open Studio modals with no equivalent
// screen built yet — both are stubbed as a notice.

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.network.models.LibraryTrack
import com.paperweight.os.network.models.TokenAssignment
import com.paperweight.os.network.models.UpdateCollectionRequest
import com.paperweight.os.network.models.VaultPricingRequest
import com.paperweight.os.network.models.VaultProject
import com.paperweight.os.network.models.VaultToken
import com.paperweight.os.network.models.VaultTrackPrice
import com.paperweight.os.ui.components.DropdownField
import com.paperweight.os.ui.components.EmptyStateView
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val PAYMENT_TYPES = listOf("one_time" to "One-time", "recurring" to "Recurring")
private val RECURRING_INTERVALS = listOf("monthly" to "Monthly", "annually" to "Annually")
private val TOKEN_TIERS = listOf("subscriber" to "Subscriber", "pro" to "Pro", "all_access" to "All-access")

@Composable
fun VaultScreen(viewModel: VaultViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // "Add to vault": needs a one-time SAF tree grant over the SD card
    // (plan decision #10) before it can pick source files. If the grant is
    // already held, skip straight to the file picker.
    var pendingTreeGrant by rememberSaveable { mutableStateOf(false) }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.ingestTracks(uris)
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            viewModel.persistVaultTreeGrant(treeUri)
            if (pendingTreeGrant) {
                pendingTreeGrant = false
                filePickerLauncher.launch(arrayOf("audio/*"))
            }
        } else {
            pendingTreeGrant = false
            viewModel.notify("SD card folder access is required to add to the vault.")
        }
    }
    val onAddToVault: () -> Unit = {
        coroutineScope.launch {
            if (viewModel.hasVaultTreeAccess()) {
                filePickerLauncher.launch(arrayOf("audio/*"))
            } else {
                pendingTreeGrant = true
                treeLauncher.launch(null)
            }
        }
    }

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        var editingTrackId by rememberSaveable { mutableStateOf<Int?>(null) }
        var editingProjectId by rememberSaveable { mutableStateOf<Int?>(null) }
        var editingLocalTrackId by rememberSaveable { mutableStateOf<String?>(null) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Vault",
                    title = "Keep some things close.",
                    description = "Private works, subscriber previews, and the pieces that deserve a quieter room.",
                    action = {
                        OutlinedButton(onClick = { viewModel.notify("Access control settings aren't ported to this kiosk yet.") }) {
                            Icon(Icons.Outlined.Lock, contentDescription = null)
                            Text(text = "Access control", modifier = Modifier.padding(start = 8.dp))
                        }
                    },
                )
            }
            item {
                HeroPanel(
                    trackCount = data.trackPrices.size,
                    projectCount = data.projects.size,
                    onAddToVault = onAddToVault,
                )
            }
            item {
                Text(
                    text = "Your vault",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (data.localTracks.isEmpty()) {
                item {
                    Text(
                        text = "Nothing ingested yet — tap \"Add to vault\" to pick audio files from the device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(data.localTracks, key = { it.id }) { track ->
                    Column {
                        LocalVaultTrackRow(
                            track = track,
                            enabled = !data.actionInFlight,
                            onEditClick = { editingLocalTrackId = if (editingLocalTrackId == track.id) null else track.id },
                        )
                        if (editingLocalTrackId == track.id) {
                            LocalTrackPriceForm(
                                track = track,
                                enabled = !data.actionInFlight,
                                onCancel = { editingLocalTrackId = null },
                                onSave = { suggested, minimum, allowFree ->
                                    viewModel.saveLocalTrackPricing(track.id, suggested, minimum, allowFree)
                                    editingLocalTrackId = null
                                },
                            )
                        }
                    }
                }
            }
            if (!data.hasAnything) {
                item {
                    EmptyStateView(
                        icon = Icons.Outlined.Lock,
                        title = "Nothing priced yet",
                        body = "Set a price on a track or collection to add it to the vault.",
                    )
                }
            } else {
                if (data.projects.isNotEmpty()) {
                    item { Text(text = "Collections", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(data.projects) { project ->
                        Column {
                            ProjectRow(
                                project = project,
                                highlighted = data.highlight.highlight_type == "project" && data.highlight.highlight_id == project.id,
                                enabled = !data.actionInFlight,
                                onClick = { editingProjectId = if (editingProjectId == project.id) null else project.id },
                                onHighlightClick = { viewModel.toggleHighlight("project", project.id) },
                            )
                            if (editingProjectId == project.id) {
                                CollectionManagePanel(
                                    project = project,
                                    availableTracks = data.availableTracks,
                                    enabled = !data.actionInFlight,
                                    onCancel = { editingProjectId = null },
                                    onSavePricing = { viewModel.saveCollectionPricing(project, it) },
                                    onDelete = { viewModel.deleteCollection(project.id); editingProjectId = null },
                                    onAddTrack = { viewModel.addCollectionTrack(project.id, it) },
                                    onRemoveTrack = { viewModel.removeCollectionTrack(project.id, it) },
                                    onMoveTrack = { contentId, direction -> viewModel.moveCollectionTrack(project, contentId, direction) },
                                )
                            }
                        }
                    }
                }
                if (data.trackPrices.isNotEmpty()) {
                    item {
                        Text(
                            text = "Priced tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(data.trackPrices) { track ->
                        Column {
                            TrackRow(
                                track = track,
                                isUnpriced = false,
                                highlighted = data.highlight.highlight_type == "track" && data.highlight.highlight_id == track.content_id,
                                enabled = !data.actionInFlight,
                                onEditClick = { editingTrackId = if (editingTrackId == track.content_id) null else track.content_id },
                                onHighlightClick = { viewModel.toggleHighlight("track", track.content_id) },
                                onUploadArtwork = { viewModel.uploadArtwork(track.content_id, it) },
                            )
                            if (editingTrackId == track.content_id) {
                                TrackPriceForm(
                                    track = track,
                                    enabled = !data.actionInFlight,
                                    onCancel = { editingTrackId = null },
                                    onSave = {
                                        viewModel.saveTrackPricing(track.content_id, it)
                                        editingTrackId = null
                                    },
                                )
                            }
                        }
                    }
                }
                if (data.unpricedVaultTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Needs a price",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(data.unpricedVaultTracks) { track ->
                        Column {
                            TrackRow(
                                track = track,
                                isUnpriced = true,
                                highlighted = false,
                                enabled = !data.actionInFlight,
                                onEditClick = { editingTrackId = if (editingTrackId == track.content_id) null else track.content_id },
                                onHighlightClick = {},
                                onUploadArtwork = {},
                            )
                            if (editingTrackId == track.content_id) {
                                TrackPriceForm(
                                    track = track,
                                    enabled = !data.actionInFlight,
                                    onCancel = { editingTrackId = null },
                                    onSave = {
                                        viewModel.saveTrackPricing(track.content_id, it)
                                        editingTrackId = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                TokenManagerPanel(
                    tokens = data.tokens,
                    openTokenId = data.openTokenId,
                    assignments = data.tokenAssignments,
                    createdToken = data.createdToken,
                    actionInFlight = data.actionInFlight,
                    onCreate = viewModel::createToken,
                    onRevoke = viewModel::revokeToken,
                    onSetTier = viewModel::setTokenTier,
                    onToggleAssignments = viewModel::toggleTokenAssignments,
                    onAssign = viewModel::assignToken,
                    onUnassign = viewModel::unassignToken,
                )
            }
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "A little privacy, by design.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Vault links are encrypted and expire when you choose. Anyone with a link can listen, but downloads stay off unless you explicitly turn them on.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (data.actionMessage != null) {
                item {
                    Text(
                        text = data.actionMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPanel(trackCount: Int, projectCount: Int, onAddToVault: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Private vault", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "$trackCount priced ${if (trackCount == 1) "track" else "tracks"}, $projectCount ${if (projectCount == 1) "collection" else "collections"}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Button(onClick = onAddToVault) { Text("Add to vault") }
        }
    }
}

@Composable
private fun ProjectRow(project: VaultProject, highlighted: Boolean, enabled: Boolean, onClick: () -> Unit, onHighlightClick: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = project.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${project.items.size} ${if (project.items.size == 1) "track" else "tracks"} · ${formatPriceCents(project.suggested_price, project.allow_free != 0)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onHighlightClick, enabled = enabled) {
                Icon(Icons.Outlined.Star, contentDescription = "Highlight", tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onClick, enabled = enabled) { Text("Manage") }
        }
    }
}

@Composable
private fun TrackRow(
    track: VaultTrackPrice,
    isUnpriced: Boolean,
    highlighted: Boolean,
    enabled: Boolean,
    onEditClick: () -> Unit,
    onHighlightClick: () -> Unit,
    onUploadArtwork: (Uri) -> Unit,
) {
    val artworkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(onUploadArtwork) }
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = track.title ?: track.filename, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (isUnpriced) "Marked vault, no price set" else formatPriceCents(track.suggested_price, track.allow_free != 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isUnpriced) {
                IconButton(onClick = { artworkLauncher.launch("image/*") }, enabled = enabled) {
                    Icon(Icons.Outlined.Image, contentDescription = "Upload artwork")
                }
                IconButton(onClick = onHighlightClick, enabled = enabled) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = "Highlight",
                        tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = onEditClick, enabled = enabled) { Text(if (isUnpriced) "Set price" else "Edit price") }
        }
    }
}

@Composable
private fun PricingFieldsBody(
    suggestedPrice: String,
    onSuggestedPriceChange: (String) -> Unit,
    minimumPrice: String,
    onMinimumPriceChange: (String) -> Unit,
    allowFree: Boolean,
    onAllowFreeChange: (Boolean) -> Unit,
    paymentType: String,
    onPaymentTypeChange: (String) -> Unit,
    recurringInterval: String,
    onRecurringIntervalChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(value = suggestedPrice, onValueChange = onSuggestedPriceChange, label = { Text("Suggested price ($)") }, placeholder = { Text("5.00") }, modifier = Modifier.weight(1f))
        OutlinedTextField(value = minimumPrice, onValueChange = onMinimumPriceChange, label = { Text("Minimum price ($)") }, placeholder = { Text("1.00") }, modifier = Modifier.weight(1f))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(text = "Allow free / pay-what-you-want", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = allowFree, onCheckedChange = onAllowFreeChange)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        DropdownField(label = "Payment type", options = PAYMENT_TYPES, selected = paymentType, onSelect = onPaymentTypeChange, modifier = Modifier.weight(1f))
        if (paymentType == "recurring") {
            DropdownField(label = "Interval", options = RECURRING_INTERVALS, selected = recurringInterval, onSelect = onRecurringIntervalChange, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrackPriceForm(track: VaultTrackPrice, enabled: Boolean, onCancel: () -> Unit, onSave: (VaultPricingRequest) -> Unit) {
    var suggested by rememberSaveable(track.content_id) { mutableStateOf(centsToDollarText(track.suggested_price)) }
    var minimum by rememberSaveable(track.content_id) { mutableStateOf(centsToDollarText(track.minimum_price)) }
    var allowFree by rememberSaveable(track.content_id) { mutableStateOf(track.allow_free != 0) }
    var paymentType by rememberSaveable(track.content_id) { mutableStateOf(track.payment_type) }
    var recurringInterval by rememberSaveable(track.content_id) { mutableStateOf(track.recurring_interval ?: "monthly") }

    PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(text = "Price “${track.title ?: track.filename}”", style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.padding(top = 12.dp)) {
            PricingFieldsBody(
                suggested, { suggested = it }, minimum, { minimum = it },
                allowFree, { allowFree = it }, paymentType, { paymentType = it },
                recurringInterval, { recurringInterval = it },
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) { Text("Cancel") }
            Button(
                onClick = {
                    onSave(
                        VaultPricingRequest(
                            suggested_price = dollarsToCents(suggested),
                            minimum_price = dollarsToCents(minimum),
                            allow_free = allowFree,
                            payment_type = paymentType,
                            recurring_interval = if (paymentType == "recurring") recurringInterval else null,
                        )
                    )
                },
                enabled = enabled,
            ) { Text("Save pricing") }
        }
    }
}

@Composable
private fun CollectionManagePanel(
    project: VaultProject,
    availableTracks: List<LibraryTrack>,
    enabled: Boolean,
    onCancel: () -> Unit,
    onSavePricing: (UpdateCollectionRequest) -> Unit,
    onDelete: () -> Unit,
    onAddTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
) {
    var suggested by rememberSaveable(project.id) { mutableStateOf(centsToDollarText(project.suggested_price)) }
    var minimum by rememberSaveable(project.id) { mutableStateOf(centsToDollarText(project.minimum_price)) }
    var allowFree by rememberSaveable(project.id) { mutableStateOf(project.allow_free != 0) }
    var paymentType by rememberSaveable(project.id) { mutableStateOf(project.payment_type) }
    var recurringInterval by rememberSaveable(project.id) { mutableStateOf(project.recurring_interval ?: "monthly") }
    var addTrackId by rememberSaveable(project.id) { mutableStateOf<Int?>(null) }
    var confirmingDelete by rememberSaveable(project.id) { mutableStateOf(false) }

    val alreadyInProject = project.items.map { it.content_id }.toSet()
    val addOptions = availableTracks.filter { it.id !in alreadyInProject }
    val addTrackOptions: List<Pair<Int?, String>> = addOptions.map { it.id to (it.title ?: "Untitled") }

    PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(text = "Manage “${project.name}”", style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.padding(top = 12.dp)) {
            PricingFieldsBody(
                suggested, { suggested = it }, minimum, { minimum = it },
                allowFree, { allowFree = it }, paymentType, { paymentType = it },
                recurringInterval, { recurringInterval = it },
            )
        }
        Text(
            text = "Tracks in this collection",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp),
        )
        if (project.items.isEmpty()) {
            Text(
                text = "No tracks yet — add one below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                project.items.forEach { item ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = item.title ?: item.filename, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onMoveTrack(item.content_id, -1) }, enabled = enabled) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(onClick = { onMoveTrack(item.content_id, 1) }, enabled = enabled) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move down")
                        }
                        IconButton(onClick = { onRemoveTrack(item.content_id) }, enabled = enabled) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        if (addOptions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                DropdownField(
                    label = "Add a track from your library",
                    options = addTrackOptions,
                    selected = addTrackId,
                    onSelect = { addTrackId = it },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { addTrackId?.let(onAddTrack); addTrackId = null }, enabled = addTrackId != null && enabled) { Text("Add") }
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (confirmingDelete) {
                Text(
                    text = "Delete this collection?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { confirmingDelete = false }) { Text("No") }
                TextButton(onClick = onDelete) { Text("Yes, delete") }
            } else {
                OutlinedButton(onClick = { confirmingDelete = true }, enabled = enabled) {
                    Text(text = "Delete collection", color = MaterialTheme.colorScheme.error)
                }
                Row {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) { Text("Close") }
                    Button(
                        onClick = {
                            onSavePricing(
                                UpdateCollectionRequest(
                                    name = project.name,
                                    description = project.description,
                                    suggested_price = dollarsToCents(suggested),
                                    minimum_price = dollarsToCents(minimum),
                                    allow_free = allowFree,
                                    payment_type = paymentType,
                                    recurring_interval = if (paymentType == "recurring") recurringInterval else null,
                                )
                            )
                        },
                        enabled = enabled,
                    ) { Text("Save pricing") }
                }
            }
        }
    }
}

@Composable
private fun TokenManagerPanel(
    tokens: List<VaultToken>,
    openTokenId: Int?,
    assignments: List<TokenAssignment>,
    createdToken: String?,
    actionInFlight: Boolean,
    onCreate: (String, String, String) -> Unit,
    onRevoke: (Int) -> Unit,
    onSetTier: (Int, String) -> Unit,
    onToggleAssignments: (Int) -> Unit,
    onAssign: (Int, String) -> Unit,
    onUnassign: (Int, Int) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var tier by rememberSaveable { mutableStateOf("subscriber") }
    var email by rememberSaveable { mutableStateOf("") }

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Access tokens", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Token label") }, placeholder = { Text("July subscriber comp") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DropdownField(label = "Tier", options = TOKEN_TIERS, selected = tier, onSelect = { tier = it }, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Assign to account") },
                    placeholder = { Text("listener@example.com") },
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = {
                    onCreate(label, tier, email)
                    label = ""
                    email = ""
                },
                enabled = label.isNotBlank() && !actionInFlight,
            ) { Text("Create") }
        }
        if (createdToken != null) {
            PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), contentPadding = PaddingValues(14.dp)) {
                Text(
                    text = "Share once — this token will not be shown again.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = createdToken, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (tokens.isEmpty()) {
            Text(
                text = "No access tokens yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tokens.forEach { token ->
                    TokenRow(
                        token = token,
                        open = openTokenId == token.id,
                        assignments = if (openTokenId == token.id) assignments else emptyList(),
                        actionInFlight = actionInFlight,
                        onRevoke = { onRevoke(token.id) },
                        onSetTier = { onSetTier(token.id, it) },
                        onToggle = { onToggleAssignments(token.id) },
                        onAssign = { onAssign(token.id, it) },
                        onUnassign = { onUnassign(token.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenRow(
    token: VaultToken,
    open: Boolean,
    assignments: List<TokenAssignment>,
    actionInFlight: Boolean,
    onRevoke: () -> Unit,
    onSetTier: (String) -> Unit,
    onToggle: () -> Unit,
    onAssign: (String) -> Unit,
    onUnassign: (Int) -> Unit,
) {
    var assignEmail by rememberSaveable(token.id) { mutableStateOf("") }

    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = token.label ?: "Untitled token", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${token.last_used?.let { "used ${it.take(10)}" } ?: "unused"} · ${if (token.is_active) "active" else "revoked"}" +
                        (token.scope_type?.let { " · $it #${token.scope_id}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            if (token.is_active) {
                DropdownField(label = "Tier", options = TOKEN_TIERS, selected = token.tier, onSelect = onSetTier, modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = onToggle) { Text("Assignments") }
            OutlinedButton(onClick = onRevoke, enabled = token.is_active && !actionInFlight) {
                Text(text = "Revoke", color = MaterialTheme.colorScheme.error)
            }
        }
        if (open) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = assignEmail,
                        onValueChange = { assignEmail = it },
                        label = { Text("listener@example.com") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { onAssign(assignEmail); assignEmail = "" }, enabled = assignEmail.isNotBlank() && !actionInFlight) { Text("Assign") }
                }
                if (assignments.isEmpty()) {
                    Text(
                        text = "No account assignments yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        assignments.forEach { assignment ->
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = assignment.email, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onUnassign(assignment.id) }) { Text(text = "Remove", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalVaultTrackRow(track: VaultTrackEntity, enabled: Boolean, onEditClick: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = track.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${track.artist ?: "Unknown artist"} · ${formatDurationMs(track.durationMs)} · " +
                        formatPriceCents(track.suggestedPriceCents, track.allowFree),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onEditClick, enabled = enabled) { Text("Edit price") }
        }
    }
}

@Composable
private fun LocalTrackPriceForm(track: VaultTrackEntity, enabled: Boolean, onCancel: () -> Unit, onSave: (Int, Int, Boolean) -> Unit) {
    var suggested by rememberSaveable(track.id) { mutableStateOf(centsToDollarText(track.suggestedPriceCents)) }
    var minimum by rememberSaveable(track.id) { mutableStateOf(centsToDollarText(track.minimumPriceCents)) }
    var allowFree by rememberSaveable(track.id) { mutableStateOf(track.allowFree) }

    PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(text = "Price “${track.title}”", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            OutlinedTextField(value = suggested, onValueChange = { suggested = it }, label = { Text("Suggested price ($)") }, placeholder = { Text("5.00") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = minimum, onValueChange = { minimum = it }, label = { Text("Minimum price ($)") }, placeholder = { Text("1.00") }, modifier = Modifier.weight(1f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(text = "Allow free / pay-what-you-want", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Switch(checked = allowFree, onCheckedChange = { allowFree = it })
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) { Text("Cancel") }
            Button(
                onClick = { onSave(dollarsToCents(suggested), dollarsToCents(minimum), allowFree) },
                enabled = enabled,
            ) { Text("Save pricing") }
        }
    }
}

private fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatPriceCents(cents: Int, allowFree: Boolean): String {
    if (allowFree && cents == 0) return "Free / pay-what-you-want"
    return "$%.2f".format(cents / 100.0)
}

private fun dollarsToCents(value: String): Int = (value.toDoubleOrNull()?.let { (it * 100).roundToInt() } ?: 0).coerceAtLeast(0)

private fun centsToDollarText(cents: Int): String = if (cents % 100 == 0) (cents / 100).toString() else "%.2f".format(cents / 100.0)
