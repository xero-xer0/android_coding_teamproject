package com.example.android_coding_teamproject

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class activity_device_appinfo : AppCompatActivity() {

    private lateinit var mBtnGotoHome: ImageButton
    private lateinit var mBtnAppInfo: TextView
    private lateinit var mBtnHowToUse: TextView
    private lateinit var mBtnFeedback: TextView
    private lateinit var mBtnDeviceRename: TextView
    private lateinit var mBtnDeviceDelete: TextView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_device_appinfo)
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


    }

}
