package com.hxrsh.sawari.ui.licence

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hxrsh.sawari.ui.permissions.PermissionController
import com.hxrsh.sawari.ui.permissions.PermissionStatus
import com.hxrsh.sawari.ui.permissions.rememberPermissionController
import kotlinx.coroutines.launch
import java.io.File

/**
 * Full-screen scanner: permission gate -> live camera -> review (retake / use photo). Everything stays on this device.
 * [onDone] gets the path of the confirmed photo; [onCancel] means nothing was kept.
 */
@Composable
fun LicenceScanScreen(
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: LicenceScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camera = rememberPermissionController(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { viewModel.onPicked { context.contentResolver.openInputStream(uri) } }
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    BackHandler { viewModel.onEvent(ScanEvent.Cancel) }
    LaunchedEffect(state.stage) {
        when (state.stage) {
            ScanStage.Done -> state.photoPath?.let(onDone)
            ScanStage.Cancelled -> onCancel()
            else -> Unit
        }
    }
    // Reset only when the screen is really gone. Resetting right after Done would flip back to the live preview for a
    // moment and re-open the camera while the host is still closing the scanner.
    DisposableEffect(Unit) { onDispose { viewModel.resetIfFinished() } }

    Column(Modifier.fillMaxSize().testTag("licence_scanner")) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Scan driving licence", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            TextButton(onClick = { viewModel.onEvent(ScanEvent.Cancel) }, modifier = Modifier.heightIn(min = 48.dp).testTag("scan_cancel")) { Text("Cancel") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                // Result is final and the host is closing this screen: show nothing, and above all do not open the camera.
                state.stage == ScanStage.Done || state.stage == ScanStage.Cancelled -> Unit
                state.stage == ScanStage.Review && state.photoPath != null ->
                    ReviewPhoto(state.photoPath!!, onRetake = { viewModel.onEvent(ScanEvent.Retake) }, onConfirm = { viewModel.onEvent(ScanEvent.Confirm) })
                camera.status == PermissionStatus.Granted ->
                    CameraStage(state, viewModel, pickPhoto)
                else -> CameraPermissionStage(camera, pickPhoto)
            }
        }
    }
}

@Composable
private fun CameraPermissionStage(controller: PermissionController, onPickPhoto: () -> Unit) {
    var showRationale by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        when (controller.status) {
            PermissionStatus.NotAsked, PermissionStatus.Granted -> {
                Text("Take a photo of your licence", style = MaterialTheme.typography.titleMedium)
                Text("Optional. The photo is stored only on this phone and is not uploaded or checked.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { showRationale = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("camera_enable")) { Text("Use the camera") }
            }
            PermissionStatus.DeniedCanAskAgain -> {
                Text("Camera access is off", style = MaterialTheme.typography.titleMedium)
                Text("You can allow it, or choose a photo from your gallery instead.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { showRationale = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("camera_ask_again")) { Text("Ask again") }
            }
            PermissionStatus.DeniedPermanently -> {
                Text("Camera access is blocked", style = MaterialTheme.typography.titleMedium)
                Text("Allow the camera for Sawari in system settings, or choose a photo from your gallery instead.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = controller.openSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("camera_open_settings")) { Text("Open settings") }
            }
        }
        OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pick_photo")) { Text("Choose a photo instead") }
    }
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Why we ask") },
            text = { Text("Sawari needs the camera only to take a photo of your driving licence. The photo never leaves this phone.") },
            confirmButton = { TextButton(onClick = { showRationale = false; controller.request() }, modifier = Modifier.testTag("camera_rationale_continue")) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun CameraStage(state: ScanState, viewModel: LicenceScanViewModel, onPickPhoto: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // The shutter is only live once the camera is really bound; tapping earlier would just fail.
    var cameraReady by remember { mutableStateOf(false) }

    // Bind to the lifecycle owner: CameraX opens the camera on start and releases it on stop (home, rotation, back).
    DisposableEffect(lifecycleOwner) {
        var active = true
        val future = ProcessCameraProvider.getInstance(context)
        val bind = Runnable {
            // CameraX completes this future asynchronously. If the preview was already disposed by then, binding now
            // would hold the camera for the whole Activity lifetime with nothing on screen.
            if (!active) return@Runnable
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                cameraReady = true
            } catch (e: Exception) {
                viewModel.onEvent(ScanEvent.CameraUnavailable("No camera available on this device."))
            }
        }
        future.addListener(bind, ContextCompat.getMainExecutor(context))
        onDispose {
            active = false
            cameraReady = false
            provider?.unbindAll()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Camera preview" }.testTag("camera_preview"),
        )
        Column(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("scan_error"))
            }
            if (state.stage == ScanStage.Capturing) {
                CircularProgressIndicator(Modifier.testTag("scan_busy"))
            } else {
                Button(
                    onClick = {
                        viewModel.onEvent(ScanEvent.Capture)
                        val file = viewModel.newPhotoFile()
                        imageCapture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) = viewModel.onEvent(ScanEvent.PhotoSaved(file.absolutePath))
                                override fun onError(e: ImageCaptureException) {
                                    file.delete()
                                    viewModel.onEvent(ScanEvent.CaptureFailed("Could not take the photo. Try again."))
                                }
                            },
                        )
                    },
                    enabled = cameraReady,
                    modifier = Modifier.heightIn(min = 56.dp).testTag("capture_button"),
                ) { Text(if (cameraReady) "Take photo" else "Starting camera...") }
            }
            TextButton(onClick = onPickPhoto, modifier = Modifier.heightIn(min = 48.dp).testTag("pick_photo")) { Text("Choose a photo instead") }
        }
    }
}

@Composable
private fun ReviewPhoto(path: String, onRetake: () -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(
            model = File(path),
            contentDescription = "Photo of your driving licence, for review",
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("review_image"),
        )
        Text("Is the text readable? The photo stays on this phone.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("retake_button")) { Text("Retake") }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("use_photo_button")) { Text("Use photo") }
        }
    }
}
