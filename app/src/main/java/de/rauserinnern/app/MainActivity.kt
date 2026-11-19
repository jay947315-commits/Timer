package de.rauserinnern.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var list: LinearLayout
    private val prefs by lazy { getSharedPreferences("raus", MODE_PRIVATE) }
    private val reminders get() = prefs.getStringSet("items", setOf("Licht ausschalten", "Fenster schließen", "Haustür abschließen", "Herd kontrollieren", "Schlüssel und Handy mitnehmen"))!!.toMutableSet()
    private val request = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
        render()
        askNotificationPermission()
    }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = 1; setPadding(28, 36, 28, 24); setBackgroundColor(Color.rgb(248,249,255)) }
        root.addView(TextView(this).apply { text = "RausErinnern"; textSize = 30f; setTextColor(Color.rgb(28,35,50)); setPadding(0,0,0,8) })
        root.addView(TextView(this).apply { text = "Nichts vergessen, wenn du gehst."; textSize = 16f; setTextColor(Color.DKGRAY); setPadding(0,0,0,24) })
        val input = EditText(this).apply { hint = "Neue Erinnerung hinzufügen"; textSize = 16f }
        root.addView(input)
        root.addView(Button(this).apply { text = "＋ Erinnerung hinzufügen"; setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isNotEmpty()) { val s = reminders; s.add(value); prefs.edit().putStringSet("items", s).apply(); input.text.clear(); render() }
        }})
        root.addView(TextView(this).apply { text = "DEINE CHECKLISTE"; textSize = 13f; setTextColor(Color.GRAY); setPadding(0,24,0,8) })
        list = LinearLayout(this).apply { orientation = 1 }
        reminders.forEach { item ->
            val row = LinearLayout(this).apply { orientation = 0; gravity = Gravity.CENTER_VERTICAL; setPadding(8,5,0,5) }
            row.addView(TextView(this).apply { text = "☐  $item"; textSize = 17f; setTextColor(Color.rgb(35,42,58)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            row.addView(Button(this).apply { text = "×"; setOnClickListener { val s = reminders; s.remove(item); prefs.edit().putStringSet("items",s).apply(); render() } })
            list.addView(row)
        }
        root.addView(list)
        root.addView(TextView(this).apply { text = "STANDORT-ERINNERUNG"; textSize = 13f; setTextColor(Color.GRAY); setPadding(0,24,0,4) })
        root.addView(TextView(this).apply { text = "Lege deinen aktuellen Standort als Zuhause fest. Beim Verlassen des Bereichs erhältst du deine Erinnerung als Nachricht."; textSize = 14f; setTextColor(Color.DKGRAY) })
        root.addView(Button(this).apply { text = "Zuhause hier festlegen"; setOnClickListener { setHome() } })
        root.addView(Button(this).apply { text = "Standort-Erinnerung deaktivieren"; setOnClickListener { LocationServices.getGeofencingClient(this@MainActivity).removeGeofences(geofencePendingIntent()); Toast.makeText(this@MainActivity,"Standort-Erinnerung deaktiviert",Toast.LENGTH_SHORT).show() } })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun setHome() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION), request); return
        }
        if (Build.VERSION.SDK_INT >= 29 && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), request); return
        }
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc == null) { Toast.makeText(this,"Standort gerade nicht verfügbar. Bitte GPS aktivieren und erneut versuchen.",Toast.LENGTH_LONG).show(); return@addOnSuccessListener }
            val geofence = Geofence.Builder().setRequestId("home").setCircularRegion(loc.latitude,loc.longitude,150f)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT).setLoiteringDelay(0)
                .setExpirationDuration(Geofence.NEVER_EXPIRE).build()
            val req = GeofencingRequest.Builder().setInitialTrigger(0).addGeofence(geofence).build()
            try {
                LocationServices.getGeofencingClient(this).addGeofences(req,geofencePendingIntent())
                    .addOnSuccessListener { Toast.makeText(this,"Zuhause gespeichert. Erinnerung beim Verlassen aktiv.",Toast.LENGTH_LONG).show() }
                    .addOnFailureListener { Toast.makeText(this,"Konnte Standort nicht aktivieren: ${it.localizedMessage}",Toast.LENGTH_LONG).show() }
            } catch (_: SecurityException) { Toast.makeText(this,"Bitte Standortberechtigungen erlauben.",Toast.LENGTH_LONG).show() }
        }
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(this, ExitReceiver::class.java)
        return PendingIntent.getBroadcast(this, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("leaving","Beim Verlassen",NotificationManager.IMPORTANCE_HIGH))
    }
}

class ExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = context.getSharedPreferences("raus", Context.MODE_PRIVATE).getStringSet("items", emptySet()) ?: emptySet()
        if (reminders.isEmpty()) return
        val body = reminders.joinToString("\n") { "• $it" }
        val notification = NotificationCompat.Builder(context,"leaving")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Bevor du gehst!")
            .setContentText(reminders.first()).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX).setAutoCancel(true).build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001,notification)
    }
}
