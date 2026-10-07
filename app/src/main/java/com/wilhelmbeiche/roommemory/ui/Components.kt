package com.wilhelmbeiche.roommemory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wilhelmbeiche.roommemory.R

/** Top bar for secondary screens: back arrow, title, optional actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        actions = actions
    )
}

/** A titled group of form fields on a soft card. */
@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
internal fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = MaterialTheme.shapes.small,
        trailingIcon = if (value.isNotEmpty() && singleLine) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
        )
    )
}

/** Chips with previously used values that match the current input. */
@Composable
internal fun Suggestions(input: String, options: List<String>, onSelect: (String) -> Unit) {
    val query = input.trim()
    val matches = options
        .filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
        .take(8)
    AnimatedVisibility(visible = matches.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matches) { option ->
                SuggestionChip(
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    shape = CircleShape
                )
            }
        }
    }
}

/**
 * Large photo area. Without a photo it invites the user to take or pick one;
 * with a photo it shows it with small buttons to replace or remove it.
 * [model] can be anything Coil loads, e.g. a URI string or a Bitmap.
 */
@Composable
internal fun PhotoBox(
    model: Any?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    emptyTitle: String? = null,
    emptyHint: String? = null,
    busyText: String? = null,
    enabled: Boolean = true
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (model == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_photo_camera),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(emptyTitle ?: stringResource(R.string.add_photo), style = MaterialTheme.typography.titleMedium)
                if (emptyHint != null) {
                    Text(
                        emptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCamera, enabled = enabled) {
                        Icon(
                            painterResource(R.drawable.ic_photo_camera),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.camera))
                    }
                    OutlinedButton(onClick = onGallery, enabled = enabled) {
                        Icon(
                            painterResource(R.drawable.ic_photo_library),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.gallery))
                    }
                }
            }
        } else {
            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Darken the bottom edge so the buttons stay visible on bright photos.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhotoAction(R.drawable.ic_photo_camera, stringResource(R.string.take_new_photo), onCamera, enabled)
                PhotoAction(R.drawable.ic_photo_library, stringResource(R.string.choose_from_gallery), onGallery, enabled)
                if (onRemove != null) {
                    FilledTonalIconButton(onClick = onRemove, enabled = enabled) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_photo))
                    }
                }
            }
        }

        if (busyText != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(busyText, color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun PhotoAction(icon: Int, description: String, onClick: () -> Unit, enabled: Boolean) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** Square photo thumbnail; the title's initial shows when there is no (readable) photo. */
@Composable
internal fun ItemThumbnail(
    imageUri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer
) {
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor
            )
            if (imageUri != null && imageUri != "") {
                AsyncImage(
                    model = imageUri,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/** "Keller › Regal › Oben" with a pin, skipping empty parts. */
@Composable
internal fun LocationLine(
    room: String,
    furniture: String,
    compartment: String,
    modifier: Modifier = Modifier
) {
    val text = listOf(room, furniture, compartment).filter { it.isNotBlank() }.joinToString("  ›  ")
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Place,
            contentDescription = stringResource(R.string.location),
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Primary action pinned to the bottom of the screen, above keyboard and navigation bar. */
@Composable
internal fun BottomActionBar(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
    hint: String? = null
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = onClick,
                enabled = enabled && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(text, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
