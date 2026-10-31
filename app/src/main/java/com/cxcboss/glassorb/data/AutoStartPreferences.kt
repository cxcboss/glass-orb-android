package com.cxcboss.glassorb.data

import android.content.Context

internal object AutoStartPreferences {
    private const val FILE_NAME = "runtime_preferences"
    private const val KEY_AUTO_START = "auto_start_enabled"

    fun isEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_AUTO_START, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_START, enabled)
            .apply()
    }
}
