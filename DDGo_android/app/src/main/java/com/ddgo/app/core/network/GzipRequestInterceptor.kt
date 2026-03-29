package com.ddgo.app.core.network

import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.GzipSink
import okio.buffer

class GzipRequestInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalBody = originalRequest.body

        if (originalBody == null || originalRequest.header(CONTENT_ENCODING_HEADER) != null) {
            return chain.proceed(originalRequest)
        }

        val compressedRequest = originalRequest.newBuilder()
            .header(CONTENT_ENCODING_HEADER, GZIP_ENCODING)
            .removeHeader(CONTENT_LENGTH_HEADER)
            .method(originalRequest.method, originalBody.gzip())
            .build()

        return chain.proceed(compressedRequest)
    }

    private fun RequestBody.gzip(): RequestBody = object : RequestBody() {
        override fun contentType() = this@gzip.contentType()

        override fun contentLength(): Long = -1L

        override fun isDuplex(): Boolean = this@gzip.isDuplex()

        override fun isOneShot(): Boolean = this@gzip.isOneShot()

        override fun writeTo(sink: BufferedSink) {
            GzipSink(sink).buffer().use { gzipSink ->
                this@gzip.writeTo(gzipSink)
            }
        }
    }

    companion object {
        private const val CONTENT_ENCODING_HEADER = "Content-Encoding"
        private const val CONTENT_LENGTH_HEADER = "Content-Length"
        private const val GZIP_ENCODING = "gzip"
    }
}
