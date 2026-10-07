package com.wilhelmbeiche.roommemory.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wilhelmbeiche.roommemory.R
import com.wilhelmbeiche.roommemory.data.ItemEntity
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: ItemViewModel,
    onNavigateToScan: () -> Unit,
    onNavigateToEditItem: (Long) -> Unit
) {
    val resources = LocalResources.current
    val items by viewModel.items.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val rooms by viewModel.rooms.collectAsState()
    val recentlyDeleted by viewModel.recentlyDeleted.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    var selectedRoom by rememberSaveable { mutableStateOf<String?>(null) }

    // Drop the filter once its room no longer exists (e.g. its last item was deleted).
    LaunchedEffect(rooms) {
        if (selectedRoom != null && selectedRoom !in rooms) selectedRoom = null
    }

    LaunchedEffect(recentlyDeleted) {
        val item = recentlyDeleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.item_deleted, item.title),
            actionLabel = resources.getString(R.string.undo),
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.confirmDelete()
    }

    // Items matching the search, grouped by room in the order of `rooms` (most used first).
    val matching = items
    val countsByRoom = matching.orEmpty().groupingBy { it.room }.eachCount()
    // Rooms not yet in `rooms` (it updates separately right after a save) are appended.
    val roomOrder = (rooms + countsByRoom.keys).distinct()
    val groups = roomOrder
        .filter { (countsByRoom[it] ?: 0) > 0 && (selectedRoom == null || it == selectedRoom) }
        .map { room -> room to matching.orEmpty().filter { it.room == room } }

    // Nothing saved at all: the empty state has its own scan button, so the FAB would only repeat it.
    val isLibraryEmpty = matching != null && matching.isEmpty() && searchQuery.isBlank()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // One entry point; manual entry without a photo is offered on the scan screen.
        floatingActionButton = {
            if (!isLibraryEmpty) ExtendedFloatingActionButton(
                onClick = onNavigateToScan,
                expanded = fabExpanded,
                icon = { Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null) },
                text = { Text(stringResource(R.string.scan_photo)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                itemCount = if (searchQuery.isBlank()) matching?.size else null
            )

            // Only offer a filter when there is something to choose between.
            if (countsByRoom.size > 1 || selectedRoom != null) {
                RoomFilter(
                    rooms = roomOrder.filter { (countsByRoom[it] ?: 0) > 0 || it == selectedRoom },
                    counts = countsByRoom,
                    selected = selectedRoom,
                    onSelect = { selectedRoom = it }
                )
            }

            when {
                // Still loading: show nothing instead of flashing the empty state.
                matching == null -> Unit
                isLibraryEmpty -> EmptyState(
                    title = stringResource(R.string.empty_title),
                    text = stringResource(R.string.empty_text),
                    action = stringResource(R.string.empty_action),
                    onAction = onNavigateToScan,
                    modifier = Modifier.weight(1f)
                )
                groups.isEmpty() -> EmptyState(
                    title = stringResource(R.string.no_results_title),
                    text = if (searchQuery.isNotBlank()) {
                        stringResource(R.string.no_results_query, searchQuery.trim())
                    } else {
                        stringResource(R.string.no_results_room)
                    },
                    modifier = Modifier.weight(1f)
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    // Keep the last card clear of the floating buttons.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)
                ) {
                    groups.forEach { (room, roomItems) ->
                        stickyHeader(key = "header:$room") {
                            RoomHeader(room = room, count = roomItems.size)
                        }
                        items(roomItems, key = { it.id }) { item ->
                            SwipeToDeleteItem(
                                item = item,
                                query = searchQuery.trim(),
                                onClick = { onNavigateToEditItem(item.id) },
                                onDelete = { viewModel.deleteItem(item) },
                                modifier = Modifier
                                    .padding(bottom = 10.dp)
                                    .animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, itemCount: Int?) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = {
            Text(
                if (itemCount != null && itemCount > 0) {
                    pluralStringResource(R.plurals.search_hint_count, itemCount, itemCount)
                } else {
                    stringResource(R.string.search_hint)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun RoomFilter(
    rooms: List<String>,
    counts: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.all_rooms)) },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        items(rooms) { room ->
            val isSelected = room == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else room) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(room)
                        Spacer(Modifier.width(6.dp))
                        Text(
                        (counts[room] ?: 0).toString(),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                }
            )
        }
    }
}

/** Section title for one room; stays at the top while its items scroll by. */
@Composable
private fun RoomHeader(room: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            room,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            pluralStringResource(R.plurals.item_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAction) {
                Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(action)
            }
        }
    }
}

/** Item card that is deleted by swiping it to the left. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteItem(
    item: ItemEntity,
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        ItemCard(item = item, query = query, onClick = onClick)
    }
}

@Composable
private fun ItemCard(item: ItemEntity, query: String, onClick: () -> Unit) {
    val spot = listOf(item.furniture, item.compartment).filter { it.isNotBlank() }.joinToString("  ›  ")
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        background = MaterialTheme.colorScheme.tertiaryContainer
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (container, content) = placeholderColors(item.title)
            ItemThumbnail(
                imageUri = item.imageUri,
                title = item.title,
                size = 64.dp,
                containerColor = container,
                contentColor = content
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = highlightMatches(item.title, query, highlight),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = relativeTime(item.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                if (spot.isNotEmpty()) {
                    Text(
                        text = highlightMatches(spot, query, highlight),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = stringResource(R.string.no_spot),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (item.description.isNotBlank()) {
                    Text(
                        text = highlightMatches(item.description, query, highlight),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** A stable, varied placeholder color per item, so the list without photos is not one flat color. */
@Composable
private fun placeholderColors(seed: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val options = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer
    )
    return options[seed.lowercase().hashCode().absoluteValue % options.size]
}

/** "Just now", "5 min. ago", "Yesterday", … in the device language. */
@Composable
private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    // DateUtils would say "0 min. ago" for the first minute.
    if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.just_now)
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

/** Marks every case-insensitive occurrence of [query] in [text]. */
private fun highlightMatches(text: String, query: String, style: SpanStyle): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var start = text.indexOf(query, ignoreCase = true)
        while (start >= 0) {
            addStyle(style, start, start + query.length)
            start = text.indexOf(query, start + query.length, ignoreCase = true)
        }
    }
}
