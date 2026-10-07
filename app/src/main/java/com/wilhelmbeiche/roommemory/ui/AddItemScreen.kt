package com.wilhelmbeiche.roommemory.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wilhelmbeiche.roommemory.R
import com.wilhelmbeiche.roommemory.data.ItemEntity

@Composable
fun AddItemScreen(
    viewModel: ItemViewModel,
    itemId: Long?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isEditing = itemId != null

    var title by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf("") }
    var furniture by rememberSaveable { mutableStateOf("") }
    var compartment by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var imageUri by rememberSaveable { mutableStateOf("") }
    var fieldsLoaded by rememberSaveable { mutableStateOf(!isEditing) }
    var isSaving by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<ItemEntity?>(null) }
    // Camera output file, kept across process death while the camera app is open.
    var pendingCameraStoredUri by rememberSaveable { mutableStateOf<String?>(null) }

    val rooms by viewModel.rooms.collectAsState()
    val allFurniture by viewModel.furniture.collectAsState()

    LaunchedEffect(itemId) {
        if (itemId == null) return@LaunchedEffect
        val item = viewModel.getItem(itemId)
        if (item == null) {
            onNavigateBack()
            return@LaunchedEffect
        }
        existing = item
        if (!fieldsLoaded) {
            title = item.title
            room = item.room
            furniture = item.furniture
            compartment = item.compartment
            description = item.description
            imageUri = item.imageUri
            fieldsLoaded = true
        }
    }

    // The helpers below read all state when called, not when composed: Compose remembers function
    // references across recompositions, so a value captured from the first composition (before the
    // item was loaded) would make the saved photo look new and get deleted on "back".

    /** Removes the current photo from storage if it was added in this session (i.e. is not the saved one). */
    fun discardUnsavedImage() {
        val original = existing?.imageUri ?: ""
        if (imageUri.isNotBlank() && imageUri != original) viewModel.discardImage(imageUri)
    }

    fun replaceImage(newUri: String) {
        discardUnsavedImage()
        imageUri = newUri
    }

    fun cancel() {
        discardUnsavedImage()
        onNavigateBack()
    }

    BackHandler(onBack = ::cancel)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importImage(uri) { stored ->
                if (stored != null) {
                    replaceImage(stored)
                } else {
                    Toast.makeText(context, R.string.photo_load_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val stored = pendingCameraStoredUri
        pendingCameraStoredUri = null
        if (stored == null) return@rememberLauncherForActivityResult
        if (success) replaceImage(stored) else viewModel.discardImage(stored)
    }

    val canSave = fieldsLoaded && !isSaving && title.isNotBlank() && room.isNotBlank()

    fun takePhoto() {
        val target = viewModel.createCameraTarget()
        pendingCameraStoredUri = target.storedUri
        try {
            cameraLauncher.launch(target.contentUri)
        } catch (e: ActivityNotFoundException) {
            pendingCameraStoredUri = null
            Toast.makeText(context, R.string.no_camera_app, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(if (isEditing) R.string.edit_item else R.string.new_item),
                onBack = ::cancel,
                actions = {
                    val item = existing
                    if (item != null) {
                        IconButton(onClick = {
                            discardUnsavedImage()
                            viewModel.deleteItem(item)
                            onNavigateBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomActionBar(
                text = stringResource(R.string.save),
                enabled = canSave,
                loading = isSaving,
                hint = if (fieldsLoaded && (title.isBlank() || room.isBlank())) stringResource(R.string.required_hint) else null,
                onClick = {
                    isSaving = true
                    viewModel.saveItem(
                        existing = existing,
                        title = title,
                        room = room,
                        furniture = furniture,
                        compartment = compartment,
                        description = description,
                        imageUri = imageUri,
                        onComplete = onNavigateBack
                    )
                }
            )
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
                model = imageUri.ifBlank { null },
                onCamera = ::takePhoto,
                onGallery = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemove = { replaceImage("") },
                emptyHint = stringResource(R.string.photo_hint)
            )

            SectionCard(title = stringResource(R.string.section_what)) {
                FormField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.label_name),
                    placeholder = stringResource(R.string.hint_name)
                )
                FormField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.label_notes),
                    placeholder = stringResource(R.string.hint_notes),
                    singleLine = false
                )
            }

            SectionCard(title = stringResource(R.string.section_where)) {
                FormField(
                    value = room,
                    onValueChange = { room = it },
                    label = stringResource(R.string.label_room),
                    placeholder = stringResource(R.string.hint_room)
                )
                Suggestions(input = room, options = rooms, onSelect = { room = it })
                FormField(
                    value = furniture,
                    onValueChange = { furniture = it },
                    label = stringResource(R.string.label_furniture),
                    placeholder = stringResource(R.string.hint_furniture)
                )
                Suggestions(input = furniture, options = allFurniture, onSelect = { furniture = it })
                FormField(
                    value = compartment,
                    onValueChange = { compartment = it },
                    label = stringResource(R.string.label_compartment),
                    placeholder = stringResource(R.string.hint_compartment)
                )
            }
        }
    }
}
