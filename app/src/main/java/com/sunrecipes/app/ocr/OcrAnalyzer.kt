package com.sunrecipes.app.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object OcrAnalyzer {
    suspend fun recognize(file: File): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(BitmapFactory.decodeFile(file.absolutePath), 0)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { continuation.resume(it.text) }
            .addOnFailureListener { continuation.resume("") }
    }
}
