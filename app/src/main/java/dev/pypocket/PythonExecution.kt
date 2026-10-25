package dev.pypocket

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class InputRequest(
    val prompt: String,
    private val latch: CountDownLatch = CountDownLatch(1),
    private val value: AtomicReference<String?> = AtomicReference(null)
) {
    fun submit(input: String) {
        value.set(input)
        latch.countDown()
    }

    fun await(cancelled: AtomicBoolean): String {
        while (!latch.await(100, TimeUnit.MILLISECONDS)) {
            if (cancelled.get() || Thread.currentThread().isInterrupted) {
                throw InterruptedException("Execution cancelled")
            }
        }
        return value.get().orEmpty()
    }
}

class InputBridge(
    private val cancelled: AtomicBoolean,
    private val onInputRequested: (InputRequest) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun readLine(prompt: String): String {
        if (cancelled.get()) throw InterruptedException("Execution cancelled")
        val request = InputRequest(prompt)
        mainHandler.post { onInputRequested(request) }
        return request.await(cancelled)
    }

    fun isCancelled(): Boolean = cancelled.get()
}

object PythonExecution {
    fun run(context: Context, source: String, bridge: InputBridge): String {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
        return Python.getInstance()
            .getModule("runner")
            .callAttr("run", source, bridge)
            .toString()
    }
}
