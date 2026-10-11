package com.note88.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var pendingText: String = ""

    companion object {
        const val REQ_EXPORT = 2001
        const val REQ_PDF    = 2002
        const val REQ_PHOTO  = 2003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(Bridge(), "Android")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            REQ_EXPORT -> {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(pendingText.toByteArray(Charsets.UTF_8))
                    }
                    toast("Saved")
                } catch (e: Exception) { e.printStackTrace() }
            }
            REQ_PDF -> thread {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: return@thread
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    runOnUiThread { js("window.onPdfLoaded('$b64')") }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread { js("window.onPdfError()") }
                }
            }
            REQ_PHOTO -> thread {
                try {
                    val input = contentResolver.openInputStream(uri) ?: return@thread
                    val original = BitmapFactory.decodeStream(input)
                    input.close()
                    if (original == null) return@thread
                    val maxDim = 1400
                    val scale = minOf(
                        maxDim.toFloat() / original.width,
                        maxDim.toFloat() / original.height,
                        1f
                    )
                    val scaled = Bitmap.createScaledBitmap(
                        original,
                        (original.width * scale).toInt(),
                        (original.height * scale).toInt(),
                        true
                    )
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    runOnUiThread { js("window.onPhotoPicked('$b64')") }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun js(code: String) { webView.evaluateJavascript(code, null) }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    inner class Bridge {
        @JavascriptInterface
        fun exportTxt(text: String, filename: String) {
            pendingText = text
            runOnUiThread {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, filename)
                }
                startActivityForResult(intent, REQ_EXPORT)
            }
        }

        @JavascriptInterface
        fun pickPdf() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                }
                startActivityForResult(intent, REQ_PDF)
            }
        }

        @JavascriptInterface
        fun pickPhoto() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, REQ_PHOTO)
            }
        }

        @JavascriptInterface
        fun toast(msg: String) { toast(msg) }
    }
}
