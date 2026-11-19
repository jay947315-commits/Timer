package de.rauserinnern.app

import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.view.ViewGroup

class MapDrawActivity : Activity() {
    private var saved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lat = intent.getDoubleExtra("lat", 0.0)
        val lng = intent.getDoubleExtra("lng", 0.0)
        if (lat == 0.0 && lng == 0.0) {
            Toast.makeText(this, "Lege zuerst deinen Zuhause-Standort fest.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val title = TextView(this).apply {
            text = "Hausbereich einzeichnen"
            textSize = 20f
            setTextColor(Color.rgb(27,35,51))
            setPadding(18, 18, 18, 8)
        }
        val hint = TextView(this).apply {
            text = "Tippe auf „Umriss zeichnen“ und markiere mindestens 3 Ecken deines Hauses. Du kannst die Punkte danach verschieben."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(18, 0, 18, 12)
        }
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Android")
        layout.addView(title)
        layout.addView(hint)
        layout.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        web.loadDataWithBaseURL("https://unpkg.com", html(lat, lng), "text/html", "UTF-8", null)
    }

    private fun html(lat: Double, lng: Double) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet.draw/1.0.4/leaflet.draw.css">
<style>html,body,#map{height:100%;margin:0;font-family:Arial}.tools{position:absolute;z-index:1000;bottom:18px;left:12px;right:12px;display:flex;gap:8px}.tools button{border:0;border-radius:12px;padding:14px;background:#2463eb;color:white;font-weight:bold;flex:1}.tools .save{background:#14213d}</style></head>
<body><div id="map"></div><div class="tools"><button onclick="startDraw()">Umriss zeichnen</button><button class="save" onclick="saveShape()">Speichern</button></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet.draw/1.0.4/leaflet.draw.js"></script>
<script>
const map=L.map('map').setView([$lat,$lng],19);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:21,attribution:'© OpenStreetMap'}).addTo(map);
L.marker([$lat,$lng]).addTo(map).bindPopup('Dein gespeicherter Zuhause-Standort').openPopup();
const group=new L.FeatureGroup();map.addLayer(group);let polygon=null;
function startDraw(){const drawer=new L.Draw.Polygon(map,{allowIntersection:false,showArea:true,shapeOptions:{color:'#2463eb',weight:4,fillOpacity:.18}});drawer.enable();}
map.on(L.Draw.Event.CREATED,e=>{group.clearLayers();polygon=e.layer;group.addLayer(polygon);});
function saveShape(){if(!polygon){alert('Bitte zuerst den Hausumriss mit mindestens 3 Punkten zeichnen.');return;}
const pts=polygon.getLatLngs()[0].map(p=>({lat:p.lat,lng:p.lng}));
Android.save(JSON.stringify(pts));}
</script></body></html>
    """.trimIndent()

    inner class Bridge {
        @JavascriptInterface fun save(json: String) {
            if (saved) return
            saved = true
            runOnUiThread {
                setResult(Activity.RESULT_OK, intent.putExtra("polygon", json))
                finish()
            }
        }
    }
}
