package dev.janken.prototype.camera

import com.google.mediapipe.tasks.components.processors.ClassifierOptions

internal const val JANKEN_GESTURE_ASSET = "janken_gesture_recognizer.task"

internal fun jankenCannedClassifierOptions(): ClassifierOptions = ClassifierOptions.builder()
    .setCategoryDenylist(listOf("None", "Closed_Fist", "Open_Palm", "Pointing_Up",
        "Thumb_Down", "Thumb_Up", "Victory", "ILoveYou"))
    .build()
