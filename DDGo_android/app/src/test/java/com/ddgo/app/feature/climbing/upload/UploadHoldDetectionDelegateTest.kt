package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadHoldDetectionDelegateTest {

    @After
    fun tearDown() {
        unmockkStatic(BitmapFactory::class)
        unmockkStatic(Uri::class)
    }

    @Test
    fun `같은 입력이면 홀드 탐지를 다시 실행하지 않고 기존 편집 결과를 유지한다`() = runTest {
        val personDetector = mockk<PersonDetector>(relaxed = true)
        val holdDetector = mockk<HoldDetector>(relaxed = true)
        val holdColorClassifier = mockk<HoldColorClassifier>(relaxed = true)
        val delegate = createDelegate(
            personDetector = personDetector,
            holdDetector = holdDetector,
            holdColorClassifier = holdColorClassifier
        )
        val cachedBitmap = mockBitmap()
        val filteredHold = hold(centerX = 0.30f, centerY = 0.40f)
        val manualHold = hold(centerX = 0.72f, centerY = 0.68f, holdNo = 99)
        val cachedDetectedHolds = listOf(filteredHold, manualHold)

        delegate.bestFrameBitmap = cachedBitmap
        delegate.allRawHolds = listOf(filteredHold)
        delegate.detectedHolds = cachedDetectedHolds
        setLastSuccessfulDetectionInput(
            delegate = delegate,
            sourceVideoUri = "file:///cached.mp4",
            debugBestFrameImageUri = null,
            normalizedDetectionTargetColor = "red"
        )

        val result = delegate.runHoldDetection(
            sourceVideoUri = "file:///cached.mp4",
            detectionTargetColor = " Red "
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString() ?: "success", result.isSuccess)
        assertEquals(cachedDetectedHolds, delegate.detectedHolds)
        coVerify(exactly = 0) { personDetector.findBestFrameTime(any()) }
        coVerify(exactly = 0) { holdDetector.detectFromFrame(any()) }
        verify(exactly = 0) { holdColorClassifier.classifyForDetection(any(), any(), any(), any()) }
        verify(exactly = 0) { holdColorClassifier.classifySingle(any(), any()) }
        verify(exactly = 0) { holdColorClassifier.classifyAll(any(), any()) }
        verify(exactly = 0) { holdColorClassifier.classifyAndFilter(any(), any(), any(), any()) }
    }

    @Test
    fun `같은 디버그 이미지와 같은 색상이면 홀드 탐지를 다시 실행하지 않는다`() = runTest {
        val personDetector = mockk<PersonDetector>(relaxed = true)
        val holdDetector = mockk<HoldDetector>(relaxed = true)
        val holdColorClassifier = mockk<HoldColorClassifier>(relaxed = true)
        val delegate = createDelegate(
            personDetector = personDetector,
            holdDetector = holdDetector,
            holdColorClassifier = holdColorClassifier
        )

        delegate.bestFrameBitmap = mockBitmap()
        delegate.allRawHolds = listOf(hold(centerX = 0.30f, centerY = 0.40f))
        delegate.detectedHolds = listOf(hold(centerX = 0.30f, centerY = 0.40f))
        delegate.debugBestFrameImageUri = "file:///cached_frame.png"
        setLastSuccessfulDetectionInput(
            delegate = delegate,
            sourceVideoUri = null,
            debugBestFrameImageUri = "file:///cached_frame.png",
            normalizedDetectionTargetColor = "red"
        )

        val result = delegate.runHoldDetection(
            sourceVideoUri = null,
            detectionTargetColor = " red "
        )

        assertTrue(result.exceptionOrNull()?.stackTraceToString() ?: "success", result.isSuccess)
        coVerify(exactly = 0) { personDetector.findBestFrameTime(any()) }
        coVerify(exactly = 0) { holdDetector.detectFromFrame(any()) }
        verify(exactly = 0) { holdColorClassifier.classifyForDetection(any(), any(), any(), any()) }
    }

    @Test
    fun `홀드 탐지는 색상 분류 단일 패스를 사용한다`() = runTest {
        val bitmap = mockBitmap()
        val parsedUri = mockk<Uri> {
            every { scheme } returns "file"
            every { path } returns "/debug_frame.png"
        }
        val rawHold = hold(centerX = 0.30f, centerY = 0.40f)
        val classifiedHold = rawHold.copy(colorLabel = "red", colorScore = 0.9f)
        val holdDetector = mockk<HoldDetector>()
        val holdColorClassifier = mockk<HoldColorClassifier>(relaxed = true)
        val delegate = createDelegate(
            personDetector = mockk(relaxed = true),
            holdDetector = holdDetector,
            holdColorClassifier = holdColorClassifier
        )

        mockkStatic(BitmapFactory::class)
        mockkStatic(Uri::class)
        every { Uri.parse("file:///debug_frame.png") } returns parsedUri
        every { BitmapFactory.decodeFile("/debug_frame.png") } returns bitmap
        coEvery { holdDetector.detectFromFrame(bitmap) } returns listOf(rawHold)
        every {
            holdColorClassifier.classifyAllRich(
                bitmap = bitmap,
                holds = listOf(rawHold),
                relaxedRejection = true
            )
        } returns HoldColorClassifier.ClassifiedHoldPrecomputeResult(
            classifiedHolds = listOf(
                HoldColorClassifier.ClassifiedHoldRich(
                    hold = classifiedHold,
                    colorLabel = "red",
                    colorScore = 0.9f,
                    colorStatus = "classified",
                    primaryColor = "red",
                    colorDistribution = mapOf("red" to 0.9f),
                    rawColorScore = 0.9f,
                    detectionReliability = 0.9f,
                    validPixelRatio = 0.9f,
                    warnings = emptySet()
                )
            ),
            allHolds = listOf(classifiedHold),
        )
        every {
            holdColorClassifier.filterClassifiedHolds(
                classifiedHolds = any(),
                targetColorName = "red",
                scoreThreshold = 0.25f
            )
        } returns listOf(classifiedHold)

        delegate.useDebugBestFrameImage("file:///debug_frame.png")
        val result = delegate.runHoldDetection(sourceVideoUri = null, detectionTargetColor = "red")

        assertTrue(result.exceptionOrNull()?.stackTraceToString() ?: "success", result.isSuccess)
        assertEquals(listOf(classifiedHold), delegate.allRawHolds)
        assertEquals(listOf(classifiedHold), delegate.detectedHolds)
        verify(exactly = 1) { holdColorClassifier.classifyAllRich(bitmap, listOf(rawHold), true) }
        verify(exactly = 1) { holdColorClassifier.filterClassifiedHolds(any(), "red", 0.25f) }
        verify(exactly = 0) { holdColorClassifier.classifyForDetection(any(), any(), any(), any()) }
        verify(exactly = 0) { holdColorClassifier.classifySingle(any(), any()) }
        verify(exactly = 0) { holdColorClassifier.classifyAll(any(), any()) }
        verify(exactly = 0) { holdColorClassifier.classifyAndFilter(any(), any(), any(), any()) }
    }

    @Test
    fun `타깃 색상이 바뀌면 홀드 탐지를 다시 시도한다`() = runTest {
        val personDetector = mockk<PersonDetector>()
        val holdDetector = mockk<HoldDetector>(relaxed = true)
        val holdColorClassifier = mockk<HoldColorClassifier>(relaxed = true)
        val delegate = createDelegate(
            personDetector = personDetector,
            holdDetector = holdDetector,
            holdColorClassifier = holdColorClassifier
        )

        delegate.bestFrameBitmap = mockBitmap()
        setLastSuccessfulDetectionInput(
            delegate = delegate,
            sourceVideoUri = "file:///cached.mp4",
            debugBestFrameImageUri = null,
            normalizedDetectionTargetColor = "red"
        )
        coEvery { personDetector.findBestFrameTime("file:///cached.mp4") } returns 0L

        val result = delegate.runHoldDetection(
            sourceVideoUri = "file:///cached.mp4",
            detectionTargetColor = "blue"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { personDetector.findBestFrameTime("file:///cached.mp4") }
        coVerify(exactly = 0) { holdDetector.detectFromFrame(any()) }
    }

    @Test
    fun `리셋 이후에는 같은 입력이어도 홀드 탐지를 다시 시도한다`() = runTest {
        val personDetector = mockk<PersonDetector>()
        val holdDetector = mockk<HoldDetector>(relaxed = true)
        val holdColorClassifier = mockk<HoldColorClassifier>(relaxed = true)
        val delegate = createDelegate(
            personDetector = personDetector,
            holdDetector = holdDetector,
            holdColorClassifier = holdColorClassifier
        )

        delegate.bestFrameBitmap = mockBitmap()
        setLastSuccessfulDetectionInput(
            delegate = delegate,
            sourceVideoUri = "file:///cached.mp4",
            debugBestFrameImageUri = null,
            normalizedDetectionTargetColor = "red"
        )
        delegate.resetHoldDetectionState(clearDebugSource = false)
        delegate.bestFrameBitmap = mockBitmap()
        coEvery { personDetector.findBestFrameTime("file:///cached.mp4") } returns 0L

        val result = delegate.runHoldDetection(
            sourceVideoUri = "file:///cached.mp4",
            detectionTargetColor = "red"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { personDetector.findBestFrameTime("file:///cached.mp4") }
        coVerify(exactly = 0) { holdDetector.detectFromFrame(any()) }
    }

    @Test
    fun `precompute 이후 색상만 바뀌면 YOLO를 다시 돌리지 않고 필터만 재적용한다`() = runTest {
        val bitmap = mockBitmap()
        val parsedUri = mockk<Uri> {
            every { scheme } returns "file"
            every { path } returns "/debug_frame.png"
        }
        val rawHold = hold(centerX = 0.30f, centerY = 0.40f)
        val redHold = rawHold.copy(colorLabel = "red", colorScore = 0.9f)
        val blueHold = rawHold.copy(colorLabel = "blue", colorScore = 0.88f)
        val classifiedRich = listOf(
            HoldColorClassifier.ClassifiedHoldRich(
                hold = redHold,
                colorLabel = "red",
                colorScore = 0.9f,
                colorStatus = "classified",
                primaryColor = "red",
                colorDistribution = mapOf("red" to 0.9f, "blue" to 0.1f),
                rawColorScore = 0.9f,
                detectionReliability = 0.9f,
                validPixelRatio = 0.9f,
                warnings = emptySet()
            )
        )
        val holdDetector = mockk<HoldDetector>()
        val holdColorClassifier = mockk<HoldColorClassifier>(relaxed = true)
        val delegate = createDelegate(
            personDetector = mockk(relaxed = true),
            holdDetector = holdDetector,
            holdColorClassifier = holdColorClassifier
        )

        mockkStatic(BitmapFactory::class)
        mockkStatic(Uri::class)
        every { Uri.parse("file:///debug_frame.png") } returns parsedUri
        every { BitmapFactory.decodeFile("/debug_frame.png") } returns bitmap
        coEvery { holdDetector.detectFromFrame(bitmap) } returns listOf(rawHold)
        every {
            holdColorClassifier.classifyAllRich(
                bitmap = bitmap,
                holds = listOf(rawHold),
                relaxedRejection = true
            )
        } returns HoldColorClassifier.ClassifiedHoldPrecomputeResult(
            classifiedHolds = classifiedRich,
            allHolds = listOf(redHold)
        )
        every {
            holdColorClassifier.filterClassifiedHolds(
                classifiedHolds = classifiedRich,
                targetColorName = "red",
                scoreThreshold = 0.25f
            )
        } returns listOf(redHold)
        every {
            holdColorClassifier.filterClassifiedHolds(
                classifiedHolds = classifiedRich,
                targetColorName = "blue",
                scoreThreshold = 0.25f
            )
        } returns listOf(blueHold)

        delegate.useDebugBestFrameImage("file:///debug_frame.png")
        val precomputeResult = delegate.precomputeHoldDetection(
            selectionGeneration = 7L,
            sourceVideoUri = null
        )
        val redFilterResult = delegate.applyHoldColorFilter(
            selectionGeneration = 7L,
            detectionTargetColor = "red"
        )
        val blueFilterResult = delegate.applyHoldColorFilter(
            selectionGeneration = 7L,
            detectionTargetColor = "blue"
        )

        assertTrue(precomputeResult.exceptionOrNull()?.stackTraceToString() ?: "success", precomputeResult.isSuccess)
        assertTrue(redFilterResult.isSuccess)
        assertTrue(blueFilterResult.isSuccess)
        assertEquals(listOf(redHold), delegate.allRawHolds)
        assertEquals(listOf(blueHold), delegate.detectedHolds)
        coVerify(exactly = 1) { holdDetector.detectFromFrame(bitmap) }
        verify(exactly = 1) { holdColorClassifier.classifyAllRich(bitmap, listOf(rawHold), true) }
        verify(exactly = 1) { holdColorClassifier.filterClassifiedHolds(classifiedRich, "red", 0.25f) }
        verify(exactly = 1) { holdColorClassifier.filterClassifiedHolds(classifiedRich, "blue", 0.25f) }
    }

    private fun createDelegate(
        personDetector: PersonDetector,
        holdDetector: HoldDetector,
        holdColorClassifier: HoldColorClassifier
    ): UploadHoldDetectionDelegate {
        return UploadHoldDetectionDelegate(
            context = mockk<Context>(relaxed = true),
            personDetector = personDetector,
            holdDetector = holdDetector,
            holdColorClassifier = holdColorClassifier
        )
    }

    private fun setLastSuccessfulDetectionInput(
        delegate: UploadHoldDetectionDelegate,
        sourceVideoUri: String?,
        debugBestFrameImageUri: String?,
        normalizedDetectionTargetColor: String
    ) {
        val keyClass = Class.forName(
            "com.ddgo.app.feature.climbing.upload.UploadHoldDetectionDelegate\$DetectionInputKey"
        )
        val constructor = keyClass.declaredConstructors.single()
        constructor.isAccessible = true
        val instance = constructor.newInstance(
            sourceVideoUri,
            debugBestFrameImageUri,
            normalizedDetectionTargetColor
        )

        val field = UploadHoldDetectionDelegate::class.java.getDeclaredField("lastSuccessfulDetectionInput")
        field.isAccessible = true
        field.set(delegate, instance)
    }

    private fun mockBitmap(): Bitmap = mockk(relaxed = true) {
        every { width } returns 1080
        every { height } returns 1920
    }

    private fun hold(
        centerX: Float,
        centerY: Float,
        holdNo: Int = 0
    ): Hold = Hold(
        holdNo = holdNo,
        boundingBox = Hold.BoundingBox(
            left = centerX - 0.025f,
            top = centerY - 0.025f,
            right = centerX + 0.025f,
            bottom = centerY + 0.025f
        ),
        confidence = 0.95f,
        polygon = listOf(
            Hold.Point(centerX - 0.02f, centerY - 0.02f),
            Hold.Point(centerX + 0.02f, centerY - 0.02f),
            Hold.Point(centerX + 0.02f, centerY + 0.02f),
            Hold.Point(centerX - 0.02f, centerY + 0.02f)
        )
    )
}
