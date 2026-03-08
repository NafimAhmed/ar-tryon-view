

package com.nafim.ar_tryon_view

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FaceTracker(
    context: Context,
    modelPath: String = "face_landmarker.task",
    private val onResult: (FaceLandmarkerResult) -> Unit,
    private val onError: (RuntimeException) -> Unit,
) {
    private val faceLandmarker: FaceLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelPath)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener { result, _ ->
                onResult(result)
            }
            .setErrorListener { error ->
                onError(error)
            }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    fun detectAsync(image: MPImage, timestampMs: Long) {
        faceLandmarker.detectAsync(image, timestampMs)
    }

    fun close() {
        faceLandmarker.close()
    }
}