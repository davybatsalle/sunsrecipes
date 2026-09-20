package com.sunrecipes.app

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(viewModel: RecipeViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val busy by viewModel.isScanning.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }, ContextCompat.getMainExecutor(context))
        onDispose { providerFuture.get().unbindAll() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scanner une recette") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }) },
        containerColor = Color.Black
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC171411)).padding(20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Cadrez toute la feuille, même si elle contient plusieurs recettes", color = Color.White)
                Button(enabled = !busy, onClick = { capture(context, imageCapture, viewModel) }, modifier = Modifier.padding(top = 12.dp)) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Default.Camera, "Capturer")
                    Text(if (busy) " Analyse…" else " Capturer")
                }
            }
        }
    }
}

private fun capture(context: Context, imageCapture: ImageCapture, viewModel: RecipeViewModel) {
    val file = File.createTempFile("recipe-scan-", ".jpg", context.cacheDir)
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { viewModel.processScan(file) }
        override fun onError(exception: ImageCaptureException) { file.delete() }
    })
}
