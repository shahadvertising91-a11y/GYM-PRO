package com.forge.bar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import android.content.Intent

/**
 * JS name: "ForgeBar"
 *   start({goal, kps}) -> starts the health foreground service (asks for permissions first)
 *   update({goal, kps}) -> updates goal / kcal-per-step; the notification refreshes immediately
 *   stop()             -> stops the service
 *   getSteps()         -> { steps, day, running }
 * events: "steps" {steps, day}   "state" {running}
 */
@CapacitorPlugin(
    name = "ForgeBar",
    permissions = [
        Permission(strings = [Manifest.permission.ACTIVITY_RECOGNITION], alias = "activity"),
        Permission(strings = [Manifest.permission.POST_NOTIFICATIONS], alias = "notifications")
    ]
)
class ForgeBarPlugin : Plugin() {

    companion object {
        @Volatile private var instance: ForgeBarPlugin? = null
        fun emitSteps(steps: Long, day: String) { instance?.emit("steps", JSObject().put("steps", steps).put("day", day)) }
        fun emitState(running: Boolean) { instance?.emit("state", JSObject().put("running", running)) }
    }

    private fun emit(name: String, data: JSObject) { notifyListeners(name, data) }

    override fun load() { instance = this }
    override fun handleOnDestroy() { if (instance === this) instance = null }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun missing(): Array<String> {
        val m = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACTIVITY_RECOGNITION)) m.add("activity")
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) m.add("notifications")
        return m.toTypedArray()
    }

    @PluginMethod
    fun start(call: PluginCall) {
        val m = missing()
        if (m.isNotEmpty()) requestPermissionForAliases(m, call, "permsDone") else begin(call)
    }

    @PermissionCallback
    private fun permsDone(call: PluginCall) {
        // Notifications may be denied (the service still counts), but activity recognition is mandatory
        if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
            call.reject("Activity recognition permission denied"); return
        }
        begin(call)
    }

    private fun begin(call: PluginCall) {
        val store = StepStore(context)
        call.getInt("goal")?.let { store.goal = it }
        call.getFloat("kps")?.let { store.kps = it }
        val i = Intent(context, StepService::class.java)
            .putExtra(StepService.EXTRA_GOAL, store.goal).putExtra(StepService.EXTRA_KPS, store.kps)
        try {
            ContextCompat.startForegroundService(context, i)
            call.resolve(JSObject().put("running", true))
        } catch (e: Exception) {
            call.reject("Could not start step service: ${e.message}")
        }
    }

    @PluginMethod
    fun update(call: PluginCall) {
        val store = StepStore(context)
        call.getInt("goal")?.let { store.goal = it }
        call.getFloat("kps")?.let { store.kps = it }
        StepService.instance?.refresh()      // notification redraws immediately
        call.resolve()
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        val svc = StepService.instance
        if (svc != null) svc.stopFromApp()
        else { StepStore(context).running = false; context.stopService(Intent(context, StepService::class.java)) }
        call.resolve()
    }

    @PluginMethod
    fun getSteps(call: PluginCall) {
        val s = StepStore(context)
        call.resolve(JSObject().put("steps", s.todaySteps()).put("day", s.today()).put("running", s.running))
    }
}
