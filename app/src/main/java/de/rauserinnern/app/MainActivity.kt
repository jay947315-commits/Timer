package de.rauserinnern.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var root: LinearLayout
    private lateinit var reminderList: LinearLayout
    private lateinit var locationStatus: TextView
    private lateinit var radiusLabel: TextView
    private lateinit var addressInput: EditText
    private lateinit var latitudeInput: EditText
    private lateinit var longitudeInput: EditText
    private lateinit var radiusBar: SeekBar
    private val prefs by lazy { getSharedPreferences("raus", MODE_PRIVATE) }
    private val reminders get() = prefs.getStringSet("items", linkedSetOf("Licht ausschalten", "Fenster schließen", "Haustür abschließen", "Herd kontrollieren", "Schlüssel und Handy mitnehmen"))!!.toMutableSet()
    private val requestFine = 41
    private val requestNotifications = 42
    private val ink = Color.rgb(27, 35, 51)
    private val muted = Color.rgb(104, 115, 133)
    private val accent = Color.rgb(35, 105, 245)
    private val page = Color.rgb(245, 247, 251)
    private var selectedLat = 0.0
    private var selectedLng = 0.0
    private var selectedRadius = 150

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
        selectedLat = prefs.getString("home_lat", "0")?.toDoubleOrNull() ?: 0.0
        selectedLng = prefs.getString("home_lng", "0")?.toDoubleOrNull() ?: 0.0
        selectedRadius = prefs.getInt("radius", 150).coerceIn(100, 1000)
        render()
        askNotificationPermission()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(label: String, primary: Boolean = false, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(if (primary) Color.WHITE else accent)
            if (Build.VERSION.SDK_INT >= 21) backgroundTintList = android.content.res.ColorStateList.valueOf(if (primary) accent else Color.rgb(232, 239, 255))
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setOnClickListener { action() }
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        setBackgroundColor(Color.WHITE)
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    private fun sectionTitle(title: String, subtitle: String? = null): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 19f, ink, true))
            if (subtitle != null) addView(text(subtitle, 13f, muted).apply { setPadding(0, dp(4), 0, 0) })
        }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(24))
            setBackgroundColor(page)
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(18)) }
        header.addView(text("RausErinnern", 29f, ink, true))
        header.addView(text("Geh mit einem guten Gefühl aus dem Haus.", 14f, muted).apply { setPadding(0, dp(5), 0, 0) })
        root.addView(header)

        val hero = card().apply { setBackgroundColor(Color.rgb(232, 240, 255)) }
        hero.addView(text("DEIN ALLTAGS-CHECK", 11f, accent, true))
        hero.addView(text("Alles dabei. Alles aus. Alles sicher.", 19f, ink, true).apply { setPadding(0, dp(7), 0, dp(4)) })
        hero.addView(text("Deine persönliche Liste erscheint als Benachrichtigung, sobald du deinen Zuhause-Bereich verlässt.", 13f, muted))
        root.addView(hero)

        val remindersCard = card()
        remindersCard.addView(sectionTitle("Deine Erinnerungen", "Füge eigene Punkte hinzu und entferne, was du nicht brauchst."))
        val addRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(6)) }
        val input = EditText(this).apply {
            hint = "z. B. Kaffeemaschine ausschalten"
            textSize = 14f
            singleLine = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(210, 218, 230))
        }
        addRow.addView(input, LinearLayout.LayoutParams(0, dp(50), 1f))
        addRow.addView(button("Hinzufügen", true) {
            val value = input.text.toString().trim()
            if (value.isNotEmpty()) {
                val items = reminders
                items.add(value)
                prefs.edit().putStringSet("items", items).apply()
                render()
            }
        })
        remindersCard.addView(addRow)
        reminderList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        reminders.sorted().forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(7), 0, dp(7))
            }
            val dot = text("✓", 15f, accent, true).apply { gravity = Gravity.CENTER; setBackgroundColor(Color.rgb(235, 242, 255)) }
            row.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)))
            row.addView(text(item, 15f, ink).apply { setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply {
                text = "Entfernen"
                textSize = 12f
                setTextColor(Color.rgb(190, 70, 70))
                setPadding(dp(8), dp(8), 0, dp(8))
                setOnClickListener {
                    val items = reminders
                    items.remove(item)
                    prefs.edit().putStringSet("items", items).apply()
                    render()
                }
            })
            reminderList.addView(row)
        }
        remindersCard.addView(reminderList)
        root.addView(remindersCard)

        val locationCard = card()
        locationCard.addView(sectionTitle("Dein Zuhause-Standort", "Du bestimmst selbst, wo dein Zuhause-Bereich liegt."))
        locationStatus = text(locationDescription(), 14f, muted).apply { setPadding(0, dp(12), 0, dp(12)) }
        locationCard.addView(locationStatus)

        locationCard.addView(text("Adresse suchen", 13f, ink, true))
        addressInput = EditText(this).apply {
            hint = "Straße, Hausnummer, Ort"
            textSize = 14f
            singleLine = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        locationCard.addView(addressInput, LinearLayout.LayoutParams(-1, dp(50)))
        locationCard.addView(button("Adresse als Zuhause verwenden") { searchAddress() })

        locationCard.addView(text("Oder Koordinaten direkt eingeben", 13f, ink, true).apply { setPadding(0, dp(14), 0, dp(4)) })
        val coordRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        latitudeInput = EditText(this).apply { hint = "Breitengrad"; textSize = 13f; inputType = 8194; setSingleLine() }
        longitudeInput = EditText(this).apply { hint = "Längengrad"; textSize = 13f; inputType = 8194; setSingleLine() }
        coordRow.addView(latitudeInput, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(8) })
        coordRow.addView(longitudeInput, LinearLayout.LayoutParams(0, dp(50), 1f))
        locationCard.addView(coordRow)
        locationCard.addView(button("Koordinaten übernehmen") { saveCoordinatesFromFields() })

        locationCard.addView(button("Meinen aktuellen GPS-Standort verwenden", true) { useCurrentLocation() }.apply { setPadding(dp(4), dp(5), dp(4), dp(5)) })
        locationCard.addView(text("Erinnerungsradius: ${selectedRadius} m", 14f, ink, true).apply { setPadding(0, dp(18), 0, 0) }.also { radiusLabel = it })
        radiusBar = SeekBar(this).apply {
            max = 900
            progress = selectedRadius - 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedRadius = progress + 100
                    radiusLabel.text = "Erinnerungsradius: ${selectedRadius} m"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    prefs.edit().putInt("radius", selectedRadius).apply()
                    if (hasSavedLocation()) registerGeofence()
                }
            })
        }
        locationCard.addView(radiusBar)
        locationCard.addView(text("100 m", 11f, muted).apply { setPadding(0, 0, 0, dp(8)) })
        locationCard.addView(button("Standort-Erinnerung aktivieren / aktualisieren", true) { activateLocationReminder() })
        locationCard.addView(button("Standort-Erinnerung ausschalten") {
            LocationServices.getGeofencingClient(this).removeGeofences(geofencePendingIntent())
                .addOnSuccessListener { Toast.makeText(this, "Standort-Erinnerung ausgeschaltet.", Toast.LENGTH_SHORT).show() }
                .addOnFailureListener { Toast.makeText(this, "Konnte nicht ausgeschaltet werden.", Toast.LENGTH_SHORT).show() }
        })
        root.addView(locationCard)

        root.addView(text("Tipp: Für zuverlässige Erinnerungen müssen Standortzugriff im Hintergrund und Benachrichtigungen erlaubt sein.", 12f, muted).apply { setPadding(dp(3), dp(2), dp(3), 0) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun hasSavedLocation() = selectedLat != 0.0 || selectedLng != 0.0

    private fun locationDescription(): String =
        if (hasSavedLocation()) "Gespeichert: ${"%.5f".format(Locale.US, selectedLat)}, ${"%.5f".format(Locale.US, selectedLng)}\nRadius: ${selectedRadius} m"
        else "Noch kein Zuhause-Standort gespeichert. Suche eine Adresse, gib Koordinaten ein oder nutze GPS."

    private fun saveLocation(lat: Double, lng: Double, label: String) {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            Toast.makeText(this, "Bitte gültige Koordinaten eingeben.", Toast.LENGTH_LONG).show()
            return
        }
        selectedLat = lat
        selectedLng = lng
        prefs.edit().putString("home_lat", lat.toString()).putString("home_lng", lng.toString()).putInt("radius", selectedRadius).putString("home_label", label).apply()
        hideKeyboard()
        render()
        Toast.makeText(this, "Zuhause-Standort gespeichert. Jetzt Erinnerung aktivieren.", Toast.LENGTH_LONG).show()
    }

    private fun saveCoordinatesFromFields() {
        val lat = latitudeInput.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val lng = longitudeInput.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (lat == null || lng == null) {
            Toast.makeText(this, "Bitte beide Koordinaten korrekt eingeben.", Toast.LENGTH_LONG).show()
            return
        }
        saveLocation(lat, lng, "Manuell festgelegter Standort")
    }

    private fun searchAddress() {
        val query = addressInput.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Bitte zuerst eine Adresse eingeben.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, "Adresssuche ist auf diesem Gerät nicht verfügbar. Bitte Koordinaten verwenden.", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Adresse wird gesucht …", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(this, Locale.getDefault()).getFromLocationName(query, 1)
                runOnUiThread {
                    val result = results?.firstOrNull()
                    if (result == null) Toast.makeText(this, "Adresse nicht gefunden. Prüfe die Schreibweise.", Toast.LENGTH_LONG).show()
                    else saveLocation(result.latitude, result.longitude, query)
                }
            } catch (_: Exception) {
                runOnUiThread { Toast.makeText(this, "Adresssuche fehlgeschlagen. Bitte Koordinaten verwenden.", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun useCurrentLocation() {
        if (!ensureForegroundPermission()) return
        try {
            Toast.makeText(this, "GPS-Standort wird ermittelt …", Toast.LENGTH_SHORT).show()
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    if (loc == null) Toast.makeText(this, "Kein GPS-Standort gefunden. Bitte Standortdienste aktivieren und erneut versuchen.", Toast.LENGTH_LONG).show()
                    else saveLocation(loc.latitude, loc.longitude, "Aktueller GPS-Standort")
                }
                .addOnFailureListener { Toast.makeText(this, "Standort konnte nicht ermittelt werden: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
        } catch (_: SecurityException) {
            Toast.makeText(this, "Standortberechtigung fehlt.", Toast.LENGTH_LONG).show()
        }
    }

    private fun activateLocationReminder() {
        if (!hasSavedLocation()) {
            Toast.makeText(this, "Bitte zuerst deinen Zuhause-Standort festlegen.", Toast.LENGTH_LONG).show()
            return
        }
        if (!ensureForegroundPermission()) return
        if (Build.VERSION.SDK_INT >= 29 && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("Standort im Hintergrund erlauben")
                .setMessage("Damit RausErinnern erkennt, wenn du das Zuhause-Gebiet verlässt, muss der Standortzugriff auf „Immer zulassen“ stehen. Öffne die App-Einstellungen und wähle diese Berechtigung aus.")
                .setNegativeButton("Später", null)
                .setPositiveButton("Einstellungen öffnen") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }.show()
            return
        }
        registerGeofence()
    }

    private fun ensureForegroundPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) return true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), requestFine)
        return false
    }

    private fun registerGeofence() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val geofence = Geofence.Builder()
            .setRequestId("home")
            .setCircularRegion(selectedLat, selectedLng, selectedRadius.toFloat())
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(0)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
        val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofence(geofence).build()
        try {
            LocationServices.getGeofencingClient(this).addGeofences(request, geofencePendingIntent())
                .addOnSuccessListener { Toast.makeText(this, "Standort-Erinnerung aktiv! Radius: ${selectedRadius} m.", Toast.LENGTH_LONG).show() }
                .addOnFailureListener { Toast.makeText(this, "Aktivierung fehlgeschlagen: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
        } catch (_: SecurityException) {
            Toast.makeText(this, "Bitte Standortberechtigungen prüfen.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestFine && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "Standortzugriff erteilt. Tippe erneut auf die gewünschte Standortaktion.", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(this, ExitReceiver::class.java)
        return PendingIntent.getBroadcast(this, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestNotifications)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("leaving", "Beim Verlassen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Erinnert dich an deine Checkliste, wenn du deinen Zuhause-Bereich verlässt."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

class ExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = context.getSharedPreferences("raus", Context.MODE_PRIVATE).getStringSet("items", emptySet()) ?: emptySet()
        if (reminders.isEmpty()) return
        val body = reminders.sorted().joinToString("\n") { "• $it" }
        val notification = NotificationCompat.Builder(context, "leaving")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Bevor du gehst!")
            .setContentText(reminders.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }
}
