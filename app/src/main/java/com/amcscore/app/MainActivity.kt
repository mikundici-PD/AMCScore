package com.amcscore.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var server: SimpleHttpServer? = null
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnVolley: Button = findViewById(R.id.btnVolley)
        val btnBasket: Button = findViewById(R.id.btnBasket)
        statusView = findViewById(R.id.status)

        if (server == null) {
            server = SimpleHttpServer(this, 8080)
            server?.start()
            statusView.text = "Server attivo su porta 8080.\nSul device: http://127.0.0.1:8080\nDa altri dispositivi: http://IP_DEL_TELEFONO:8080"
        }

        btnVolley.setOnClickListener {
            openBrowser("http://127.0.0.1:8080/control/Volley")
        }

        btnBasket.setOnClickListener {
            openBrowser("http://127.0.0.1:8080/control/Basket")
        }
    }

    private fun openBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }
}
