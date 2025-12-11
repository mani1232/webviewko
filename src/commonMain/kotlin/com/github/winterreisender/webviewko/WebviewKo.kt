@file:OptIn(ExperimentalForeignApi::class)

package com.github.winterreisender.webviewko

import com.github.winterreisender.cwebview.*
import kotlinx.cinterop.*
import kotlin.concurrent.AtomicReference

private typealias BindContext = Pair<WebviewKo, WebviewKo.(String?) -> Pair<String, Int>?>

/**
 * The Kotlin/Native binding to webview
 *
 * @param debug enable debug mode for webview (inspector tools)
 * @param window window handle (NSWindow*, GtkWindow*, etc.) to embed webview into. Pass null for default window.
 */
class WebviewKo(debug: Boolean = true, window: CPointer<*>? = null) : AutoCloseable {

    private val w: webview_t = webview_create(if (debug) 1 else 0, window)
        ?: throw RuntimeException("Failed to create webview instance")

    private val keepAliveRefs = AtomicReference<List<StableRef<Any>>>(emptyList())

    private fun keepAlive(s: StableRef<Any>) {
        while (true) {
            val current = keepAliveRefs.value
            val new = current + s
            if (keepAliveRefs.compareAndSet(current, new)) {
                break
            }
        }
    }

    override fun close() {
        destroy()
    }

    /**
     * Updates the title of the native window.
     * Must be called from the UI thread.
     */
    fun title(v: String) = webview_set_title(w, v)

    /**
     * Navigates webview to the given URL.
     */
    fun url(v: String) = navigate(v)

    /**
     * Navigates webview to the given URL.
     */
    fun navigate(url: String) = webview_navigate(w, url)

    /**
     * Set webview HTML directly.
     */
    fun html(v: String) = webview_set_html(w, v)

    enum class WindowHint(val v: webview_hint_t) {
        None(webview_hint_t.WEBVIEW_HINT_NONE),
        Min(webview_hint_t.WEBVIEW_HINT_MIN),
        Max(webview_hint_t.WEBVIEW_HINT_MAX),
        Fixed(webview_hint_t.WEBVIEW_HINT_FIXED)
    }

    /**
     * Updates the size of the native window.
     */
    fun size(width: Int, height: Int, hints: WindowHint) =
        webview_set_size(w, width, height, hints.v)

    /**
     * Injects JS code at the initialization of the new page.
     * Executed before window.onload.
     */
    fun init(js: String) = webview_init(w, js)

    /**
     * Evaluates arbitrary JS code asynchronously.
     */
    fun eval(js: String) = webview_eval(w, js)

    /**
     * Binds a Kotlin function callback to a global JS function.
     *
     * @param name the name of the global JS function
     * @param fn callback: (Request String) -> Pair(Response String, Status Code 0=OK 1=Error)
     */
    fun bindRaw(name: String, fn: WebviewKo.(String?) -> Pair<String, Int>?) {
        val ctx = StableRef.create(BindContext(this, fn))
        keepAlive(ctx)

        webview_bind(
            w, name,
            staticCFunction { seq, req, arg ->
                val ref = arg!!.asStableRef<BindContext>()
                val (webviewKo, callback) = ref.get()

                val result = callback(webviewKo, req?.toKString())

                val (response, status) = result ?: Pair("", 0)

                webview_return(webviewKo.w, seq?.toKString(), status, response)
            },
            ctx.asCPointer()
        )
    }

    class JSRejectException(reason: String?, json: String?) :
        Throwable(json ?: """ "$reason" """)

    /**
     * Simplified bind function.
     * Throws JSRejectException to reject the JS Promise.
     */
    fun bind(name: String, fn: WebviewKo.(String) -> String) {
        bindRaw(name) { request ->
            runCatching { fn(request ?: "") }.fold(
                onSuccess = { Pair(it, 0) },
                onFailure = {
                    when (it) {
                        is JSRejectException -> Pair(""" "${it.message}" """, 1)
                        else -> Pair(""" "Kotlin Exception: ${it.message}" """, 1)
                    }
                }
            )
        }
    }

    /**
     * Removes a callback.
     * Note: This strictly removes the C binding. The Kotlin StableRef
     * implies a small leak until destroy() is called, which is usually acceptable for bindings.
     */
    fun unbind(name: String) = webview_unbind(w, name)

    /**
     * Posts a function to be executed on the main thread.
     * SAFE: Does not cause memory leaks for recurring events.
     */
    fun dispatch(fn: WebviewKo.() -> Unit) {
        val ctx = StableRef.create(Pair(this, fn))

        webview_dispatch(
            w,
            staticCFunction { _, arg ->
                val ref = arg!!.asStableRef<Pair<WebviewKo, WebviewKo.() -> Unit>>()
                val (instance, action) = ref.get()

                try {
                    instance.action()
                } catch (e: Throwable) {
                    e.printStackTrace()
                } finally {
                    ref.dispose()
                }
            },
            ctx.asCPointer()
        )
    }

    /**
     * Runs the main loop. Blocks until terminated.
     */
    fun start() = webview_run(w)

    /**
     * Stops the main loop. Safe to call from background threads.
     */
    fun terminate() = webview_terminate(w)

    /**
     * Destroy the webview and close the native window.
     */
    fun destroy() {
        keepAliveRefs.getAndSet(emptyList()).forEach {
            try { it.dispose() } catch (_: Throwable) {}
        }
        webview_destroy(w)
    }

    /**
     * Runs the loop and destroys afterwards.
     */
    fun show() {
        webview_run(w)
        destroy()
    }

    fun getWebviewPointer() = w
}