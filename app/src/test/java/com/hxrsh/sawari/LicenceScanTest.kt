package com.hxrsh.sawari

import androidx.lifecycle.SavedStateHandle
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.usecase.CreateBookingUseCase
import com.hxrsh.sawari.domain.usecase.GetBusySlotsUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCarUseCase
import com.hxrsh.sawari.ui.booking.BookingEffect
import com.hxrsh.sawari.ui.booking.BookingIntent
import com.hxrsh.sawari.ui.booking.BookingViewModel
import com.hxrsh.sawari.ui.licence.LicenceScanViewModel
import com.hxrsh.sawari.ui.licence.ScanEvent
import com.hxrsh.sawari.ui.licence.ScanStage
import com.hxrsh.sawari.ui.licence.ScanState
import com.hxrsh.sawari.ui.licence.on
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ScanStateMachineTest {
    private fun state(stage: ScanStage, path: String? = null, error: String? = null) = ScanState(stage, path, error)

    /** (from, event, expected) */
    private val table = listOf(
        Triple(state(ScanStage.Preview), ScanEvent.Capture, state(ScanStage.Capturing)),
        Triple(state(ScanStage.Capturing), ScanEvent.PhotoSaved("a.jpg"), state(ScanStage.Review, "a.jpg")),
        Triple(state(ScanStage.Capturing), ScanEvent.CaptureFailed("boom"), state(ScanStage.Preview, error = "boom")),
        Triple(state(ScanStage.Preview), ScanEvent.CameraUnavailable("none"), state(ScanStage.Preview, error = "none")),
        Triple(state(ScanStage.Preview), ScanEvent.PickedFromLibrary("p.jpg"), state(ScanStage.Review, "p.jpg")),
        Triple(state(ScanStage.Review, "a.jpg"), ScanEvent.PickedFromLibrary("p.jpg"), state(ScanStage.Review, "p.jpg")),
        Triple(state(ScanStage.Review, "a.jpg"), ScanEvent.Retake, state(ScanStage.Preview)),
        Triple(state(ScanStage.Review, "a.jpg"), ScanEvent.Confirm, state(ScanStage.Done, "a.jpg")),
        Triple(state(ScanStage.Preview), ScanEvent.Cancel, state(ScanStage.Cancelled)),
        Triple(state(ScanStage.Review, "a.jpg"), ScanEvent.Cancel, state(ScanStage.Cancelled)),
        // events that make no sense in the current stage are ignored
        Triple(state(ScanStage.Capturing), ScanEvent.Capture, state(ScanStage.Capturing)),          // double tap on the shutter
        Triple(state(ScanStage.Review, "a.jpg"), ScanEvent.Capture, state(ScanStage.Review, "a.jpg")),
        Triple(state(ScanStage.Preview), ScanEvent.Confirm, state(ScanStage.Preview)),              // nothing to confirm
        Triple(state(ScanStage.Preview), ScanEvent.Retake, state(ScanStage.Preview)),
        Triple(state(ScanStage.Preview), ScanEvent.PhotoSaved("late.jpg"), state(ScanStage.Preview)), // late camera callback after cancel/retake
        Triple(state(ScanStage.Done, "a.jpg"), ScanEvent.Cancel, state(ScanStage.Done, "a.jpg")),   // a confirmed photo cannot be cancelled away
        Triple(state(ScanStage.Done, "a.jpg"), ScanEvent.Capture, state(ScanStage.Done, "a.jpg")),
        Triple(state(ScanStage.Review, null), ScanEvent.Confirm, state(ScanStage.Review, null)),    // defensive: Review without a path cannot confirm
    )

    @Test fun `every transition in the table`() {
        table.forEachIndexed { i, (from, event, expected) -> assertEquals("row $i: $from + $event", expected, from.on(event)) }
    }

    @Test fun `a successful capture error is cleared by the next capture`() {
        val s = state(ScanStage.Preview, error = "old").on(ScanEvent.Capture)
        assertNull(s.error)
    }

    @Test fun `full happy path then retake then confirm`() {
        var s = ScanState()
        s = s.on(ScanEvent.Capture).on(ScanEvent.PhotoSaved("1.jpg")).on(ScanEvent.Retake)
        assertEquals(ScanStage.Preview, s.stage)
        assertNull(s.photoPath)
        s = s.on(ScanEvent.Capture).on(ScanEvent.PhotoSaved("2.jpg")).on(ScanEvent.Confirm)
        assertEquals(state(ScanStage.Done, "2.jpg"), s)
    }
}

class LicenceScanViewModelTest {
    private val store = FakeLicencePhotoStore()
    private fun vm() = LicenceScanViewModel(store)

    @Test fun `retake deletes the rejected photo`() {
        val vm = vm()
        val f = vm.newPhotoFile().path
        vm.onEvent(ScanEvent.Capture); vm.onEvent(ScanEvent.PhotoSaved(f))
        assertTrue(f in store.existing)
        vm.onEvent(ScanEvent.Retake)
        assertFalse(f in store.existing)
    }

    @Test fun `cancel in review deletes the photo`() {
        val vm = vm()
        val f = vm.newPhotoFile().path
        vm.onEvent(ScanEvent.Capture); vm.onEvent(ScanEvent.PhotoSaved(f)); vm.onEvent(ScanEvent.Cancel)
        assertFalse(f in store.existing)
        assertEquals(ScanStage.Cancelled, vm.state.value.stage)
    }

    @Test fun `confirm keeps the photo for the booking draft`() {
        val vm = vm()
        val f = vm.newPhotoFile().path
        vm.onEvent(ScanEvent.Capture); vm.onEvent(ScanEvent.PhotoSaved(f)); vm.onEvent(ScanEvent.Confirm)
        assertTrue(f in store.existing)
        assertEquals(ScanStage.Done, vm.state.value.stage)
    }

    @Test fun `picking another photo replaces and deletes the previous one`() = runTest {
        val vm = vm()
        val first = vm.newPhotoFile().path
        vm.onEvent(ScanEvent.Capture); vm.onEvent(ScanEvent.PhotoSaved(first))
        vm.onPicked { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        assertFalse(first in store.existing)
        assertEquals(ScanStage.Review, vm.state.value.stage)
        assertTrue(vm.state.value.photoPath in store.existing)
    }

    @Test fun `unreadable picked photo shows an error and keeps the flow alive`() = runTest {
        val vm = vm()
        store.importFails = true
        vm.onPicked { null }
        assertEquals(ScanStage.Preview, vm.state.value.stage)
        assertEquals("Could not read that photo. Try another one.", vm.state.value.error)
    }

    @Test fun `picker works straight from the permission-denied screen`() = runTest {
        val vm = vm()   // no camera at all: stage is still Preview, the picker feeds the same state machine
        vm.onPicked { ByteArrayInputStream(byteArrayOf(9)) }
        assertEquals(ScanStage.Review, vm.state.value.stage)
    }

    @Test fun `reset starts a fresh scan after Done`() {
        val vm = vm()
        val f = vm.newPhotoFile().path
        vm.onEvent(ScanEvent.Capture); vm.onEvent(ScanEvent.PhotoSaved(f)); vm.onEvent(ScanEvent.Confirm)
        vm.reset()
        assertEquals(ScanState(), vm.state.value)
        assertTrue("the confirmed photo belongs to the booking draft now", f in store.existing)
    }
}

class LicenceScanResetTest {
    private val store = FakeLicencePhotoStore()

    @Test fun `leaving the screen after Done or Cancel resets, but a photo under review survives rotation`() {
        val review = LicenceScanViewModel(store)
        review.onEvent(ScanEvent.Capture); review.onEvent(ScanEvent.PhotoSaved("/fake/a.jpg"))
        review.resetIfFinished()          // what a rotation does to the composable: the state must stay
        assertEquals(ScanStage.Review, review.state.value.stage)

        val done = LicenceScanViewModel(store)
        done.onEvent(ScanEvent.Capture); done.onEvent(ScanEvent.PhotoSaved("/fake/b.jpg")); done.onEvent(ScanEvent.Confirm)
        done.resetIfFinished()
        assertEquals(ScanState(), done.state.value)

        val cancelled = LicenceScanViewModel(store)
        cancelled.onEvent(ScanEvent.Cancel)
        cancelled.resetIfFinished()
        assertEquals(ScanState(), cancelled.state.value)
    }
}

class BookingLicenceDraftTest {
    @get:Rule val main = MainDispatcherRule()
    private val carRepo = FakeCarRepository()
    private val bookingRepo = FakeBookingRepository()
    private val store = FakeLicencePhotoStore()
    private val clock = Clock.fixed(Instant.parse("2030-06-01T09:20:00Z"), ZoneOffset.UTC)

    private fun vm() = BookingViewModel(
        SavedStateHandle(mapOf("carId" to 1)), ObserveCarUseCase(carRepo), GetBusySlotsUseCase(carRepo),
        CreateBookingUseCase(bookingRepo, FakeReminderScheduler()), store, FakeAnalytics(), clock,
    )

    @Test fun `attached photo is part of the draft state`() {
        val vm = vm()
        vm.onIntent(BookingIntent.AttachLicence("/fake/licence-1.jpg"))
        assertEquals("/fake/licence-1.jpg", vm.state.value.licencePhotoPath)
    }

    @Test fun `removing the photo deletes the file`() {
        val vm = vm()
        val f = store.newFile().path
        vm.onIntent(BookingIntent.AttachLicence(f)); vm.onIntent(BookingIntent.RemoveLicence)
        assertNull(vm.state.value.licencePhotoPath)
        assertFalse(f in store.existing)
    }

    @Test fun `replacing the photo deletes the old file`() {
        val vm = vm()
        val a = store.newFile().path; val b = store.newFile().path
        vm.onIntent(BookingIntent.AttachLicence(a)); vm.onIntent(BookingIntent.AttachLicence(b))
        assertFalse(a in store.existing); assertTrue(b in store.existing)
    }

    @Test fun `the photo is never sent with the booking and is discarded after a successful booking`() = runTest {
        val vm = vm()
        val f = store.newFile().path
        vm.onIntent(BookingIntent.AttachLicence(f))
        vm.onIntent(BookingIntent.Submit)
        assertTrue(vm.effects.first() is BookingEffect.Booked)
        assertEquals("the repository call carries only car and times, no photo", 1, bookingRepo.created.size)
        assertNull(vm.state.value.licencePhotoPath)
        assertFalse(f in store.existing)
    }

    @Test fun `a failed booking keeps the photo so the user can retry`() = runTest {
        bookingRepo.createResult = AppResult.Failure(AppError.Conflict("taken"))
        val vm = vm()
        val f = store.newFile().path
        vm.onIntent(BookingIntent.AttachLicence(f))
        vm.onIntent(BookingIntent.Submit)
        vm.effects.first()
        assertEquals(f, vm.state.value.licencePhotoPath)
        assertTrue(f in store.existing)
    }
}
