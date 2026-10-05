package com.autobot.mailautomation

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random

object MailManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var cachedDomain: String? = null

    private fun randomUsername(): String {
        val firstNames = listOf("alex", "david", "sara", "ryan", "emily", "james", "lisa", "john", "anna", "mark")
        val lastNames = listOf("smith", "jones", "brown", "davis", "miller", "wilson", "moore", "taylor")
        val num = Random.nextInt(100, 9999)
        return "${firstNames.random()}.${lastNames.random()}$num"
    }

    private fun getDomain(): String? {
        if (!cachedDomain.isNullOrEmpty()) return cachedDomain
        return try {
            val req = Request.Builder().url("https://api.mail.tm/domains").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val members = json.getJSONArray("hydra:member")
                if (members.length() > 0) {
                    cachedDomain = members.getJSONObject(0).getString("domain")
                    cachedDomain
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    data class AccountResult(val email: String, val token: String)

    fun createAccount(): AccountResult? {
        val domain = getDomain() ?: return null
        val username = randomUsername()
        val email = "$username@$domain"
        val password = "Password@12345!"

        val jsonBody = JSONObject().apply {
            put("address", email)
            put("password", password)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()

        try {
            val createReq = Request.Builder()
                .url("https://api.mail.tm/accounts")
                .post(jsonBody.toRequestBody(mediaType))
                .build()
            val createResp = client.newCall(createReq).execute()
            if (createResp.code != 201) return null

            val tokenReq = Request.Builder()
                .url("https://api.mail.tm/token")
                .post(jsonBody.toRequestBody(mediaType))
                .build()
            val tokenResp = client.newCall(tokenReq).execute()
            if (tokenResp.isSuccessful) {
                val tokenBody = tokenResp.body?.string() ?: ""
                val token = JSONObject(tokenBody).getString("token")
                return AccountResult(email, token)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun pollForOtp(token: String, timeoutSec: Int = 60): String? {
        val otpPattern = Pattern.compile("\\b\\d{6}\\b")
        val startTime = System.currentTimeMillis()
        val timeoutMs = timeoutSec * 1000L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val req = Request.Builder()
                    .url("https://api.mail.tm/messages")
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val messages = json.getJSONArray("hydra:member")
                    if (messages.length() > 0) {
                        val firstMsg = messages.getJSONObject(0)
                        val preview = (firstMsg.optString("intro", "") + " " + firstMsg.optString("subject", ""))
                        val matcher = otpPattern.matcher(preview)
                        if (matcher.find()) {
                            return matcher.group(0)
                        }

                        val msgId = firstMsg.getString("id")
                        val detailReq = Request.Builder()
                            .url("https://api.mail.tm/messages/$msgId")
                            .addHeader("Authorization", "Bearer $token")
                            .build()
                        val detailResp = client.newCall(detailReq).execute()
                        if (detailResp.isSuccessful) {
                            val detailBody = detailResp.body?.string() ?: ""
                            val detailMatcher = otpPattern.matcher(detailBody)
                            if (detailMatcher.find()) {
                                return detailMatcher.group(0)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try { Thread.sleep(600) } catch (_: Exception) {}
        }
        return null
    }
}
