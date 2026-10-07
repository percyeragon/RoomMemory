package com.wilhelmbeiche.roommemory.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wilhelmbeiche.roommemory.R

/** Take one photo, let ML Kit find the things on it, and save them all at one location. */
@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    onEnterManually: () -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    var room by rememberSaveable { mutableStateOf("") }
    var furniture by rememberSaveable { mutableStateOf("") }
    var compartment by rememberSaveable { mutableStateOf("") }

    val rooms by viewModel.rooms.collectAsState()
    val allFurniture by viewModel.furniture.collectAsState()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = viewModel::onCameraResult
    )
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onPhotoPicked(uri)
    }

    fun takePhoto() {
        try {
            cameraLauncher.launch(viewModel.createCameraTarget())
        } catch (e: ActivityNotFoundException) {
            viewModel.onCameraUnavailable()
        }
    }

    val selectedCount = viewModel.candidates.count { it.selected && it.name.isNotBlank() }
    val canSave = !viewModel.isAnalyzing && room.isNotBlank() && selectedCount > 0

    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.scan_photo), onBack = onNavigateBack) },
        bottomBar = {
            if (viewModel.candidates.isNotEmpty()) {
                BottomActionBar(
                    text = if (selectedCount == 0) {
                        stringResource(R.string.nothing_selected)
                    } else {
                        pluralStringResource(R.plurals.save_items, selectedCount, selectedCount)
                    },
                    enabled = canSave,
                    loading = viewModel.isSaving,
                    hint = if (room.isBlank()) stringResource(R.string.room_required_hint) else null,
                    onClick = {
                        viewModel.save(room, furniture, compartment) { saved ->
                            val text = resources.getQuantityString(R.plurals.items_saved, saved, saved)
                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PhotoBox(
                model = viewModel.photo,
                onCamera = ::takePhoto,
                onGallery = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                emptyTitle = stringResource(R.string.scan_empty_title),
                emptyHint = stringResource(R.string.scan_empty_hint),
                busyText = if (viewModel.isAnalyzing) stringResource(R.string.analyzing) else null,
                enabled = !viewModel.isAnalyzing
            )

            if (viewModel.photo == null && !viewModel.isAnalyzing) {
                TextButton(
                    onClick = onEnterManually,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.enter_manually))
                }
            }

            viewModel.message?.let { InfoBanner(stringResource(it)) }

            if (viewModel.candidates.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.detected_title)) {
                    viewModel.candidates.forEach { candidate ->
                        CandidateRow(candidate = candidate, onRemove = { viewModel.removeCandidate(candidate) })
                    }
                    AddCandidateButton(onClick = viewModel::addManualCandidate)
                }
            } else if (viewModel.photo != null && !viewModel.isAnalyzing) {
                AddCandidateButton(onClick = viewModel::addManualCandidate)
            }

            SectionCard(title = stringResource(R.string.where_things)) {
                FormField(value = room, onValueChange = { room = it }, label = stringResource(R.string.label_room), placeholder = stringResource(R.string.hint_room_scan))
                Suggestions(input = room, options = rooms, onSelect = { room = it })
                FormField(
                    value = furniture,
                    onValueChange = { furniture = it },
                    label = stringResource(R.string.label_furniture),
                    placeholder = stringResource(R.string.hint_furniture_scan)
                )
                Suggestions(input = furniture, options = allFurniture, onSelect = { furniture = it })
                FormField(
                    value = compartment,
                    onValueChange = { compartment = it },
                    label = stringResource(R.string.label_compartment),
                    placeholder = stringResource(R.string.hint_compartment_scan)
                )
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AddCandidateButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.add_item_manually))
    }
}

@Composable
private fun CandidateRow(candidate: ScanCandidate, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.alpha(if (candidate.selected) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = candidate.selected, onCheckedChange = { candidate.selected = it })
        ItemThumbnail(imageUri = candidate.image, title = candidate.name, size = 56.dp)
        OutlinedTextField(
            value = candidate.name,
            onValueChange = { candidate.name = it },
            placeholder = { Text(stringResource(R.string.name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove))
        }
    }
}
