package com.example.sistema_tienda.ui

import android.Manifest
import android.util.Log
import android.content.pm.PackageManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeScanner(onCode: (String) -> Unit) {
    val context=LocalContext.current; val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current; var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    if(!allowed){ androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){ allowed=it }.launch(Manifest.permission.CAMERA); return }
    AndroidView(modifier=Modifier.fillMaxSize(), factory={ ctx -> PreviewView(ctx).also { view -> val future=ProcessCameraProvider.getInstance(ctx); future.addListener({ val provider=future.get(); val handled=AtomicBoolean(false); Log.d("POS_TEST", "Cámara inicializada"); val preview=Preview.Builder().build().also { it.surfaceProvider=view.surfaceProvider }; val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build(); val executor=Executors.newSingleThreadExecutor(); val scanner=BarcodeScanning.getClient(); analysis.setAnalyzer(executor){ proxy -> val image=proxy.image; if(image==null){proxy.close();return@setAnalyzer}; scanner.process(InputImage.fromMediaImage(image,proxy.imageInfo.rotationDegrees)).addOnSuccessListener { barcodes -> barcodes.firstOrNull()?.rawValue?.let { code -> if (handled.compareAndSet(false, true)) { Log.d("POS_TEST", "Código detectado por cámara: $code"); onCode(code); provider.unbindAll() } } }.addOnFailureListener { error -> Log.d("POS_TEST", "Error leyendo código: ${error.message}") }.addOnCompleteListener { proxy.close() } }; provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis) },ContextCompat.getMainExecutor(ctx)) } })
}
