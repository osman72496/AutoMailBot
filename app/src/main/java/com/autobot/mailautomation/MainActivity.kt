package com.autobot.mailautomation

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        val btnOpenAccessibility = findViewById<Button>(R.id.btnOpenAccessibility)
        val btnTestEmail = findViewById<Button>(R.id.btnTestEmail)

        btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            appendLog("[+] Opened Accessibility Settings. Turn on 'Auto Mail Bot'.")
        }

        btnTestEmail.setOnClickListener {
            appendLog("[~] Testing Mail.tm API connection...")
            CoroutineScope(Dispatchers.IO).launch {
                val acc = MailManager.createAccount()
                withContext(Dispatchers.Main) {
                    if (acc != null) {
                        appendLog("[✓] Success! Generated: ${acc.email}")
                    } else {
                        appendLog("[!] Failed to connect or generate email.")
                    }
                }
            }
        }
    }

    private fun appendLog(msg: String) {
        val current = tvLog.text.toString()
        tvLog.text = "$current\n$msg"
    }
}
