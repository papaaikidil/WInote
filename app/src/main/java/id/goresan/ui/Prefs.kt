package id.goresan.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Pengaturan yang diingat antarsesi. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("goresan", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = mutableStateOf(sp.getBoolean(key, def))

    // Seperti Procreate, jari ikut menggambar. Begitu pena pertama kali dipakai, jari otomatis
    // beralih ke menggeser kanvas (kecuali pengguna sudah memilih sendiri di Preferensi).
    private val _fingerDraws = bool("fingerDraws2", true)
    var fingerDraws: Boolean
        get() = _fingerDraws.value
        set(v) {
            _fingerDraws.value = v
            sp.edit().putBoolean("fingerDraws2", v).putBoolean("fingerAuto", false).apply()
        }

    /** Pena terdeteksi: matikan gambar-dengan-jari sekali saja. Mengembalikan true bila berubah. */
    fun autoDisableFinger(): Boolean {
        if (!sp.getBoolean("fingerAuto", true) || !_fingerDraws.value) return false
        _fingerDraws.value = false
        sp.edit().putBoolean("fingerDraws2", false).putBoolean("fingerAuto", false).apply()
        return true
    }

    private val _penButtonErase = bool("penButtonErase", true)
    var penButtonErase: Boolean
        get() = _penButtonErase.value
        set(v) { _penButtonErase.value = v; sp.edit().putBoolean("penButtonErase", v).apply() }

    private val _quickShape = bool("quickShape", true)
    /** Bentuk otomatis: tahan pena di ujung goresan untuk merapikan garis/lingkaran/kotak/segitiga. */
    var quickShape: Boolean
        get() = _quickShape.value
        set(v) { _quickShape.value = v; sp.edit().putBoolean("quickShape", v).apply() }

    private val _quickShapeHold = bool("quickShapeHold", true)
    /** Pena ditahan sekitar 1 detik lagi sesudah bentuk muncul = bentuk presisi (pengganti jari kedua). */
    var quickShapeHold: Boolean
        get() = _quickShapeHold.value
        set(v) { _quickShapeHold.value = v; sp.edit().putBoolean("quickShapeHold", v).apply() }

    private val _quickShapeErase = bool("quickShapeErase", true)
    /** Bentuk otomatis juga untuk penghapus (bisa dimatikan bila penghapusan terasa berhenti saat pena diam). */
    var quickShapeErase: Boolean
        get() = _quickShapeErase.value
        set(v) { _quickShapeErase.value = v; sp.edit().putBoolean("quickShapeErase", v).apply() }

    // Lingkaran kuas saat pena melayang: mati bawaan (bisa dinyalakan lagi di Aksi).
    private val _showCursor = bool("showCursor", false)
    var showCursor: Boolean
        get() = _showCursor.value
        set(v) { _showCursor.value = v; sp.edit().putBoolean("showCursor", v).apply() }

    private val _prediction = bool("prediction", true)
    var prediction: Boolean
        get() = _prediction.value
        set(v) { _prediction.value = v; sp.edit().putBoolean("prediction", v).apply() }

    private val _rotateCanvas = bool("rotateCanvas", true)
    var rotateCanvas: Boolean
        get() = _rotateCanvas.value
        set(v) { _rotateCanvas.value = v; sp.edit().putBoolean("rotateCanvas", v).apply() }

    private val _scrubClear = bool("scrubClear", true)
    /** Gosok tiga jari (kiri-kanan berulang) di kanvas = bersihkan layer, seperti Procreate. */
    var scrubClear: Boolean
        get() = _scrubClear.value
        set(v) { _scrubClear.value = v; sp.edit().putBoolean("scrubClear", v).apply() }

    private val _swipe3Menu = bool("swipe3Menu", true)
    /** Geser tiga jari ke bawah = menu Salin & Tempel. */
    var swipe3Menu: Boolean
        get() = _swipe3Menu.value
        set(v) { _swipe3Menu.value = v; sp.edit().putBoolean("swipe3Menu", v).apply() }

    private val _fourFingerQuick = bool("fourFingerQuick", false)
    /** Ketuk empat jari membuka QuickMenu (bawaan: menyembunyikan alat). */
    var fourFingerQuick: Boolean
        get() = _fourFingerQuick.value
        set(v) { _fourFingerQuick.value = v; sp.edit().putBoolean("fourFingerQuick", v).apply() }

    private val _sidebarRight = bool("sidebarRight", false)
    var sidebarRight: Boolean
        get() = _sidebarRight.value
        set(v) { _sidebarRight.value = v; sp.edit().putBoolean("sidebarRight", v).apply() }

    private val _gamma = mutableFloatStateOf(sp.getFloat("pressureGamma", 1f))
    var pressureGamma: Float
        get() = _gamma.floatValue
        set(v) { _gamma.floatValue = v; sp.edit().putFloat("pressureGamma", v).apply() }

    // Tekanan & penghalusan (berlaku untuk semua kuas, seperti Preferensi Procreate).
    private val _smStab = mutableFloatStateOf(sp.getFloat("smoothStab", 0f))
    var smoothStab: Float
        get() = _smStab.floatValue
        set(v) { _smStab.floatValue = v; sp.edit().putFloat("smoothStab", v).apply() }

    private val _smMotion = mutableFloatStateOf(sp.getFloat("smoothMotion", 0f))
    var smoothMotion: Float
        get() = _smMotion.floatValue
        set(v) { _smMotion.floatValue = v; sp.edit().putFloat("smoothMotion", v).apply() }

    private val _smExpr = mutableFloatStateOf(sp.getFloat("smoothExpr", 0f))
    var smoothExpr: Float
        get() = _smExpr.floatValue
        set(v) { _smExpr.floatValue = v; sp.edit().putFloat("smoothExpr", v).apply() }

    var fillThreshold: Float
        get() = sp.getFloat("fillThreshold", 0.4f)
        set(v) { sp.edit().putFloat("fillThreshold", v).apply() }

    fun getString(key: String, def: String): String = sp.getString(key, def) ?: def
    fun putString(key: String, v: String) { sp.edit().putString(key, v).apply() }
    fun getInt(key: String, def: Int) = sp.getInt(key, def)
    fun getBool(key: String, def: Boolean) = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) { sp.edit().putBoolean(key, v).apply() }
    fun putInt(key: String, v: Int) { sp.edit().putInt(key, v).apply() }

    fun getIntList(key: String): List<Int> = runCatching {
        val a = JSONArray(sp.getString(key, "[]"))
        (0 until a.length()).map { a.getInt(it) }
    }.getOrDefault(emptyList())

    fun putIntList(key: String, list: List<Int>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        sp.edit().putString(key, a.toString()).apply()
    }

    fun getFloatMap(key: String): Map<String, Float> = runCatching {
        val o = JSONObject(sp.getString(key, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getDouble(it).toFloat() }
    }.getOrDefault(emptyMap())

    fun putFloatMap(key: String, m: Map<String, Float>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v.toDouble()) }
        sp.edit().putString(key, o.toString()).apply()
    }
}
