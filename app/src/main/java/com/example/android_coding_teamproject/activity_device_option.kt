package com.example.android_coding_teamproject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class activity_device_option : AppCompatActivity() {

    private lateinit var mBtnGotoHome: ImageButton
    private lateinit var mBtnAppInfo: TextView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_device_option)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mBtnGotoHome = findViewById<ImageButton>(R.id.bottom_home_button)
        mBtnGotoHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        mBtnAppInfo = findViewById<TextView>(R.id.appinfo)
        mBtnAppInfo.setOnClickListener {
            val intent2 = Intent(this, activity_device_appinfo::class.java)
            startActivity(intent2)
        }
        findViewById<TextView>(R.id.howtouse).setOnClickListener { view ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://forms.gle/LYbvMLkZJUPEhgKj8")))
        }
        findViewById<TextView>(R.id.feedback).setOnClickListener { view ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://forms.gle/LYbvMLkZJUPEhgKj8")))
        }
    }

}
