package com.example.crashlens

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.os.NetworkOnMainThreadException
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash Reporter: crash hone par report file save karta hai.
 * Apne app mein sirf copy karein aur Application.onCreate() mein
 * CrashReporter.install(this) call karein.
 */
object CrashReporter {

    private const val REPORT_DIR = "crash_reports"

    /** Application.onCreate() mein ek baar call karein. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveReport(appContext, thread, throwable)
            } catch (e: Throwable) {
                // Report save fail ho to bhi crash ko aage badhne dein
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Saved reports, sabse nayi pehle. */
    fun listReports(context: Context): List<File> {
        val dir = File(context.filesDir, REPORT_DIR)
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** Saari purani reports delete karein. */
    fun deleteAll(context: Context) {
        File(context.filesDir, REPORT_DIR).deleteRecursively()
    }

    private fun saveReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, REPORT_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$stamp.txt")
        file.writeText(buildReport(context, thread, throwable))
    }

    fun buildReport(context: Context, thread: Thread, throwable: Throwable): String {
        val pkg = context.packageName
        val time = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(Date())

        val stackText = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

        // Stack mein pehla frame jo aapke apne package ka ho
        val appFrame = throwable.stackTrace.firstOrNull { it.className.startsWith(pkg) }

        val sb = StringBuilder()
        sb.appendLine("===== SUMMARY =====")
        sb.appendLine("Time      : $time")
        sb.appendLine("Exception : ${throwable.javaClass.name}")
        sb.appendLine("Message   : ${throwable.message ?: "(no message)"}")
        sb.appendLine("Reason    : ${explain(throwable)}")
        sb.appendLine()
        if (appFrame != null) {
            sb.appendLine(
                "Aapke code mein: ${appFrame.className}.${appFrame.methodName}" +
                    "(${appFrame.fileName}:${appFrame.lineNumber})"
            )
        } else {
            sb.appendLine("Aapke code mein: (stack mein apna code nahi mila, neeche stack trace dekhein)")
        }
        sb.appendLine()
        sb.appendLine("Device    : ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android   : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("App       : $pkg")
        sb.appendLine("Thread    : ${thread.name}")
        sb.appendLine()
        sb.appendLine("===== STACK TRACE =====")
        sb.append(stackText)
        return sb.toString()
    }

    private fun explain(t: Throwable): String = when (t) {
        is NullPointerException -> "Null (khali) value par kaam kiya gaya. Koi variable ya object null tha."
        is ClassCastException -> "Galat type mein cast kiya gaya."
        is IndexOutOfBoundsException -> "List ya array ki limit se bahar index use hua."
        is IllegalStateException -> "Object galat state mein tha (jaise destroy hone ke baad use)."
        is IllegalArgumentException -> "Function ko galat value di gayi."
        is NumberFormatException -> "String ko number mein badalne mein galti hui."
        is ArithmeticException -> "Zero se divide kiya gaya."
        is NetworkOnMainThreadException -> "Main (UI) thread par network call kiya gaya."
        is ActivityNotFoundException -> "Intent handle karne wali app nahi mili."
        is SecurityException -> "Permission nahi mili."
        is OutOfMemoryError -> "Memory khatam ho gayi (bade bitmap ya memory leak)."
        else -> "Upar Exception name aur Message dekhein."
    }
}
