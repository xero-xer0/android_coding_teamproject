package com.example.android_coding_teamproject

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.UUID

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

    // GATT UUID 설정 (BLE 서버 코드에서 사용한 UUID와 동일하게 설정)
    private val LED_SERVICE_UUID = UUID.fromString("de8a5aac-a99b-c315-0c80-60d4cbb51224")
    private val LED_CHARACTERISTIC_UUID = UUID.fromString("5b026510-4088-c297-46d8-be6c736a087a")

    private var bluetoothGatt: BluetoothGatt? = null

    override fun onResume() {
        super.onResume()

        connectedDevice = intent.getParcelableExtra("connectedDevice")
        connectedDevice?.let { device ->
            deviceNameTextView.text = device.name ?: "Unknown Device"
            deviceStatusTextView.text = "Connected"

            // BLE 장치에 연결하고 GATT 설정
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), 1)
            }
        }
    }

    // GATT 콜백 설정
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread {
                    deviceStatusTextView.text = "Disconnected"
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(LED_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(LED_CHARACTERISTIC_UUID)

                if (characteristic != null) {
                    runOnUiThread {
                        // LED 제어 버튼 클릭 리스너 설정
                        findViewById<ImageButton>(R.id.device_action_button_1).setOnClickListener {
                            toggleLED(characteristic)
                        }
                    }
                }
            }
        }
    }

    // LED 토글 메서드
    private fun toggleLED(characteristic: BluetoothGattCharacteristic) {
        val newValue = if (characteristic.value?.get(0) == 1.toByte()) 0 else 1
        characteristic.value = byteArrayOf(newValue.toByte())

        bluetoothGatt?.writeCharacteristic(characteristic)
        deviceStatusTextView.text = if (newValue == 1) "LED On" else "LED Off"
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    fun clickpopupBtn(v: View?) {
        Toast.makeText(this, "click", Toast.LENGTH_SHORT).show()
    }
}