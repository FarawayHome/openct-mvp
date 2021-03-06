package cc.metapro.openct.utils.interceptors

/*
 *  Copyright 2016 - 2017 OpenCT open source class table
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.util.regex.Pattern

class SchoolInterceptor(private val mBaseUrl: HttpUrl) : Interceptor {
    private lateinit var mObserver: RedirectObserver<String>
    private var mRefer: HttpUrl? = null

    init {
        mRefer = mBaseUrl
    }

    fun setObserver(observer: RedirectObserver<String>) {
        mObserver = observer
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val realRequest = request.newBuilder()
                .header("User-Agent", userAgent)
                .header("Referer", mRefer!!.toString())
                .build()

        var response = chain.proceed(realRequest)

        if (response.isRedirect) {
            var location: String = response.header("Location")!!
            location = mBaseUrl.newBuilder(location)!!.toString()
            mObserver.onRedirect(location)
        } else {
            val type = response.body()!!.contentType()!!.toString()
            if (type.contains("text/html")) {
                // generate new mRefer accordingly
                mRefer = mRefer!!.newBuilder(request.url().toString())!!.build()
                val responseString = response.body()!!.string()

                // make javascript redirection to http 302 redirection
                var pattern = Pattern.compile(JS_REDIRECT_PATTERN)
                var matcher = pattern.matcher(responseString)
                if (matcher.find()) {
                    var found = matcher.group()
                    pattern = Pattern.compile(URL_PATTERN)
                    matcher = pattern.matcher(found)
                    if (matcher.find()) {
                        found = matcher.group()
                        found = mBaseUrl.newBuilder(found)!!.toString()
                        mObserver.onRedirect(found)

                        response = response.newBuilder()
                                .addHeader("Location", found)
                                .code(302)
                                .body(ResponseBody.create(response.body()!!.contentType(), ""))
                                .build()
                    }
                } else {
                    response = response.newBuilder()
                            .body(ResponseBody.create(response.body()!!.contentType(), responseString))
                            .build()
                }
            }
        }
        return response
    }

    interface RedirectObserver<in T> {
        fun onRedirect(x: T)
    }

    companion object {
        val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"
        private val URL_PATTERN = "((http|ftp|https)://)(([a-zA-Z0-9._-]+\\.[a-zA-Z]{2,6})|([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}))(:[0-9]{1,4})*(/[a-zA-Z0-9&%_./-~-]*)?"
        private val JS_REDIRECT_PATTERN = "(window\\.location).*?$URL_PATTERN"
    }
}
