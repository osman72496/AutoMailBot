package com.autobot.mailautomation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class AutoBotAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentAccount: MailManager.AccountResult? = null
    private var isProcessingEmail = false
    private var isProcessingOtp = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected!")
        prepareFreshAccount()
    }

    private fun prepareFreshAccount() {
        serviceScope.launch {
            val acc = MailManager.createAccount()
            if (acc != null) {
                currentAccount = acc
                Log.d(TAG, "Account prepared: ${acc.email}")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return

        // 1. Detect Email Registration Screen
        if (!isProcessingEmail) {
            val emailKeywords = listOf("Add Email", "Choose a login email", "Please enter your valid email", "Enter email")
            val hasEmailText = emailKeywords.any { rootNode.findAccessibilityNodeInfosByText(it).isNotEmpty() }

            if (hasEmailText) {
                val editTexts = mutableListOf<AccessibilityNodeInfo>()
                findEditTexts(rootNode, editTexts)
                if (editTexts.isNotEmpty()) {
                    val inputField = editTexts[0]
                    isProcessingEmail = true
                    serviceScope.launch {
                        handleEmailInput(inputField)
                    }
                }
            }
        }

        // 2. Detect OTP Verification Screen
        if (!isProcessingOtp && currentAccount != null) {
            val otpKeywords = listOf("Verification code", "Please check your email", "Enter OTP", "confirmation code")
            val hasOtpText = otpKeywords.any { rootNode.findAccessibilityNodeInfosByText(it).isNotEmpty() }

            if (hasOtpText) {
                val editTexts = mutableListOf<AccessibilityNodeInfo>()
                findEditTexts(rootNode, editTexts)
                if (editTexts.isNotEmpty()) {
                    val otpField = editTexts[0]
                    isProcessingOtp = true
                    serviceScope.launch {
                        handleOtpInput(otpField)
                    }
                }
            }
        }
    }

    private suspend fun handleEmailInput(inputNode: AccessibilityNodeInfo) {
        if (currentAccount == null) {
            currentAccount = MailManager.createAccount()
        }
        val email = currentAccount?.email ?: return

        delay(300)
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, email)
        }
        inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "Typed Email: $email")

        delay(1000)
        isProcessingEmail = false
    }

    private suspend fun handleOtpInput(otpNode: AccessibilityNodeInfo) {
        val token = currentAccount?.token ?: return
        Log.d(TAG, "Polling OTP for token: $token")
        val otp = MailManager.pollForOtp(token, timeoutSec = 45)

        if (otp != null && otp.length == 6) {
            delay(300)
            otpNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, otp)
            }
            otpNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Typed OTP: $otp")

            currentAccount = null
            prepareFreshAccount()
        }
        delay(2000)
        isProcessingOtp = false
    }

    private fun findEditTexts(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findEditTexts(it, list) }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AutoBotService"
    }
}
