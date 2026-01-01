package com.aqtscore

import android.content.Context
import org.opencv.android.OpenCVLoader

object OpenCVHelper {
    private var isInitialized = false

    fun initialize(context: Context): Boolean {
        if (!isInitialized) {
            isInitialized = OpenCVLoader.initLocal()
        }
        return isInitialized
    }

    fun isReady(): Boolean = isInitialized
}
