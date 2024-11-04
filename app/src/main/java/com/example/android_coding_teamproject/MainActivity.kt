package com.example.android_coding_teamproject

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var menubtn: AppCompatImageButton
    private val deviceList = mutableListOf<BluetoothDevice>()

    private var connectedDevice: BluetoothDevice? = null
    private lateinit var deviceNameTextView: TextView
    private lateinit var deviceStatusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v: View, insets: WindowInsetsCompat ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        menubtn = findViewById(R.id.add_device_button)
        deviceNameTextView = findViewById(R.id.device_name_1)
        deviceStatusTextView = findViewById(R.id.device_status_1)

        menubtn.setOnClickListener { view ->
            // Create and show the popup menu
            val popup = PopupMenu(this@MainActivity, view)
            menuInflater.inflate(R.menu.popup, popup.menu)

            // Set menu item click listener
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menupo_add -> {
                        val intent = Intent(this@MainActivity, DeviceListActivity::class.java)
                        intent.putParcelableArrayListExtra("deviceList", ArrayList(deviceList))
                        startActivity(intent)
                    }
                    R.id.menupo_delete -> Toast.makeText(this@MainActivity, "DELETE", Toast.LENGTH_SHORT).show()
                }
                true
            }
            popup.show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if a connected device was passed from DeviceListActivity
        connectedDevice = intent.getParcelableExtra("connectedDevice")
        connectedDevice?.let { device ->
            // Display device name and status on Device 1 card
            deviceNameTextView.text = device.name ?: "Unknown Device"
            deviceStatusTextView.text = "Connected"
        }
    }

    fun clickpopupBtn(v: View?) {
        Toast.makeText(this, "click", Toast.LENGTH_SHORT).show()
    }
}