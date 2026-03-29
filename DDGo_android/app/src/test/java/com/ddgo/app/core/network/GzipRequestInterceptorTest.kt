package com.ddgo.app.core.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GzipRequestInterceptorTest {

    private val interceptor = GzipRequestInterceptor()

    @Test
    fun `gzip interceptor compresses request body for ai server calls`() {
        val request = Request.Builder()
            .url("https://example.com/api/v1/mujoco-complete/analyze/fast")
            .post(JSON_BODY.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val chain = CapturingChain(request)

        interceptor.intercept(chain)

        val compressedRequest = requireNotNull(chain.proceededRequest)
        assertEquals("gzip", compressedRequest.header("Content-Encoding"))
        assertNull(compressedRequest.header("Content-Length"))
        assertEquals(JSON_MEDIA_TYPE, compressedRequest.body?.contentType())
        assertEquals(JSON_BODY, compressedRequest.body.decompressUtf8())
    }

    @Test
    fun `gzip interceptor leaves pre encoded requests unchanged`() {
        val request = Request.Builder()
            .url("https://example.com/api/v1/mujoco-complete/analyze/fast")
            .header("Content-Encoding", "gzip")
            .post(JSON_BODY.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val chain = CapturingChain(request)

        interceptor.intercept(chain)

        assertTrue(chain.proceededRequest === request)
    }

    private fun okhttp3.RequestBody?.decompressUtf8(): String {
        val body = requireNotNull(this)
        val buffer = Buffer()
        body.writeTo(buffer)
        GZIPInputStream(buffer.inputStream()).bufferedReader(Charsets.UTF_8).use { reader ->
            return reader.readText()
        }
    }

    private class CapturingChain(
        private val incomingRequest: Request
    ) : Interceptor.Chain {
        var proceededRequest: Request? = null

        override fun request(): Request = incomingRequest

        override fun proceed(request: Request): Response {
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody(JSON_MEDIA_TYPE))
                .build()
        }

        override fun connection(): Connection? = null

        override fun call(): Call = FakeCall(incomingRequest)

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private class FakeCall(
        private val request: Request
    ) : Call {
        override fun request(): Request = request

        override fun execute(): Response {
            throw IOException("Not implemented for unit test.")
        }

        override fun enqueue(responseCallback: Callback) {
            error("Not implemented for unit test.")
        }

        override fun cancel() = Unit

        override fun isExecuted(): Boolean = false

        override fun isCanceled(): Boolean = false

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = FakeCall(request)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val JSON_BODY = """{"frames":[1,2,3],"meta":"ddgo"}"""
    }
}
