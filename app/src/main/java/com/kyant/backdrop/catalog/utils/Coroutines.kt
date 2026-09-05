package com.kyant.backdrop.catalog.utils

import kotlinx.coroutines.android.awaitFrame as awaitAndroidFrame

// Android single-platform adapter for the reference project's expect/actual function.
suspend fun awaitFrame() {
    awaitAndroidFrame()
}
