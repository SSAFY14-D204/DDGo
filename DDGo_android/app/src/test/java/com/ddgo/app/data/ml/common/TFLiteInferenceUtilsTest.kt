package com.ddgo.app.data.ml.common

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.floor

class TFLiteInferenceUtilsTest {

    @Test
    fun `scaleMaskToOriginal matches legacy path without letterbox`() {
        val inputWidth = 6
        val inputHeight = 4
        val info = letterboxInfo(
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            originalWidth = 6,
            originalHeight = 4,
            padLeft = 0,
            padTop = 0,
            scale = 1f
        )
        val mask = FloatArray(inputWidth * inputHeight) { index ->
            ((index % inputWidth) + (index / inputWidth)) / 8f
        }

        val legacy = legacyScaleMaskToOriginal(mask, inputWidth, inputHeight, info)
        val optimized = invokeScaleMaskToOriginal(mask, inputWidth, inputHeight, info)

        assertArrayEquals(legacy, optimized)
    }

    @Test
    fun `scaleMaskToOriginal matches legacy path for portrait letterbox`() {
        val inputWidth = 8
        val inputHeight = 8
        val info = letterboxInfo(
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            originalWidth = 4,
            originalHeight = 8,
            padLeft = 2,
            padTop = 0,
            scale = 1f
        )
        val mask = FloatArray(inputWidth * inputHeight) { index ->
            ((index * 17) % 31) / 31f
        }

        val legacy = legacyScaleMaskToOriginal(mask, inputWidth, inputHeight, info, threshold = 0.37f)
        val optimized = invokeScaleMaskToOriginal(mask, inputWidth, inputHeight, info, threshold = 0.37f)

        assertArrayEquals(legacy, optimized)
    }

    @Test
    fun `scaleMaskToOriginal matches legacy path for landscape letterbox`() {
        val inputWidth = 8
        val inputHeight = 8
        val info = letterboxInfo(
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            originalWidth = 8,
            originalHeight = 4,
            padLeft = 0,
            padTop = 2,
            scale = 1f
        )
        val mask = FloatArray(inputWidth * inputHeight) { index ->
            val x = index % inputWidth
            val y = index / inputWidth
            ((x * 0.11f) + (y * 0.07f)).coerceIn(0f, 1f)
        }

        val legacy = legacyScaleMaskToOriginal(mask, inputWidth, inputHeight, info)
        val optimized = invokeScaleMaskToOriginal(mask, inputWidth, inputHeight, info)

        assertArrayEquals(legacy, optimized)
    }

    @Test
    fun `optimized mask preserves polygon result`() {
        val inputWidth = 8
        val inputHeight = 8
        val info = letterboxInfo(
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            originalWidth = 4,
            originalHeight = 8,
            padLeft = 2,
            padTop = 0,
            scale = 1f
        )
        val mask = FloatArray(inputWidth * inputHeight) { 0f }.apply {
            set(2 * inputWidth + 3, 0.9f)
            set(2 * inputWidth + 4, 0.9f)
            set(3 * inputWidth + 3, 0.9f)
            set(3 * inputWidth + 4, 0.9f)
            set(4 * inputWidth + 3, 0.9f)
            set(4 * inputWidth + 4, 0.9f)
        }
        val fallbackBox = TFLiteInferenceUtils.RawDetection(
            x1 = 1f,
            y1 = 2f,
            x2 = 3f,
            y2 = 5f,
            confidence = 0.9f,
            classIndex = 0
        )

        val legacyMask = legacyScaleMaskToOriginal(mask, inputWidth, inputHeight, info)
        val optimizedMask = invokeScaleMaskToOriginal(mask, inputWidth, inputHeight, info)
        val legacyPolygon = invokeBuildNormalizedPolygon(
            mask = legacyMask,
            maskWidth = info.originalWidth,
            maskHeight = info.originalHeight,
            fallbackBox = fallbackBox
        )
        val optimizedPolygon = invokeBuildNormalizedPolygon(
            mask = optimizedMask,
            maskWidth = info.originalWidth,
            maskHeight = info.originalHeight,
            fallbackBox = fallbackBox
        )

        assertEquals(legacyPolygon, optimizedPolygon)
    }

    @Test
    fun `bbox local polygon matches baseline without letterbox`() {
        val inputWidth = 10
        val inputHeight = 10
        val info = letterboxInfo(
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            originalWidth = 10,
            originalHeight = 10,
            padLeft = 0,
            padTop = 0,
            scale = 1f
        )
        val mask = FloatArray(inputWidth * inputHeight) { 0f }.apply {
            for (y in 2..7) {
                for (x in 3..6) {
                    this[y * inputWidth + x] = 0.9f
                }
            }
        }
        val fallbackBox = TFLiteInferenceUtils.RawDetection(
            x1 = 3f,
            y1 = 2f,
            x2 = 7f,
            y2 = 8f,
            confidence = 0.8f,
            classIndex = 1
        )

        val baselinePolygon = invokeBuildNormalizedPolygon(
            mask = invokeScaleMaskToOriginal(mask, inputWidth, inputHeight, info),
            maskWidth = info.originalWidth,
            maskHeight = info.originalHeight,
            fallbackBox = fallbackBox
        )
        val localPolygon = invokeBuildNormalizedPolygonFromLocalMask(
            mask = mask,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            info = info,
            fallbackBox = fallbackBox
        )

        assertEquals(baselinePolygon, localPolygon)
    }

    @Test
    fun `bbox local polygon matches baseline for portrait letterbox`() {
        val inputWidth = 12
        val inputHeight = 12
        val info = letterboxInfo(
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            originalWidth = 6,
            originalHeight = 12,
            padLeft = 3,
            padTop = 0,
            scale = 1f
        )
        val mask = FloatArray(inputWidth * inputHeight) { 0f }.apply {
            for (y in 2..9) {
                for (x in 4..7) {
                    this[y * inputWidth + x] = if (x == 4 || x == 7 || y == 2 || y == 9) 0.85f else 0.95f
                }
            }
        }
        val fallbackBox = TFLiteInferenceUtils.RawDetection(
            x1 = 1f,
            y1 = 2f,
            x2 = 5f,
            y2 = 10f,
            confidence = 0.92f,
            classIndex = 0
        )

        val baselinePolygon = invokeBuildNormalizedPolygon(
            mask = invokeScaleMaskToOriginal(mask, inputWidth, inputHeight, info),
            maskWidth = info.originalWidth,
            maskHeight = info.originalHeight,
            fallbackBox = fallbackBox
        )
        val localPolygon = invokeBuildNormalizedPolygonFromLocalMask(
            mask = mask,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            info = info,
            fallbackBox = fallbackBox
        )

        assertEquals(baselinePolygon, localPolygon)
    }

    private fun invokeScaleMaskToOriginal(
        mask: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        info: TFLiteInferenceUtils.LetterboxInfo,
        threshold: Float = 0.5f
    ): BooleanArray {
        val method = TFLiteInferenceUtils::class.java.getDeclaredMethod(
            "scaleMaskToOriginal",
            FloatArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            TFLiteInferenceUtils.LetterboxInfo::class.java,
            Float::class.javaPrimitiveType
        ).apply { isAccessible = true }

        return method.invoke(
            TFLiteInferenceUtils,
            mask,
            inputWidth,
            inputHeight,
            info,
            threshold
        ) as BooleanArray
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeBuildNormalizedPolygon(
        mask: BooleanArray,
        maskWidth: Int,
        maskHeight: Int,
        fallbackBox: TFLiteInferenceUtils.RawDetection
    ): List<TFLiteInferenceUtils.NormalizedPoint> {
        val method = TFLiteInferenceUtils::class.java.getDeclaredMethod(
            "buildNormalizedPolygon",
            BooleanArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            TFLiteInferenceUtils.RawDetection::class.java
        ).apply { isAccessible = true }

        return method.invoke(
            TFLiteInferenceUtils,
            mask,
            maskWidth,
            maskHeight,
            fallbackBox
        ) as List<TFLiteInferenceUtils.NormalizedPoint>
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeBuildNormalizedPolygonFromLocalMask(
        mask: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        info: TFLiteInferenceUtils.LetterboxInfo,
        fallbackBox: TFLiteInferenceUtils.RawDetection,
        threshold: Float = 0.5f
    ): List<TFLiteInferenceUtils.NormalizedPoint> {
        val method = TFLiteInferenceUtils::class.java.getDeclaredMethod(
            "buildNormalizedPolygonFromLocalMask",
            FloatArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            TFLiteInferenceUtils.LetterboxInfo::class.java,
            TFLiteInferenceUtils.RawDetection::class.java,
            Float::class.javaPrimitiveType
        ).apply { isAccessible = true }

        val result = method.invoke(
            TFLiteInferenceUtils,
            mask,
            inputWidth,
            inputHeight,
            info,
            fallbackBox,
            threshold
        ) ?: error("Expected local polygon build result")

        val polygonGetter = result.javaClass.getDeclaredMethod("getPolygon").apply { isAccessible = true }
        return polygonGetter.invoke(result) as List<TFLiteInferenceUtils.NormalizedPoint>
    }

    private fun legacyScaleMaskToOriginal(
        mask: FloatArray,
        inputWidth: Int,
        inputHeight: Int,
        info: TFLiteInferenceUtils.LetterboxInfo,
        threshold: Float = 0.5f
    ): BooleanArray {
        if (mask.isEmpty()) return BooleanArray(info.originalWidth * info.originalHeight)

        val scaledWidth = (info.originalWidth * info.scale).toInt().coerceIn(1, inputWidth)
        val scaledHeight = (info.originalHeight * info.scale).toInt().coerceIn(1, inputHeight)
        val cropLeft = info.padLeft.coerceIn(0, inputWidth - 1)
        val cropTop = info.padTop.coerceIn(0, inputHeight - 1)
        val cropWidth = minOf(scaledWidth, inputWidth - cropLeft).coerceAtLeast(1)
        val cropHeight = minOf(scaledHeight, inputHeight - cropTop).coerceAtLeast(1)

        val cropped = FloatArray(cropWidth * cropHeight)
        for (y in 0 until cropHeight) {
            val srcOffset = (cropTop + y) * inputWidth + cropLeft
            val dstOffset = y * cropWidth
            for (x in 0 until cropWidth) {
                cropped[dstOffset + x] = mask[srcOffset + x]
            }
        }

        val resized = legacyResizeFloatGrid(
            source = cropped,
            sourceWidth = cropWidth,
            sourceHeight = cropHeight,
            targetWidth = info.originalWidth,
            targetHeight = info.originalHeight
        )
        return BooleanArray(resized.size) { index -> resized[index] >= threshold }
    }

    private fun legacyResizeFloatGrid(
        source: FloatArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): FloatArray {
        if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
            return source.copyOf()
        }

        val result = FloatArray(targetWidth * targetHeight)
        val xScale = if (targetWidth > 1) (sourceWidth - 1).toFloat() / (targetWidth - 1) else 0f
        val yScale = if (targetHeight > 1) (sourceHeight - 1).toFloat() / (targetHeight - 1) else 0f

        for (y in 0 until targetHeight) {
            val srcY = y * yScale
            val y0 = floor(srcY).toInt().coerceIn(0, sourceHeight - 1)
            val y1 = minOf(y0 + 1, sourceHeight - 1)
            val yWeight = srcY - y0

            for (x in 0 until targetWidth) {
                val srcX = x * xScale
                val x0 = floor(srcX).toInt().coerceIn(0, sourceWidth - 1)
                val x1 = minOf(x0 + 1, sourceWidth - 1)
                val xWeight = srcX - x0

                val topLeft = source[y0 * sourceWidth + x0]
                val topRight = source[y0 * sourceWidth + x1]
                val bottomLeft = source[y1 * sourceWidth + x0]
                val bottomRight = source[y1 * sourceWidth + x1]

                val top = topLeft + (topRight - topLeft) * xWeight
                val bottom = bottomLeft + (bottomRight - bottomLeft) * xWeight
                result[y * targetWidth + x] = top + (bottom - top) * yWeight
            }
        }

        return result
    }

    private fun letterboxInfo(
        inputWidth: Int,
        inputHeight: Int,
        originalWidth: Int,
        originalHeight: Int,
        padLeft: Int,
        padTop: Int,
        scale: Float
    ): TFLiteInferenceUtils.LetterboxInfo {
        val bitmap = mockk<Bitmap>()
        every { bitmap.width } returns inputWidth
        every { bitmap.height } returns inputHeight
        return TFLiteInferenceUtils.LetterboxInfo(
            bitmap = bitmap,
            padLeft = padLeft,
            padTop = padTop,
            scale = scale,
            originalWidth = originalWidth,
            originalHeight = originalHeight
        )
    }
}
