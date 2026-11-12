package com.hxrsh.sawari.ui.licence

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/** Where the (on-device only) licence photo files live. An interface so the state machine tests need no Android. */
interface LicencePhotoStore {
    fun newFile(): File
    fun delete(path: String)

    /** Copies a picked image into private storage. Returns its path, or null if it could not be read. */
    suspend fun importFrom(open: () -> InputStream?): String?
}

enum class ScanStage { Preview, Capturing, Review, Done, Cancelled }

@Immutable
data class ScanState(
    val stage: ScanStage = ScanStage.Preview,
    val photoPath: String? = null,
    val error: String? = null,
)

sealed interface ScanEvent {
    data object Capture : ScanEvent
    data class PhotoSaved(val path: String) : ScanEvent
    data class CaptureFailed(val message: String) : ScanEvent
    data class PickedFromLibrary(val path: String) : ScanEvent
    data class CameraUnavailable(val message: String) : ScanEvent
    data object Retake : ScanEvent
    data object Confirm : ScanEvent
    data object Cancel : ScanEvent
}

/**
 * The whole scan flow as a pure function: Preview -> Capturing -> Review -> (Retake -> Preview | Confirm -> Done).
 * Events that make no sense in the current stage (a double tap on the shutter, Confirm before a photo exists) are ignored.
 */
fun ScanState.on(event: ScanEvent): ScanState = when (event) {
    ScanEvent.Capture -> if (stage == ScanStage.Preview) copy(stage = ScanStage.Capturing, error = null) else this
    is ScanEvent.PhotoSaved -> if (stage == ScanStage.Capturing) copy(stage = ScanStage.Review, photoPath = event.path, error = null) else this
    is ScanEvent.CaptureFailed -> if (stage == ScanStage.Capturing) copy(stage = ScanStage.Preview, error = event.message) else this
    is ScanEvent.CameraUnavailable -> if (stage == ScanStage.Preview || stage == ScanStage.Capturing) copy(stage = ScanStage.Preview, error = event.message) else this
    // Picking from the system photo picker works from the camera screen and from the "permission denied" screen.
    is ScanEvent.PickedFromLibrary -> if (stage == ScanStage.Preview || stage == ScanStage.Review) copy(stage = ScanStage.Review, photoPath = event.path, error = null) else this
    ScanEvent.Retake -> if (stage == ScanStage.Review) copy(stage = ScanStage.Preview, photoPath = null, error = null) else this
    ScanEvent.Confirm -> if (stage == ScanStage.Review && photoPath != null) copy(stage = ScanStage.Done) else this
    ScanEvent.Cancel -> if (stage == ScanStage.Done) this else copy(stage = ScanStage.Cancelled, photoPath = null, error = null)
}

/** Holds the flow state across rotation and deletes photo files that are not going to be used. */
@HiltViewModel
class LicenceScanViewModel @Inject constructor(private val store: LicencePhotoStore) : ViewModel() {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun newPhotoFile(): File = store.newFile()

    fun onEvent(event: ScanEvent) {
        val before = _state.value
        val after = before.on(event)
        _state.value = after
        // A photo that is replaced, retaken or abandoned must not linger on the device.
        val dropped = before.photoPath?.takeIf { it != after.photoPath }
        if (dropped != null && !(event == ScanEvent.Confirm)) store.delete(dropped)
    }

    suspend fun onPicked(open: () -> InputStream?) {
        val path = store.importFrom(open)
        if (path == null) _state.update { it.copy(error = "Could not read that photo. Try another one.") }
        else onEvent(ScanEvent.PickedFromLibrary(path))
    }

    /** Back to a fresh Preview, so reopening the scanner starts from the beginning. */
    fun reset() {
        _state.value = ScanState()
    }

    /** Called when the scanner leaves the screen: a finished flow (Done / Cancelled) must not greet the next opening. */
    fun resetIfFinished() {
        if (_state.value.stage == ScanStage.Done || _state.value.stage == ScanStage.Cancelled) reset()
    }

    override fun onCleared() {
        // Scanner closed without confirming: drop an unconfirmed photo.
        val s = _state.value
        if (s.stage != ScanStage.Done) s.photoPath?.let(store::delete)
    }
}
