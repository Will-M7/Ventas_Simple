package com.example.sistema_tienda.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

private const val TAG = "SistematiendaScanner"
private const val SCAN_TAG = "POS_SCAN"
private const val FRAME_HEIGHT_DP = 116
private const val CONFIRMATIONS = 2
private const val CONFIRM_WINDOW_MS = 2_000L
private const val REPEAT_COOLDOWN_MS = 2_000L

enum class ScannerMode { SINGLE, CONTINUOUS_SALE }

class BarcodeFeedback : AutoCloseable {
    private val tone = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (failure: RuntimeException) {
        Log.w(TAG, "No se pudo iniciar el sonido del escáner", failure)
        null
    }

    fun accepted() {
        try {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "No se pudo reproducir el sonido del escáner", failure)
        }
    }

    override fun close() {
        try {
            tone?.release()
        } catch (failure: RuntimeException) {
            Log.w(TAG, "No se pudo liberar el sonido del escáner", failure)
        }
    }
}

@Composable fun rememberBarcodeFeedback(): BarcodeFeedback {
    val feedback = remember { BarcodeFeedback() }
    DisposableEffect(feedback) { onDispose { feedback.close() } }
    return feedback
}

@Composable fun BarcodeScanner(
    onCode: (String) -> Unit,
    onClose: () -> Unit = {},
    onManualCode: ((String) -> Unit)? = null,
    mode: ScannerMode = ScannerMode.SINGLE,
    beepOnDecode: Boolean = mode == ScannerMode.SINGLE,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentBeep by rememberUpdatedState(beepOnDecode)
    val feedback = rememberBarcodeFeedback()
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var requested by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var torch by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var manual by remember { mutableStateOf(false) }
    var manualValue by remember { mutableStateOf("") }
    val runtime = remember(lifecycleOwner, mode) { ScannerRuntime(mode) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
        requested = true
    }

    LaunchedEffect(Unit) { if (!allowed) permission.launch(Manifest.permission.CAMERA) }
    DisposableEffect(runtime) { onDispose { runtime.close() } }
    BackHandler { onClose() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(camera) {
                detectTransformGestures { _, _, gestureZoom, _ ->
                    camera?.let { bound ->
                        runtime.manualZoomUntil = SystemClock.elapsedRealtime() + 4_000L
                        zoom = (zoom * gestureZoom).coerceIn(minZoom, maxZoom)
                        bound.cameraControl.setZoomRatio(zoom)
                    }
                }
            }
            .pointerInput(camera) {
                detectTapGestures { offset ->
                    camera?.let { bound ->
                        previewView?.meteringPointFactory?.createPoint(offset.x, offset.y)?.let { point ->
                            bound.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                            )
                        }
                    }
                }
            }
    ) {
        if (allowed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx: Context -> PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                } },
                update = { view ->
                    runtime.start(
                        view, lifecycleOwner,
                        onReady = { bound ->
                            camera = bound
                            hasFlash = bound.cameraInfo.hasFlashUnit()
                            val state = bound.cameraInfo.zoomState.value
                            minZoom = state?.minZoomRatio ?: 1f
                            maxZoom = state?.maxZoomRatio ?: 1f
                            zoom = state?.zoomRatio ?: 1f
                            torch = false
                        },
                        onError = { error = "No se pudo iniciar la cámara." },
                        onZoom = { zoom = it },
                        onDetected = { value ->
                            if (currentBeep) feedback.accepted()
                            currentOnCode(value)
                        }
                    )
                }
            )
        } else if (requested) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Se necesita permiso de cámara para escanear códigos.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onClose) { Text("Volver") }
            }
        }
        TopAppBar(
            title = { Text(if (mode == ScannerMode.CONTINUOUS_SALE) "Escanear venta" else "Scanner", color = Color.White) },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Cerrar", tint = Color.White) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        Box(Modifier.align(Alignment.Center).fillMaxWidth(0.86f).height(FRAME_HEIGHT_DP.dp).border(1.dp, Color.White.copy(alpha = .8f)))
        Text("Centra el código dentro del marco y mantenlo quieto", color = Color.White.copy(alpha = .9f),
            modifier = Modifier.align(Alignment.Center).offset(y = 76.dp))
        Text("${"%.1f".format(zoom)}x", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(18.dp))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            bottomContent?.invoke(this)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasFlash) OutlinedButton(onClick = {
                    camera?.let { bound ->
                        val requestedTorch = !torch
                        val result = bound.cameraControl.enableTorch(requestedTorch)
                        result.addListener({
                            try { result.get(); torch = requestedTorch }
                            catch (failure: Exception) { Log.w(TAG, "No se pudo cambiar la linterna", failure) }
                        }, ContextCompat.getMainExecutor(context))
                    }
                }) {
                    Icon(Icons.Outlined.FlashlightOn, "Linterna", tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(if (torch) "Apagar" else "Linterna", color = Color.White)
                }
                OutlinedButton(onClick = { manual = true }) {
                    Icon(Icons.Outlined.QrCodeScanner, "Código", tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Código manual", color = Color.White)
                }
            }
        }
        error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(top = 220.dp)) }
    }
    if (manual) AlertDialog(
        onDismissRequest = { manual = false },
        title = { Text("Ingresar código manualmente") },
        text = { OutlinedTextField(manualValue, { manualValue = it }, label = { Text("Código") }, singleLine = true) },
        confirmButton = { Button(enabled = manualValue.isNotBlank(), onClick = {
            manual = false
            onManualCode?.invoke(manualValue.trim()) ?: onCode(manualValue.trim())
        }) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancelar") } }
    )
}

private data class Candidate(
    val value: String, val format: Int, val distance: Float,
    val insideRoi: Boolean, val largeEnough: Boolean
)

private class ScanLog {
    private val last = mutableMapOf<String, Long>()

    fun event(key: String, message: String, intervalMs: Long = 1_000L) {
        val now = SystemClock.elapsedRealtime()
        if (now - (last[key] ?: -intervalMs) < intervalMs) return
        last[key] = now
        Log.d(SCAN_TAG, message)
    }
}

private class Confirmation(private val mode: ScannerMode) {
    private val log = ScanLog()
    private var candidate: Candidate? = null
    private var count = 0
    private var firstSeen = 0L
    private var lastAccepted: String? = null
    private var acceptedAt = 0L
    private var absentFrames = 0
    private var rearmed = true

    fun observe(next: Candidate?): String? {
        val now = SystemClock.elapsedRealtime()
        if (next?.value != lastAccepted) {
            absentFrames++
            if (absentFrames >= 6) rearmed = true
        } else {
            absentFrames = 0
        }
        if (next == null) {
            if (candidate != null) log.event("lost:${candidate?.value}", "rejected reason=LOST_CANDIDATE raw=${candidate?.value} confirmed=$count/$CONFIRMATIONS")
            candidate = null
            count = 0
            return null
        }
        if (candidate?.value != next.value || candidate?.format != next.format || now - firstSeen > CONFIRM_WINDOW_MS) {
            if (candidate != null && count > 0) {
                val reason = if (now - firstSeen > CONFIRM_WINDOW_MS) "CONFIRMATION_TIMEOUT" else "CANDIDATE_CHANGED"
                log.event("reset:${candidate?.value}:$reason",
                    "rejected reason=$reason raw=${candidate?.value} confirmed=$count/$CONFIRMATIONS")
            }
            candidate = next
            count = 1
            firstSeen = now
        } else {
            count++
        }
        log.event("count:${next.value}:$count", "confirmed $count/$CONFIRMATIONS raw=${next.value} format=${formatName(next.format)}", 500L)
        if (count < CONFIRMATIONS) return null
        if (mode == ScannerMode.SINGLE && lastAccepted != null) {
            log.event("single:${next.value}", "rejected reason=SINGLE_ALREADY_DELIVERED raw=${next.value}")
            return null
        }
        if (next.value == lastAccepted && (!rearmed || now - acceptedAt < REPEAT_COOLDOWN_MS)) {
            log.event("cooldown:${next.value}", "rejected reason=COOLDOWN raw=${next.value} rearmed=$rearmed")
            return null
        }
        lastAccepted = next.value
        acceptedAt = now
        rearmed = false
        count = 0
        Log.d(SCAN_TAG, "onCode raw=${next.value} format=${formatName(next.format)}")
        return next.value
    }
}

private class ScannerRuntime(private val mode: ScannerMode) {
    private val confirmation = Confirmation(mode)
    private val log = ScanLog()
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var active = true
    private var starting = false
    private var pending = 0
    private var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private val transformFactory = ImageProxyTransformFactory()
    var manualZoomUntil = 0L

    fun start(
        view: PreviewView,
        owner: LifecycleOwner,
        onReady: (Camera) -> Unit,
        onError: () -> Unit,
        onZoom: (Float) -> Unit,
        onDetected: (String) -> Unit
    ) {
        if (!active || starting) return
        starting = true
        if (view.width == 0 || view.height == 0) {
            view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    changed: View, left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                ) {
                    if (right <= left || bottom <= top) return
                    changed.removeOnLayoutChangeListener(this)
                    starting = false
                    if (active) start(view, owner, onReady, onError, onZoom, onDetected)
                }
            })
            return
        }
        view.post {
            if (!active) return@post
            val future = ProcessCameraProvider.getInstance(view.context)
            future.addListener({
                if (!active) return@addListener
                try {
                    val cameraProvider = future.get()
                    val viewport = view.viewPort ?: throw IllegalStateException("PreviewView todavía no tiene ViewPort")
                    val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val analysisUseCase = ImageAnalysis.Builder()
                        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER)
                        ).build())
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val group = UseCaseGroup.Builder().addUseCase(previewUseCase).addUseCase(analysisUseCase)
                        .setViewPort(viewport).build()
                    val bound = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                    if (!active) {
                        cameraProvider.unbind(previewUseCase, analysisUseCase)
                        return@addListener
                    }
                    provider = cameraProvider
                    preview = previewUseCase
                    analysis = analysisUseCase
                    onReady(bound)
                    val zoomState = bound.cameraInfo.zoomState.value
                    val minZoom = zoomState?.minZoomRatio ?: 1f
                    val maxZoom = zoomState?.maxZoomRatio ?: 1f
                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128
                        )
                        .enableAllPotentialBarcodes()
                        .setZoomSuggestionOptions(ZoomSuggestionOptions.Builder { suggestion ->
                            if (!active || SystemClock.elapsedRealtime() < manualZoomUntil) {
                                false
                            } else {
                                val current = bound.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                                val limited = suggestion.coerceIn(minZoom, minOf(maxZoom, 3f))
                                if (limited <= current + 0.05f) false
                                else {
                                    bound.cameraControl.setZoomRatio(limited)
                                    onZoom(limited)
                                    true
                                }
                            }
                        }.setMaxSupportedZoomRatio(minOf(maxZoom, 3f)).build())
                        .build()
                    val detector = BarcodeScanning.getClient(options)
                    synchronized(lock) { scanner = detector }
                    analysisUseCase.setAnalyzer(executor) { proxy ->
                        analyze(proxy, detector, view, onDetected)
                    }
                    view.post {
                        if (active && view.width > 0 && view.height > 0) {
                            val center = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
                            bound.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(center, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                            )
                        }
                    }
                } catch (failure: Exception) {
                    Log.e(TAG, "Error CameraX", failure)
                    if (active) onError()
                    close()
                }
            }, ContextCompat.getMainExecutor(view.context))
        }
    }

    private fun analyze(
        proxy: ImageProxy,
        detector: com.google.mlkit.vision.barcode.BarcodeScanner,
        view: PreviewView,
        onDetected: (String) -> Unit
    ) {
        val image = proxy.image
        if (image == null || !begin()) {
            proxy.close()
            return
        }
        val finished = AtomicBoolean(false)
        fun finish() {
            if (finished.compareAndSet(false, true)) {
                proxy.close()
                end()
            }
        }
        try {
            // ML Kit returns boxes in the rotated image's coordinates. The CameraX
            // source transform must therefore use the unrotated buffer setting.
            val source = transformFactory.getOutputTransform(proxy)
            val input = InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)
            detector.process(input)
                .addOnSuccessListener { codes ->
                    if (active) {
                        log.event("count", "barcodes=${codes.size}", if (codes.isEmpty()) 5_000L else 1_000L)
                        try {
                            val target = view.outputTransform
                            val selected = select(codes, source, target, view, proxy)
                            if (selected != null) log.event("selected:${selected.value}",
                                "candidate raw=${selected.value} format=${formatName(selected.format)} insideROI=${selected.insideRoi} minSize=${selected.largeEnough} distance=${selected.distance}")
                            confirmation.observe(selected)?.let { if (active) onDetected(it) }
                        } catch (failure: Exception) {
                            Log.w(SCAN_TAG, "rejected reason=SELECTION_ERROR", failure)
                            confirmation.observe(null)
                        }
                    }
                }
                .addOnFailureListener { Log.w(SCAN_TAG, "rejected reason=MLKIT_ERROR", it) }
                .addOnCompleteListener { finish() }
        } catch (failure: Exception) {
            Log.w(SCAN_TAG, "rejected reason=ANALYSIS_ERROR", failure)
            finish()
        }
    }

    private fun select(
        codes: List<Barcode>, source: OutputTransform, target: OutputTransform?,
        view: PreviewView, proxy: ImageProxy
    ): Candidate? {
        val frameWidth = view.width * .86f
        val frameHeight = FRAME_HEIGHT_DP * view.resources.displayMetrics.density
        val frame = RectF(
            (view.width - frameWidth) / 2f, (view.height - frameHeight) / 2f,
            (view.width + frameWidth) / 2f, (view.height + frameHeight) / 2f
        )
        val centerX = frame.centerX()
        val centerY = frame.centerY()
        val transform = target?.let {
            runCatching { CoordinateTransform(source, it) }
                .onFailure { failure -> log.event("transform", "rejected reason=TRANSFORM_ERROR message=${failure.message}") }
                .getOrNull()
        }
        if (target == null) log.event("transform_null", "geometry reason=PREVIEW_TRANSFORM_UNAVAILABLE")
        val rotatedWidth = if (proxy.imageInfo.rotationDegrees % 180 == 0) proxy.width else proxy.height
        val candidates = codes.mapNotNull { code ->
            val value = code.rawValue?.trim()
            log.event("raw:${code.format}:$value", "raw=$value format=${formatName(code.format)}(${code.format}) box=${code.boundingBox}")
            if (value == null) {
                log.event("undecoded", "rejected reason=UNDECODED_POTENTIAL format=${formatName(code.format)} box=${code.boundingBox}")
                return@mapNotNull null
            }
            val rejection = productCodeRejection(code.format, value)
            if (rejection != null) {
                log.event("reject:$value:$rejection", "rejected reason=$rejection raw=$value format=${formatName(code.format)}")
                return@mapNotNull null
            }
            val validation = if (code.format == Barcode.FORMAT_CODE_128) "checksum=NOT_APPLICABLE" else "checksum=PASS"
            log.event("valid:$value", "$validation raw=$value format=${formatName(code.format)}")
            val box = code.boundingBox
            val mapped = if (box != null && transform != null) runCatching {
                RectF(box).also { transform.mapRect(it) }
            }.onFailure { failure ->
                log.event("map:$value", "geometry reason=MAP_ERROR raw=$value message=${failure.message}")
            }.getOrNull() else null
            val inside = mapped?.let { frame.contains(it.centerX(), it.centerY()) } ?: false
            val largeEnough = box?.width()?.let { it >= rotatedWidth * .075f } ?: false
            log.event("geometry:$value", "geometry raw=$value mapped=$mapped insideROI=$inside minSize=$largeEnough frame=$frame")
            val distance = mapped?.let {
                hypot((it.centerX() - centerX) / frameWidth, (it.centerY() - centerY) / frameHeight)
            } ?: Float.MAX_VALUE
            Candidate(value, code.format, distance, inside, largeEnough)
        }.sortedWith(compareByDescending<Candidate> { it.insideRoi }
            .thenByDescending { it.largeEnough }
            .thenBy { it.distance })
        if (candidates.size == 1) {
            if (candidates[0].distance == Float.MAX_VALUE || candidates[0].distance > .5f)
                log.event("single_fallback:${candidates[0].value}", "geometry reason=SINGLE_CANDIDATE_FALLBACK raw=${candidates[0].value}")
            return candidates[0]
        }
        if (candidates.size > 1 && candidates[0].distance == Float.MAX_VALUE) {
            log.event("ambiguous_transform", "rejected reason=AMBIGUOUS_NO_GEOMETRY candidates=${candidates.size}")
            return null
        }
        if (candidates.size > 1 &&
            candidates[0].insideRoi == candidates[1].insideRoi &&
            candidates[0].largeEnough == candidates[1].largeEnough &&
            candidates[1].distance - candidates[0].distance < .10f
        ) {
            log.event("ambiguous", "rejected reason=AMBIGUOUS_CENTER candidates=${candidates.size}")
            return null
        }
        candidates.drop(1).forEach { other ->
            log.event("not_selected:${other.value}",
                "rejected reason=NOT_SELECTED_SPATIALLY raw=${other.value} insideROI=${other.insideRoi} distance=${other.distance}")
        }
        return candidates.firstOrNull()
    }

    private fun begin(): Boolean = synchronized(lock) {
        if (!active) false else { pending++; true }
    }

    private fun end() = synchronized(lock) {
        pending--
        if (!active && pending == 0) {
            scanner?.close()
            scanner = null
        }
    }

    fun close() {
        synchronized(lock) {
            if (!active) return
            active = false
            if (pending == 0) {
                scanner?.close()
                scanner = null
            }
        }
        analysis?.clearAnalyzer()
        val bound = listOfNotNull(preview, analysis)
        if (bound.isNotEmpty()) provider?.unbind(*bound.toTypedArray())
        executor.shutdown()
    }
}

private fun productCodeRejection(format: Int, value: String): String? = when (format) {
    Barcode.FORMAT_EAN_13 -> when { value.length != 13 -> "LENGTH"; !validMod10(value) -> "CHECKSUM"; else -> null }
    Barcode.FORMAT_EAN_8 -> when { value.length != 8 -> "LENGTH"; !validMod10(value) -> "CHECKSUM"; else -> null }
    Barcode.FORMAT_UPC_A -> when { value.length != 12 -> "LENGTH"; !validMod10(value) -> "CHECKSUM"; else -> null }
    Barcode.FORMAT_UPC_E -> when { value.length != 8 -> "LENGTH"; !validUpcE(value) -> "CHECKSUM"; else -> null }
    Barcode.FORMAT_CODE_128 -> if (value.length in 4..80 && value.none { it.isISOControl() }) null else "LENGTH_OR_CONTROL"
    else -> "FORMAT"
}

private fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    else -> "OTHER"
}

private fun validMod10(value: String): Boolean {
    if (!value.all(Char::isDigit)) return false
    val sum = value.dropLast(1).reversed().mapIndexed { index, digit ->
        (digit - '0') * if (index % 2 == 0) 3 else 1
    }.sum()
    return (10 - sum % 10) % 10 == value.last() - '0'
}

private fun validUpcE(value: String): Boolean {
    if (value.length != 8 || !value.all(Char::isDigit) || value[0] !in "01") return false
    val numberSystem = value[0]
    val body = value.substring(1, 7)
    val last = body[5]
    val manufacturer: String
    val product: String
    when (last) {
        '0', '1', '2' -> {
            manufacturer = body.substring(0, 2) + last + "00"
            product = "00" + body.substring(2, 5)
        }
        '3' -> {
            manufacturer = body.substring(0, 3) + "00"
            product = "000" + body.substring(3, 5)
        }
        '4' -> {
            manufacturer = body.substring(0, 4) + "0"
            product = "0000" + body[4]
        }
        else -> {
            manufacturer = body.substring(0, 5)
            product = "0000" + last
        }
    }
    return validMod10("$numberSystem$manufacturer$product${value[7]}")
}
