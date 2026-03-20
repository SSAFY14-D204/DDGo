package com.ddgo.app.feature.climbing.record.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.domain.repository.LivePoseFrameInput
import com.ddgo.app.feature.climbing.record.presentation.RecordViewModel
import com.ddgo.app.feature.climbing.shared.model.ClimbingRecordThumbnailFrame
import com.ddgo.app.feature.climbing.shared.model.ClimbingRecordedAttemptDraft
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val CAMERA_FRAME_THROTTLE_MS = 100L

@Composable
fun RecordRoute(
    onNavigateBack: () -> Unit,
    onRecordedDraftReady: (ClimbingRecordedAttemptDraft) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: RecordViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionChanged(granted)
    }

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val nextFrameIndex = remember { AtomicInteger(0) }
    val lastSubmittedFrameTimestampMs = remember { AtomicLong(0L) }
    val latestThumbnailFrame = remember { AtomicReference<ClimbingRecordThumbnailFrame?>(null) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChanged(granted)
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.recordedDraft) {
        uiState.recordedDraft?.let { draft ->
            onRecordedDraftReady(draft)
            viewModel.onRecordedDraftHandled()
        }
    }

    DisposableEffect(cameraExecutor) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, uiState.hasCameraPermission) {
        if (!uiState.hasCameraPermission) {
            currentRecording?.stop()
            currentRecording = null
            videoCapture = null
            cameraProvider?.unbindAll()
            cameraProvider = null
            viewModel.onCameraUnbound()
            onDispose { }
        } else {
            val future = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            handleImageFrame(
                                imageProxy = imageProxy,
                                viewModel = viewModel,
                                lastSubmittedFrameTimestampMs = lastSubmittedFrameTimestampMs,
                                nextFrameIndex = nextFrameIndex,
                                latestThumbnailFrame = latestThumbnailFrame
                            )
                        }
                    }

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                val capture = VideoCapture.withOutput(recorder)

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                    capture
                )

                cameraProvider = provider
                videoCapture = capture
                nextFrameIndex.set(0)
                lastSubmittedFrameTimestampMs.set(0L)
                viewModel.onCameraBound()
            }

            future.addListener(listener, ContextCompat.getMainExecutor(context))

            onDispose {
                currentRecording?.stop()
                currentRecording = null
                videoCapture = null
                cameraProvider?.unbindAll()
                cameraProvider = null
                viewModel.onCameraUnbound()
            }
        }
    }

    RecordPage(
        uiState = uiState,
        previewContent = {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        },
        onNavigateBack = onNavigateBack,
        onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onStartRecording = {
            val capture = videoCapture ?: return@RecordPage
            val outputFile = context.createRecordedVideoFile()
            val outputOptions = FileOutputOptions.Builder(outputFile).build()
            currentRecording = capture.output
                .prepareRecording(context, outputOptions)
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            latestThumbnailFrame.set(null)
                            viewModel.onRecordingStarted()
                        }

                        is VideoRecordEvent.Finalize -> {
                            currentRecording = null
                            if (event.hasError()) {
                                viewModel.onRecordingFailed(
                                    event.cause?.message ?: "Recording failed."
                                )
                            } else {
                                val outputUri = event.outputResults.outputUri
                                    .takeIf { it != Uri.EMPTY }
                                    ?: Uri.fromFile(outputFile)
                                viewModel.onRecordingStopped(
                                    ClimbingRecordedAttemptDraft(
                                        videoUri = outputUri.toString(),
                                        thumbnailFrame = latestThumbnailFrame.get()
                                    )
                                )
                            }
                        }
                    }
                }
        },
        onStopRecording = {
            currentRecording?.stop()
        },
        onRetryLivePose = viewModel::retryLivePoseAnalysis,
        onClearDraft = viewModel::clearRecordedDraft
    )
}

private fun handleImageFrame(
    imageProxy: ImageProxy,
    viewModel: RecordViewModel,
    lastSubmittedFrameTimestampMs: AtomicLong,
    nextFrameIndex: AtomicInteger,
    latestThumbnailFrame: AtomicReference<ClimbingRecordThumbnailFrame?>
) {
    try {
        val timestampMs = imageProxy.imageInfo.timestamp.let { timestampNs ->
            if (timestampNs > 0L) {
                TimeUnit.NANOSECONDS.toMillis(timestampNs)
            } else {
                SystemClock.elapsedRealtime()
            }
        }

        if (timestampMs - lastSubmittedFrameTimestampMs.get() < CAMERA_FRAME_THROTTLE_MS) {
            return
        }

        val frameIndex = nextFrameIndex.getAndIncrement()
        val frame = imageProxy.toLivePoseFrameInput(
            frameIndex = frameIndex,
            timestampMs = timestampMs
        ) ?: return

        lastSubmittedFrameTimestampMs.set(timestampMs)
        latestThumbnailFrame.set(
            ClimbingRecordThumbnailFrame(
                frameIndex = frameIndex,
                timestampMs = timestampMs,
                width = frame.width,
                height = frame.height,
                rotationDegrees = frame.rotationDegrees
            )
        )
        viewModel.submitLivePoseFrame(frame)
    } finally {
        imageProxy.close()
    }
}

private fun ImageProxy.toLivePoseFrameInput(
    frameIndex: Int,
    timestampMs: Long
): LivePoseFrameInput? {
    val argbBytes = toArgb8888Bytes() ?: return null
    return LivePoseFrameInput(
        frameIndex = frameIndex,
        timestampMs = timestampMs,
        width = width,
        height = height,
        rotationDegrees = imageInfo.rotationDegrees,
        argb8888Bytes = argbBytes
    )
}

private fun ImageProxy.toArgb8888Bytes(): ByteArray? {
    val image = image ?: return null
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + minOf(uSize, vSize) * 2)

    yBuffer.get(nv21, 0, ySize)

    var offset = ySize
    val chromaRowStride = uPlane.rowStride
    val chromaPixelStride = uPlane.pixelStride
    val uBytes = ByteArray(uSize)
    val vBytes = ByteArray(vSize)
    uBuffer.get(uBytes)
    vBuffer.get(vBytes)

    val chromaHeight = height / 2
    val chromaWidth = width / 2
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val index = row * chromaRowStride + col * chromaPixelStride
            if (index < vBytes.size && index < uBytes.size && offset + 1 < nv21.size) {
                nv21[offset++] = vBytes[index]
                nv21[offset++] = uBytes[index]
            }
        }
    }

    val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val stream = ByteArrayOutputStream()
    if (!yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, stream)) {
        return null
    }

    val jpegBytes = stream.toByteArray()
    var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null
    if (bitmap.config != Bitmap.Config.ARGB_8888) {
        val converted = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        bitmap.recycle()
        bitmap = converted
    }

    val rotated = if (imageInfo.rotationDegrees == 0) {
        bitmap
    } else {
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) },
            true
        )
    }
    if (rotated !== bitmap) {
        bitmap.recycle()
    }

    val buffer = ByteBuffer.allocate(rotated.byteCount).order(ByteOrder.nativeOrder())
    rotated.copyPixelsToBuffer(buffer)
    rotated.recycle()
    return buffer.array()
}

private fun Context.createRecordedVideoFile() =
    java.io.File(cacheDir, "recorded-${System.currentTimeMillis()}.mp4")
