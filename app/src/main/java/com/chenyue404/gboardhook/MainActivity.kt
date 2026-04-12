package com.chenyue404.gboardhook

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sw0 = findViewById<Switch>(R.id.sw0)
        val swLog = findViewById<Switch>(R.id.swLog)
        val bt0 = findViewById<Button>(R.id.bt0)
        val spCapacity = findViewById<Spinner>(R.id.spCapacity)
        val spExpire = findViewById<Spinner>(R.id.spExpire)

        val capacityLabels = resources.getStringArray(R.array.clipboard_capacity_labels)
        val capacityValues = resources.getStringArray(R.array.clipboard_capacity_values)
        val expireLabels = resources.getStringArray(R.array.clipboard_expire_labels)
        val expireValues = resources.getStringArray(R.array.clipboard_expire_values)

        spCapacity.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            capacityLabels
        )

        spExpire.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            expireLabels
        )

        val pref: SharedPreferences? = try {
            getSharedPreferences(PluginEntry.SP_FILE_NAME, MODE_PRIVATE)
        } catch (e: SecurityException) {
            Log.d("MainActivity", "getSharedPreferences failed --- $e")
            Toast.makeText(this, "Failed to read configuration", Toast.LENGTH_SHORT).show()
            null
        }

        pref?.getString(PluginEntry.SP_KEY, null)?.split(",")?.let { list ->
            val savedCapacity = list.getOrNull(0) ?: PluginEntry.DEFAULT_NUM.toString()
            val savedExpire = list.getOrNull(1) ?: PluginEntry.DEFAULT_TIME.toString()
            val switchOn = list.getOrNull(2)?.equals("true", true) ?: false

            val capacityIndex = capacityValues.indexOf(savedCapacity).takeIf { it >= 0 } ?: 0
            val expireIndex = expireValues.indexOf(savedExpire).takeIf { it >= 0 } ?: 0

            spCapacity.setSelection(capacityIndex)
            spExpire.setSelection(expireIndex)
            sw0.isChecked = switchOn
        }

        swLog.isChecked = pref?.getBoolean(PluginEntry.SP_KEY_LOG, false) ?: false

        bt0.setOnClickListener {
            val selectedCapacity = capacityValues[spCapacity.selectedItemPosition]
            val selectedExpire = expireValues[spExpire.selectedItemPosition]
            val switchOn = sw0.isChecked.toString()

            pref?.edit()?.apply {
                putString(PluginEntry.SP_KEY, "$selectedCapacity,$selectedExpire,$switchOn")
                putBoolean(PluginEntry.SP_KEY_LOG, swLog.isChecked)
                apply()
            }

            Toast.makeText(this, "Pengaturan disimpan", Toast.LENGTH_SHORT).show()

            startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .apply {
                        addCategory(Intent.CATEGORY_DEFAULT)
                        data = "package:${PluginEntry.PACKAGE_NAME}".toUri()
                    }
            )
        }

        findViewById<TextView>(R.id.tvHint).setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/chenyue404/GboardHook".toUri()
                )
            )
        }
    }
}
