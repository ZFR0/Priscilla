package com.example.priscilla.ui.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.priscilla.RemindersViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemindersScreen(viewModel: RemindersViewModel) {
    val pendingReminders by viewModel.pendingReminders.collectAsState()
    val expiredReminders by viewModel.expiredReminders.collectAsState()

    val dateFormat = remember {
        SimpleDateFormat("h:mm a 'on' EEE, MMM d", Locale.getDefault())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text("Scheduled Reminders", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (pendingReminders.isEmpty() && expiredReminders.isEmpty()) {
            item {
                Text(
                    text = "You have no reminders from Priscilla.",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            // --- PENDING REMINDERS SECTION ---
            stickyHeader { // This makes the "Pending" text stick to the top as you scroll
                ListHeader("Pending")
            }
            if (pendingReminders.isEmpty()) {
                item { Text("No pending reminders.", modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
            } else {
                items(pendingReminders, key = { it.id }) { reminder ->
                    SwipeToDeleteContainer(
                        item = reminder,
                        onDelete = { viewModel.deleteReminder(reminder) }
                    ) {
                        ReminderCard(
                            reminder = reminder,
                            dateFormat = dateFormat
                            // The animateItemPlacement modifier moves to the container
                        )
                    }
                }
            }

            // --- EXPIRED REMINDERS SECTION ---
            item {
                // Add some space between the sections
                Spacer(modifier = Modifier.height(24.dp))
            }
            stickyHeader {
                ListHeader("Expired")
            }
            if (expiredReminders.isEmpty()) {
                item { Text("No expired reminders.", modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
            } else {
                items(expiredReminders, key = { it.id }) { reminder ->
                    SwipeToDeleteContainer(
                        item = reminder,
                        onDelete = { viewModel.deleteReminder(reminder) }
                    ) {
                        ReminderCard(
                            reminder = reminder,
                            dateFormat = dateFormat,
                            isExpired = true
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SwipeToDeleteContainer(
    item: T,
    onDelete: (T) -> Unit,
    content: @Composable () -> Unit
) {
    // Use the new `rememberSwipeToDismissBoxState`
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            // The enum values have changed names
            if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                onDelete(item)
                true
            } else {
                false
            }
        },
        // We can set a positional threshold for when the swipe triggers
        positionalThreshold = { it * .25f }
    )

    // The main composable is still SwipeToDismissBox
    SwipeToDismissBox(
        state = dismissState,
        // The background content logic is the same
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
                // Add this for swiping the other direction, if you enable it
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        // `directions` is no longer a parameter. It's inferred from the state.
        // We enable it by default.
        content = { content() }
    )
}

@Composable
private fun ListHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    HorizontalDivider()
}

@Composable
private fun ReminderCard(
    reminder: com.example.priscilla.data.ReminderEntity,
    dateFormat: SimpleDateFormat,
    modifier: Modifier = Modifier,
    isExpired: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = reminder.task,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                // Make the text slightly faded if it's expired
                color = if (isExpired) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scheduled for: ${dateFormat.format(reminder.triggerAtMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}