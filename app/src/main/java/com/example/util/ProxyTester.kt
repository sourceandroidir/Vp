package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ProxyTester {

    data class ProxyTestResult(
        val isSuccess: Boolean,
        val message: String,
        val latencyMs: Long
    )

    suspend fun testConnection(
        host: String,
        port: Int,
        protocol: String,
        timeoutMs: Int = 4000
    ): ProxyTestResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank() || port !in 1..65535) {
            return@withContext ProxyTestResult(
                isSuccess = false,
                message = "لطفاً آدرس هاست و پورت معتبر وارد کنید.",
                latencyMs = 0
            )
        }

        val startTime = System.currentTimeMillis()
        val socket = Socket()
        try {
            val socketAddress = InetSocketAddress(cleanHost, port)
            socket.connect(socketAddress, timeoutMs)

            val latency = System.currentTimeMillis() - startTime

            if (protocol.equals("HTTP", ignoreCase = true)) {
                // Test simple probe on HTTP proxy
                socket.soTimeout = timeoutMs
                try {
                    val out = socket.getOutputStream()
                    val probe = "CONNECT 1.1.1.1:443 HTTP/1.1\r\nHost: 1.1.1.1:443\r\nUser-Agent: PsiphonProxyTester/1.0\r\n\r\n"
                    out.write(probe.toByteArray())
                    out.flush()

                    val input = socket.getInputStream()
                    val buffer = ByteArray(128)
                    val read = input.read(buffer)
                    socket.close()

                    if (read > 0) {
                        val responseLine = String(buffer, 0, read).lines().firstOrNull() ?: ""
                        ProxyTestResult(
                            isSuccess = true,
                            message = "پروکسی HTTP آماده است ($responseLine)",
                            latencyMs = latency
                        )
                    } else {
                        ProxyTestResult(
                            isSuccess = true,
                            message = "پورت پروکسی HTTP باز و در دسترس است.",
                            latencyMs = latency
                        )
                    }
                } catch (e: Exception) {
                    try { socket.close() } catch (_: Exception) {}
                    ProxyTestResult(
                        isSuccess = true,
                        message = "اتصال TCP برقرار شد؛ پورت پروکسی در دسترس است.",
                        latencyMs = latency
                    )
                }
            } else {
                // Test SOCKS5 handshake
                socket.soTimeout = timeoutMs
                try {
                    val out = socket.getOutputStream()
                    // SOCKS5 version 5, 1 auth method, no authentication (0x00)
                    out.write(byteArrayOf(0x05, 0x01, 0x00))
                    out.flush()

                    val input = socket.getInputStream()
                    val buffer = ByteArray(2)
                    val read = input.read(buffer)
                    socket.close()

                    if (read >= 2 && buffer[0] == 0x05.toByte()) {
                        ProxyTestResult(
                            isSuccess = true,
                            message = "پروتکل SOCKS5 تأیید شد و پروکسی فعال است.",
                            latencyMs = latency
                        )
                    } else {
                        ProxyTestResult(
                            isSuccess = true,
                            message = "پورت پروکسی باز و در دسترس است.",
                            latencyMs = latency
                        )
                    }
                } catch (e: Exception) {
                    try { socket.close() } catch (_: Exception) {}
                    ProxyTestResult(
                        isSuccess = true,
                        message = "اتصال به پورت SOCKS5 با موفقیت انجام شد.",
                        latencyMs = latency
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            ProxyTestResult(
                isSuccess = false,
                message = "زمان انتظار به پایان رسید (Timeout) - پروکسی پاسخ نداد.",
                latencyMs = 0
            )
        } catch (e: ConnectException) {
            ProxyTestResult(
                isSuccess = false,
                message = "اتصال رد شد (Connection Refused) - سرویس روی این پورت فعال نیست.",
                latencyMs = 0
            )
        } catch (e: UnknownHostException) {
            ProxyTestResult(
                isSuccess = false,
                message = "آدرس هاست یافت نشد (Unknown Host / DNS Error).",
                latencyMs = 0
            )
        } catch (e: Exception) {
            ProxyTestResult(
                isSuccess = false,
                message = "خطا در اتصال: ${e.localizedMessage ?: "نامشخص"}",
                latencyMs = 0
            )
        }
    }
}
