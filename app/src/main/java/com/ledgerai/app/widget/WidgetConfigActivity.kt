package com.ledgerai.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.runBlocking

/**
 * Minimal home-screen widget configuration (theme + private mode).
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val themeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@WidgetConfigActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("System", "Dark", "Light")
            )
            setSelection(
                when (WidgetPrefs.theme(this@WidgetConfigActivity)) {
                    WidgetPrefs.THEME_DARK -> 1
                    WidgetPrefs.THEME_LIGHT -> 2
                    else -> 0
                }
            )
        }
        val privateBox = CheckBox(this).apply {
            text = "Private mode (hide amounts and titles)"
            isChecked = WidgetPrefs.privateMode(this@WidgetConfigActivity)
        }
        val save = android.widget.Button(this).apply {
            text = "Save"
            setOnClickListener {
                WidgetPrefs.setTheme(
                    this@WidgetConfigActivity,
                    when (themeSpinner.selectedItemPosition) {
                        1 -> WidgetPrefs.THEME_DARK
                        2 -> WidgetPrefs.THEME_LIGHT
                        else -> WidgetPrefs.THEME_SYSTEM
                    }
                )
                WidgetPrefs.setPrivateMode(this@WidgetConfigActivity, privateBox.isChecked)
                runBlocking {
                    HomeWidget().updateAll(this@WidgetConfigActivity)
                    QuickActionsWidget().updateAll(this@WidgetConfigActivity)
                    FocusWidget().updateAll(this@WidgetConfigActivity)
                }
                val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                setResult(RESULT_OK, result)
                finish()
            }
        }
        root.addView(themeSpinner)
        root.addView(privateBox)
        root.addView(save)
        setContentView(root)
    }
}
