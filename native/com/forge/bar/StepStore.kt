package com.forge.bar

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the hardware step-counter baseline so "steps today" survives process death,
 * reboots and midnight.  TYPE_STEP_COUNTER is cumulative since boot, so:
 *   steps today = carry + (counter - base)
 *   - new day      -> base = last counter value seen (the "midnight baseline"), carry = 0
 *   - reboot       -> counter drops below last value: carry += (last - base), base = 0
 */
class StepStore(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("forge_bar", Context.MODE_PRIVATE)

    var goal: Int
        get() = p.getInt("goal", 8000)
        set(v) { p.edit().putInt("goal", v.coerceIn(1000, 100000)).apply() }

    /** kilocalories per step (computed in JS from height/weight so the service never needs them) */
    var kps: Float
        get() = p.getFloat("kps", 0.04f)
        set(v) { p.edit().putFloat("kps", v.coerceIn(0.005f, 0.2f)).apply() }

    var running: Boolean
        get() = p.getBoolean("running", false)
        set(v) { p.edit().putBoolean("running", v).apply() }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Called for every sensor event. Returns today's step count. */
    @Synchronized
    fun onCounter(value: Long): Long {
        val today = today()
        var day = p.getString("day", null)
        var base = p.getLong("base", -1L)
        var last = p.getLong("last", -1L)
        var carry = p.getLong("carry", 0L)

        if (day != today) {                       // first event of a new day (or first run ever)
            day = today
            carry = 0L
            base = if (last in 0..value) last else value
        } else if (base < 0L) {
            base = value
        }
        if (last >= 0L && value < last) {         // device rebooted: the hardware counter restarted at 0
            carry += maxOf(0L, last - base)
            base = 0L
        }
        last = value
        p.edit().putString("day", day).putLong("base", base).putLong("last", last).putLong("carry", carry).apply()
        return carry + maxOf(0L, value - base)
    }

    /** Midnight rollover without a sensor event (user is standing still at 00:00). */
    @Synchronized
    fun rollover() {
        val today = today()
        if (p.getString("day", null) == today) return
        val last = p.getLong("last", -1L)
        p.edit().putString("day", today).putLong("base", last).putLong("carry", 0L).apply()
    }

    @Synchronized
    fun todaySteps(): Long {
        if (p.getString("day", null) != today()) return 0L
        val base = p.getLong("base", -1L); val last = p.getLong("last", -1L)
        if (base < 0L || last < 0L) return 0L
        return p.getLong("carry", 0L) + maxOf(0L, last - base)
    }
}
