package com.paperweight.os.ui.dashboard.audience

// Mirrors views/AudienceView.tsx: today's insights, audience memory
// search/segments, marketing contacts, automations, participation (polls +
// requests), radio-host mode, and external catalog search/import. Studio's
// two-column desktop layout collapses to a single stacked column here.

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.network.models.AudienceContact
import com.paperweight.os.network.models.AudienceInsight
import com.paperweight.os.network.models.AudiencePerson
import com.paperweight.os.network.models.AudienceSegment
import com.paperweight.os.network.models.AutomationRule
import com.paperweight.os.network.models.AutomationRun
import com.paperweight.os.network.models.Automations
import com.paperweight.os.network.models.ExternalSearchItem
import com.paperweight.os.network.models.MarketingContacts
import com.paperweight.os.network.models.ParticipationRequestItem
import com.paperweight.os.network.models.Poll
import com.paperweight.os.network.models.RadioHostStatus
import com.paperweight.os.ui.components.DropdownField
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val EXTERNAL_PLATFORMS = listOf("youtube" to "YouTube", "soundcloud" to "SoundCloud", "bandcamp" to "Bandcamp")
private val AUTOMATION_MODES = listOf("draft" to "Recommend", "automatic" to "Automatic")
private val WHEN_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a")

@Composable
fun AudienceScreen(viewModel: AudienceViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        var search by rememberSaveable { mutableStateOf("") }
        var selectedSegment by rememberSaveable { mutableStateOf<String?>(null) }
        var pollQuestion by rememberSaveable { mutableStateOf("") }
        var pollOptions by rememberSaveable { mutableStateOf("") }
        var externalPlatform by rememberSaveable { mutableStateOf("youtube") }
        var externalQuery by rememberSaveable { mutableStateOf("") }

        LaunchedEffect(search, selectedSegment) { viewModel.loadPeople(search, selectedSegment) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Audience",
                    title = "Work the listener relationship.",
                    description = "Audience memory, automations, participation, radio-host mode, and station search imports.",
                )
            }
            item { TodayPanel(outcomes = data.outcomes, insights = data.insights) }
            item {
                AudienceMemoryPanel(
                    search = search,
                    onSearchChange = { search = it; selectedSegment = null },
                    segments = data.segments,
                    selectedSegment = selectedSegment,
                    onSegmentSelect = { selectedSegment = it },
                    people = data.people,
                )
            }
            item { MarketingContactsPanel(data.contacts) }
            item {
                AutomationsPanel(
                    automations = data.automations,
                    actionInFlight = data.actionInFlight,
                    onTogglePaused = viewModel::toggleAutomationsPaused,
                    onSweep = viewModel::sweepAutomations,
                    onSetRuleEnabled = viewModel::setRuleEnabled,
                    onSetRuleMode = viewModel::setRuleMode,
                    onSendRun = viewModel::sendAutomationRun,
                )
            }
            item {
                ParticipationPanel(
                    polls = data.polls,
                    requests = data.requests,
                    actionInFlight = data.actionInFlight,
                    question = pollQuestion,
                    onQuestionChange = { pollQuestion = it },
                    options = pollOptions,
                    onOptionsChange = { pollOptions = it },
                    onCreatePoll = {
                        viewModel.createPoll(pollQuestion, pollOptions.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                        pollQuestion = ""
                        pollOptions = ""
                    },
                    onTogglePollStatus = viewModel::togglePollStatus,
                    onUpdateRequestStatus = viewModel::updateRequestStatus,
                )
            }
            item {
                RadioHostPanel(
                    creatorType = data.creatorType,
                    radioHost = data.radioHost,
                    actionInFlight = data.actionInFlight,
                    onToggle = viewModel::toggleRadioHost,
                )
            }
            item {
                ExternalSearchPanel(
                    platform = externalPlatform,
                    onPlatformChange = { externalPlatform = it },
                    query = externalQuery,
                    onQueryChange = { externalQuery = it },
                    onSearch = { viewModel.runExternalSearch(externalPlatform, externalQuery) },
                    searching = data.searchingExternal,
                    results = data.externalResults,
                    actionInFlight = data.actionInFlight,
                    onImport = viewModel::importExternal,
                )
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
private fun TodayPanel(outcomes: Map<String, Int>, insights: List<AudienceInsight>) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Today", style = MaterialTheme.typography.titleMedium)
        if (outcomes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                outcomes.entries.take(4).forEach { (key, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = value.toString(), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = spacedOutcomeLabel(key),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (insights.isEmpty()) {
            Text(
                text = "No daily interventions right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                insights.forEach { insight ->
                    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                        Text(
                            text = (insight.eyebrow ?: insight.tone ?: "Insight").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(text = insight.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            text = insight.body,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun spacedOutcomeLabel(key: String): String =
    key.replace(Regex("[A-Z]")) { " ${it.value}" }.replaceFirstChar { it.uppercase() }

@Composable
private fun AudienceMemoryPanel(
    search: String,
    onSearchChange: (String) -> Unit,
    segments: List<AudienceSegment>,
    selectedSegment: String?,
    onSegmentSelect: (String?) -> Unit,
    people: List<AudiencePerson>,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Audience memory", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text("Search people") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
        ) {
            SegmentChip(label = "All", selected = selectedSegment == null, onClick = { onSegmentSelect(null) })
            segments.forEach { segment ->
                SegmentChip(
                    label = "${segment.label} ${segment.count}",
                    selected = selectedSegment == segment.key,
                    onClick = { onSegmentSelect(segment.key) },
                )
            }
        }
        if (people.isEmpty()) {
            Text(
                text = "No listeners match this view yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                people.forEach { person -> PersonRow(person) }
            }
        }
    }
}

@Composable
private fun SegmentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Text(text = label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PersonRow(person: AudiencePerson) {
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Text(text = person.display_name ?: person.email ?: "Listener", style = MaterialTheme.typography.bodyMedium)
        val support = if (person.active_subscriptions > 0) "Subscriber" else formatCents(person.purchase_cents)
        Text(
            text = "${person.listen_count} sessions · ${person.listen_seconds / 60} minutes · $support",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val favorite = person.favorite_title?.let { "Favorite: $it · " } ?: ""
        Text(
            text = favorite + "Last seen ${formatWhen(person.last_listen_at ?: person.last_seen_at)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MarketingContactsPanel(contacts: MarketingContacts) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Marketing contacts", style = MaterialTheme.typography.titleMedium)
        val bySourceText = if (contacts.bySource.isNotEmpty()) {
            " — " + contacts.bySource.entries.joinToString(", ") { (source, count) -> "$count via ${sourceLabel(source)}" }
        } else ""
        Text(
            text = "${contacts.total} people opted in to updates$bySourceText.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (contacts.contacts.isEmpty()) {
            Text(
                text = "No opted-in contacts yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                contacts.contacts.forEach { contact -> ContactRow(contact) }
            }
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "listener_profile" -> "Listener account"
    "download_lead" -> "Download lead"
    else -> source
}

@Composable
private fun ContactRow(contact: AudienceContact) {
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Text(text = contact.name ?: contact.email, style = MaterialTheme.typography.bodyMedium)
        if (contact.name != null) {
            Text(text = contact.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = "${sourceLabel(contact.source)} · ${formatWhen(contact.created_at)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AutomationsPanel(
    automations: Automations,
    actionInFlight: Boolean,
    onTogglePaused: (Boolean) -> Unit,
    onSweep: () -> Unit,
    onSetRuleEnabled: (Int, Boolean) -> Unit,
    onSetRuleMode: (Int, String) -> Unit,
    onSendRun: (Int) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Automations", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onSweep, enabled = !actionInFlight) { Text("Run sweep") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text(text = "Pause automations", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = automations.paused, onCheckedChange = onTogglePaused, enabled = !actionInFlight)
        }
        if (automations.rules.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                automations.rules.forEach { rule -> RuleRow(rule, actionInFlight, onSetRuleEnabled, onSetRuleMode) }
            }
        }
        if (automations.runs.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                automations.runs.take(8).forEach { run -> RunRow(run, actionInFlight, onSendRun) }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: AutomationRule, actionInFlight: Boolean, onSetEnabled: (Int, Boolean) -> Unit, onSetMode: (Int, String) -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = rule.name, style = MaterialTheme.typography.bodyMedium)
                Text(text = rule.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Trigger: ${rule.trigger}" + if (rule.marketing) " · consent required" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = { onSetEnabled(rule.id, it) }, enabled = !actionInFlight)
        }
        DropdownField(
            label = "Mode",
            options = AUTOMATION_MODES,
            selected = rule.mode,
            onSelect = { onSetMode(rule.id, it) },
            enabled = !actionInFlight,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

@Composable
private fun RunRow(run: AutomationRun, actionInFlight: Boolean, onSend: (Int) -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = run.display_name ?: run.template_key?.replace('_', ' ') ?: "Automation", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = run.explanation ?: run.last_error ?: run.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (run.status == "recommended") {
                Button(onClick = { onSend(run.id) }, enabled = !actionInFlight) {
                    Icon(Icons.Outlined.Send, contentDescription = null)
                    Text(text = "Send", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ParticipationPanel(
    polls: List<Poll>,
    requests: List<ParticipationRequestItem>,
    actionInFlight: Boolean,
    question: String,
    onQuestionChange: (String) -> Unit,
    options: String,
    onOptionsChange: (String) -> Unit,
    onCreatePoll: () -> Unit,
    onTogglePollStatus: (Poll) -> Unit,
    onUpdateRequestStatus: (Int, String) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Participation", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            label = { Text("Poll question") },
            placeholder = { Text("What should play next?") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedTextField(
            value = options,
            onValueChange = onOptionsChange,
            label = { Text("Options") },
            placeholder = { Text("Song A, Song B, Song C") },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        Button(onClick = onCreatePoll, enabled = !actionInFlight && question.isNotBlank(), modifier = Modifier.padding(top = 10.dp)) {
            Text("Create poll")
        }
        if (polls.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                polls.forEach { poll -> PollRow(poll, actionInFlight, onTogglePollStatus) }
            }
        }
        if (requests.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                requests.forEach { request -> RequestRow(request, actionInFlight, onUpdateRequestStatus) }
            }
        }
    }
}

@Composable
private fun PollRow(poll: Poll, actionInFlight: Boolean, onToggleStatus: (Poll) -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Text(text = poll.question, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = poll.options.joinToString(" · ") { "${it.label} ${it.votes}" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedButton(onClick = { onToggleStatus(poll) }, enabled = !actionInFlight, modifier = Modifier.padding(top = 10.dp)) {
            Text(if (poll.status == "open") "Close" else "Open")
        }
    }
}

@Composable
private fun RequestRow(request: ParticipationRequestItem, actionInFlight: Boolean, onUpdateStatus: (Int, String) -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Text(text = request.media_title, style = MaterialTheme.typography.bodyMedium)
        val dedication = request.dedication?.let { " · $it" } ?: ""
        Text(
            text = "${request.listener_name ?: "Listener"}$dedication",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (request.status == "pending") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                Button(onClick = { onUpdateStatus(request.id, "accepted") }, enabled = !actionInFlight) { Text("Queue") }
                OutlinedButton(onClick = { onUpdateStatus(request.id, "declined") }, enabled = !actionInFlight) { Text("Decline") }
            }
        }
    }
}

@Composable
private fun RadioHostPanel(creatorType: String?, radioHost: RadioHostStatus, actionInFlight: Boolean, onToggle: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Radio host", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Current type: ${creatorType ?: "unknown"} · switches ${radioHost.switches}/3" + if (radioHost.locked) " · locked" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedButton(
            onClick = onToggle,
            enabled = !radioHost.locked && !actionInFlight,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Text(if (radioHost.radioHost) "Turn off radio host" else "Turn on radio host")
        }
    }
}

@Composable
private fun ExternalSearchPanel(
    platform: String,
    onPlatformChange: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    results: List<ExternalSearchItem>,
    actionInFlight: Boolean,
    onImport: (ExternalSearchItem) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Search external catalogs", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            DropdownField(label = "Platform", options = EXTERNAL_PLATFORMS, selected = platform, onSelect = onPlatformChange, modifier = Modifier.weight(1f))
            OutlinedTextField(value = query, onValueChange = onQueryChange, label = { Text("Search") }, modifier = Modifier.weight(2f))
        }
        Button(onClick = onSearch, enabled = query.isNotBlank() && !searching, modifier = Modifier.padding(top = 10.dp)) {
            Text(if (searching) "Searching…" else "Search")
        }
        if (results.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                results.forEach { item -> ExternalResultRow(item, actionInFlight, onImport) }
            }
        }
    }
}

@Composable
private fun ExternalResultRow(item: ExternalSearchItem, actionInFlight: Boolean, onImport: (ExternalSearchItem) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = item.artist ?: item.platform,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { onImport(item) }, enabled = !actionInFlight) { Text("Add") }
    }
}

private fun formatCents(cents: Long): String = "$%.2f".format(cents / 100.0)

private fun formatWhen(value: String?): String {
    if (value.isNullOrBlank()) return "never"
    val iso = if (value.contains("T")) value else "${value.replace(' ', 'T')}Z"
    return try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(WHEN_FORMATTER)
    } catch (e: DateTimeParseException) {
        value
    }
}
