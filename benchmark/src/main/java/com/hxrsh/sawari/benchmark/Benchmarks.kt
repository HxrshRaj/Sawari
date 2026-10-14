package com.hxrsh.sawari.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

const val PKG = "com.hxrsh.sawari"
private const val EMAIL = "bench@example.com" // pre-registered on the local backend by scripts/bench.sh
private const val PASSWORD = "secret123"

/** Signs in through the real UI if the app starts on the login screen (session persists across iterations). */
fun MacrobenchmarkScope.signInIfNeeded() {
    pressHome()
    startActivityAndWait()
    if (device.wait(Until.hasObject(By.res("email_field")), 4_000)) {
        device.findObject(By.res("email_field")).text = EMAIL
        device.findObject(By.res("password_field")).text = PASSWORD
        device.findObject(By.res("submit_button")).click()
    }
    device.wait(Until.hasObject(By.res("car_list")), 20_000)
}

fun MacrobenchmarkScope.startCarList(naive: Boolean = false) {
    startActivityAndWait { intent -> intent.putExtra("naive_list", naive) }
    device.wait(Until.hasObject(By.res("car_list")), 20_000)
}

/** Cold start with no profile vs the shipped baseline profile. */
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun start(mode: CompilationMode) = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { signInIfNeeded(); killProcess() },
    ) { startCarList() }

    @Test fun coldStartNoProfile() = start(CompilationMode.None())
    @Test fun coldStartBaselineProfile() = start(CompilationMode.Partial(BaselineProfileMode.Require))
}

@OptIn(ExperimentalMetricApi::class)
class ListBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun scroll(naive: Boolean, mode: CompilationMode) = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Last),
            TraceSectionMetric("CarCard", TraceSectionMetric.Mode.Sum),
        ),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = { signInIfNeeded(); killProcess(); startCarList(naive) },
    ) {
        val list = device.findObject(By.res("car_list"))
        list.setGestureMargin(device.displayWidth / 5)
        repeat(3) { list.fling(Direction.DOWN); device.waitForIdle() }
        repeat(3) { list.fling(Direction.UP); device.waitForIdle() }
    }

    @Test fun scrollNaive() = scroll(naive = true, mode = CompilationMode.Partial(BaselineProfileMode.Require))
    @Test fun scrollOptimised() = scroll(naive = false, mode = CompilationMode.Partial(BaselineProfileMode.Require))

    /** Toggle city filters repeatedly and count how many card bodies run. */
    private fun filterToggle(naive: Boolean) = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(TraceSectionMetric("CarCard", TraceSectionMetric.Mode.Sum)),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 8,
        setupBlock = { signInIfNeeded(); killProcess(); startCarList(naive) },
    ) {
        for (city in listOf("Bengaluru", "Mumbai", "Delhi", "Delhi")) {
            device.findObject(By.res("city_chip_$city")).click()
            device.waitForIdle()
        }
    }

    @Test fun filterToggleNaive() = filterToggle(naive = true)
    @Test fun filterToggleOptimised() = filterToggle(naive = false)
}

class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = PKG, includeInStartupProfile = true) {
        signInIfNeeded()
        pressHome()
        startCarList()
        val list = device.findObject(By.res("car_list"))
        list.setGestureMargin(device.displayWidth / 5)
        repeat(2) { list.fling(Direction.DOWN); device.waitForIdle() }
        list.fling(Direction.UP); device.waitForIdle()
        device.findObject(By.res("search_field"))?.click()
        device.pressBack()
        device.findObject(By.res("city_chip_Mumbai"))?.click(); device.waitForIdle()
        device.findObject(By.res("city_chip_Mumbai"))?.click(); device.waitForIdle()
        device.findObject(By.res("car_card_1"))?.click(); device.wait(Until.hasObject(By.res("book_button")), 5_000)
        device.findObject(By.res("book_button"))?.click(); device.wait(Until.hasObject(By.res("confirm_button")), 5_000)
        device.findObject(By.res("pick_date"))?.click(); device.waitForIdle(); device.pressBack()
        device.pressBack(); device.pressBack()
        device.findObject(By.res("tab_bookings"))?.click(); device.wait(Until.hasObject(By.res("bookings_list")), 5_000)
        device.findObject(By.res("tab_cars"))?.click(); device.waitForIdle()
    }
}
