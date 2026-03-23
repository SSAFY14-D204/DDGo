package com.ddgo.app.feature.climbing.record.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.repository.LivePoseFrameInput
import com.ddgo.app.feature.climbing.record.presentation.RecordThumbnailFrame
import com.ddgo.app.feature.climbing.record.presentation.RecordViewModel
import com.ddgo.app.feature.climbing.record.presentation.RecordedAttemptDraft
import com.ddgo.app.feature.climbing.upload.RealtimeAttemptActionState
import com.ddgo.app.feature.climbing.upload.UploadRealtimeOverlayUiState
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
    onRecordedDraftReady: (RecordedAttemptDraft) -> Unit = {},
    realtimeOverlayUiState: UploadRealtimeOverlayUiState? = null,
    realtimeAttemptActionState: RealtimeAttemptActionState = RealtimeAttemptActionState.Idle,
    onOpenGymList: () -> Unit = {},
    onSearchNearbyGyms: (Double, Double, String, Boolean) -> Unit = { _, _, _, _ -> },
    onSearchQueryChange: (String) -> Unit = {},
    onSelectGym: (NearbyPlace) -> Unit = {},
    onSelectDifficulty: (GymGrade) -> Unit = {},
    onSelectHoldColor: (String) -> Unit = {},
    onSetHoldColorSheetVisible: (Boolean) -> Unit = {},
    onTapFinish: () -> Unit = {},
    onTapRetake: () -> Unit = {},
    onTorchToggle: (Boolean) -> Unit = {}
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
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var isResolvingLocation by remember { mutableStateOf(false) }
    var pendingSearchRequest by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionChanged(granted)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            pendingSearchRequest?.let { (query, nearbyOnly) ->
                requestNearbyGymSearch(
                    context = context,
                    overlayUiState = realtimeOverlayUiState,
                    query = query,
                    nearbyOnly = nearbyOnly,
                    onSearchNearbyGyms = onSearchNearbyGyms,
                    onLocationResolvingChanged = { isResolvingLocation = it },
                    onLocationMessageChanged = { locationMessage = it }
                )
            }
        } else {
            isResolvingLocation = false
            locationMessage = "위치 권한이 필요해요."
        }
    }

    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var currentRecording by remember { mutableStateOf<Recording?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    val nextFrameIndex = remember { AtomicInteger(0) }
    val lastSubmittedFrameTimestampMs = remember { AtomicLong(0L) }
    val latestThumbnailFrame = remember { AtomicReference<RecordThumbnailFrame?>(null) }

    fun resetDraftState() {
        latestThumbnailFrame.set(null)
        nextFrameIndex.set(0)
        lastSubmittedFrameTimestampMs.set(0L)
        viewModel.clearRecordedDraft()
        if (uiState.livePoseErrorMessage != null) {
            viewModel.retryLivePoseAnalysis()
        }
    }

    fun startLocationBackedGymSearch(
        query: String = realtimeOverlayUiState?.searchQuery.orEmpty(),
        nearbyOnly: Boolean = query.isBlank()
    ) {
        pendingSearchRequest = query to nearbyOnly
        if (!hasLocationPermission(context)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        requestNearbyGymSearch(
            context = context,
            overlayUiState = realtimeOverlayUiState,
            query = query,
            nearbyOnly = nearbyOnly,
            onSearchNearbyGyms = onSearchNearbyGyms,
            onLocationResolvingChanged = { isResolvingLocation = it },
            onLocationMessageChanged = { locationMessage = it }
        )
    }

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
            camera?.cameraControl?.enableTorch(false)
            camera = null
            torchEnabled = false
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
                val boundCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                    capture
                )

                camera = boundCamera
                cameraProvider = provider
                videoCapture = capture
                nextFrameIndex.set(0)
                lastSubmittedFrameTimestampMs.set(0L)
                torchEnabled = false
                boundCamera.cameraControl.enableTorch(false)
                viewModel.onCameraBound()
            }

            future.addListener(listener, ContextCompat.getMainExecutor(context))

            onDispose {
                currentRecording?.stop()
                currentRecording = null
                videoCapture = null
                camera?.cameraControl?.enableTorch(false)
                camera = null
                torchEnabled = false
                cameraProvider?.unbindAll()
                cameraProvider = null
                viewModel.onCameraUnbound()
            }
        }
    }

    fun startRecording() {
        if (currentRecording != null) {
            return
        }
        if (realtimeOverlayUiState != null && !realtimeOverlayUiState.isChallengeReady) {
            return
        }

        val capture = videoCapture ?: return
        val outputFile = context.createRecordedVideoFile()
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        onSetHoldColorSheetVisible(false)
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
                                RecordedAttemptDraft(
                                    videoUri = outputUri.toString(),
                                    thumbnailFrame = latestThumbnailFrame.get()
                                )
                            )
                        }
                    }
                }
            }
    }

    val previewContent: @Composable BoxScope.() -> Unit = {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
    val overlayContent: @Composable BoxScope.() -> Unit = {
        LivePoseOverlay(
            modifier = Modifier.fillMaxSize(),
            poseFrame = uiState.latestPoseFrame
        )
    }

    if (realtimeOverlayUiState != null) {
        RealtimeRecordPage(
            uiState = uiState,
            realtimeOverlayUiState = realtimeOverlayUiState,
            isTorchEnabled = torchEnabled,
            isTorchAvailable = camera?.cameraInfo?.hasFlashUnit() == true,
            locationMessage = locationMessage,
            isResolvingLocation = isResolvingLocation,
            attemptStatusLabel = realtimeAttemptActionState.toLabel(),
            previewContent = previewContent,
            overlayContent = overlayContent,
            onNavigateBack = onNavigateBack,
            onRequestCameraPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenGymSelector = {
                onOpenGymList()
                startLocationBackedGymSearch()
            },
            onSearchQueryChange = onSearchQueryChange,
            onSearchSubmit = { query ->
                onSearchQueryChange(query)
                startLocationBackedGymSearch(
                    query = query,
                    nearbyOnly = query.isBlank()
                )
            },
            onSelectGym = {
                locationMessage = null
                onSelectGym(it)
            },
            onSelectDifficulty = onSelectDifficulty,
            onTapShutter = {
                if (uiState.isRecording) {
                    currentRecording?.stop()
                } else {
                    startRecording()
                }
            },
            onLongPressShutter = {
                if (!uiState.isRecording && realtimeOverlayUiState.isChallengeReady) {
                    onSetHoldColorSheetVisible(true)
                }
            },
            onTapFinish = onTapFinish,
            onTapRetake = {
                resetDraftState()
                onTapRetake()
            },
            onTapFlash = {
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    val nextEnabled = !torchEnabled
                    torchEnabled = nextEnabled
                    runCatching {
                        camera?.cameraControl?.enableTorch(nextEnabled)
                    }
                    onTorchToggle(nextEnabled)
                }
            },
            onSelectHoldColor = onSelectHoldColor,
            onDismissHoldColorSheet = {
                onSetHoldColorSheetVisible(false)
            }
        )
    } else {
        RecordPage(
            uiState = uiState,
            previewContent = previewContent,
            onNavigateBack = onNavigateBack,
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onStartRecording = ::startRecording,
            onStopRecording = {
                currentRecording?.stop()
            },
            onRetryLivePose = viewModel::retryLivePoseAnalysis,
            onClearDraft = ::resetDraftState
        )
    }
}

private fun RealtimeAttemptActionState.toLabel(): String? {
    return when (this) {
        RealtimeAttemptActionState.Idle -> null
        RealtimeAttemptActionState.ShowingOptions -> "실시간 분석 준비"
        RealtimeAttemptActionState.RetakeRequested -> "재촬영 준비 완료"
        RealtimeAttemptActionState.FinalAnalysisRequested -> "최종 분석 준비"
    }
}

private fun requestNearbyGymSearch(
    context: Context,
    overlayUiState: UploadRealtimeOverlayUiState?,
    query: String,
    nearbyOnly: Boolean,
    onSearchNearbyGyms: (Double, Double, String, Boolean) -> Unit,
    onLocationResolvingChanged: (Boolean) -> Unit,
    onLocationMessageChanged: (String?) -> Unit
) {
    val cachedLatitude = overlayUiState?.lastSearchLatitude
    val cachedLongitude = overlayUiState?.lastSearchLongitude
    if (cachedLatitude != null && cachedLongitude != null) {
        onLocationResolvingChanged(false)
        onLocationMessageChanged(null)
        onSearchNearbyGyms(cachedLatitude, cachedLongitude, query, nearbyOnly)
        return
    }

    onLocationResolvingChanged(true)
    requestBestLocation(
        context = context,
        onSuccess = { latitude, longitude ->
            onLocationResolvingChanged(false)
            onLocationMessageChanged(null)
            onSearchNearbyGyms(latitude, longitude, query, nearbyOnly)
        },
        onError = { message ->
            onLocationResolvingChanged(false)
            onLocationMessageChanged(message)
        }
    )
}

private fun handleImageFrame(
    imageProxy: ImageProxy,
    viewModel: RecordViewModel,
    lastSubmittedFrameTimestampMs: AtomicLong,
    nextFrameIndex: AtomicInteger,
    latestThumbnailFrame: AtomicReference<RecordThumbnailFrame?>
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
            RecordThumbnailFrame(
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

private fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

@SuppressLint("MissingPermission")
private fun requestBestLocation(
    context: Context,
    onSuccess: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onError("위치 권한이 필요해요.")
        return
    }

    val locationManager = context.getSystemService(LocationManager::class.java)
        ?: run {
            onError("위치 서비스를 사용할 수 없어요.")
            return
        }

    val providers = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter(locationManager::isProviderEnabled)

    if (providers.isEmpty()) {
        onError("위치 서비스를 켜주세요.")
        return
    }

    val cachedLocation = providers
        .mapNotNull(locationManager::getLastKnownLocation)
        .maxByOrNull { it.time }

    if (cachedLocation != null) {
        onSuccess(cachedLocation.latitude, cachedLocation.longitude)
        return
    }

    requestCurrentLocation(
        context = context,
        locationManager = locationManager,
        providers = providers,
        providerIndex = 0,
        onSuccess = onSuccess,
        onError = onError
    )
}

@SuppressLint("MissingPermission")
private fun requestCurrentLocation(
    context: Context,
    locationManager: LocationManager,
    providers: List<String>,
    providerIndex: Int,
    onSuccess: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    if (providerIndex >= providers.size) {
        onError("현재 위치를 가져오지 못했어요.")
        return
    }

    val provider = providers[providerIndex]
    val cancellationSignal = CancellationSignal()
    var completed = false

    val locationConsumer = Consumer<Location> { location ->
        if (completed) {
            return@Consumer
        }

        if (location != null) {
            completed = true
            onSuccess(location.latitude, location.longitude)
        } else {
            completed = true
            requestCurrentLocation(
                context = context,
                locationManager = locationManager,
                providers = providers,
                providerIndex = providerIndex + 1,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    runCatching {
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            cancellationSignal,
            ContextCompat.getMainExecutor(context),
            locationConsumer
        )
    }.onFailure {
        requestCurrentLocation(
            context = context,
            locationManager = locationManager,
            providers = providers,
            providerIndex = providerIndex + 1,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}
