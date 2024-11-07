package com.example.android_coding_teamproject

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.*
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
    private var connectedDevice: BluetoothDevice? = null
    private lateinit var deviceNameTextView: TextView
    private lateinit var deviceStatusTextView: TextView
    private lateinit var deviceFeatureTextView: TextView
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler()  // Handler for optimized updates

    private val LED_SERVICE_UUID = UUID.fromString("de8a5aac-a99b-c315-0c80-60d4cbb51224")
    private val LED_CHARACTERISTIC_UUID = UUID.fromString("5b026510-4088-c297-46d8-be6c736a087a")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setupUI()
    }


    private fun setupUI() {
        menubtn = findViewById(R.id.add_device_button)
        updateDeviceStatus()

        menubtn.setOnClickListener { view ->
            val popup = PopupMenu(this, view).apply {
                menuInflater.inflate(R.menu.popup, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.menupo_add -> startActivity(Intent(this@MainActivity, DeviceListActivity::class.java))
                        R.id.menupo_disconnectall -> {
                            val builder = AlertDialog.Builder(this@MainActivity)
                            builder.setTitle("Disconnect All Devices")
                            builder.setMessage("Are you sure you want to disconnect all devices?")
                            builder.setPositiveButton("Yes") { dialog, which ->
                                bluetoothGatt?.close()
                                bluetoothGatt = null
                                connectedDevice = null  // 연결된 기기 정보를 null로 설정

                                // UI 업데이트는 runOnUiThread로 메인 스레드에서 처리
                                runOnUiThread {
                                    updateDeviceStatus()  // UI 업데이트
                                }
                            }
                            builder.setNegativeButton("No") { dialog, which -> }
                            builder.show()
                        }
                    }
                    true
                }
            }
            popup.show()
        }

        // Edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun updateDeviceStatus() {
        deviceNameTextView = findViewById(R.id.device_name)
        deviceStatusTextView = findViewById(R.id.device_status)
        deviceFeatureTextView = findViewById(R.id.device_feature)
        if (connectedDevice != null) {
            deviceNameTextView.text = connectedDevice?.name ?: "알 수 없는 기기"
            deviceStatusTextView.text = "연결됨"
        } else {
            deviceNameTextView.text = "기기 연결"
            deviceStatusTextView.text = "연결 안 됨"
        }
    }


    override fun onResume() {
        super.onResume()
        connectedDevice = intent.getParcelableExtra("connectedDevice")

        connectedDevice?.let { device ->
            deviceNameTextView.text = device.name ?: "Unknown Device"
            deviceStatusTextView.text = "Connected"
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), 1)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread {
                    deviceStatusTextView.text = "Disconnected"
                    bluetoothGatt?.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.getService(LED_SERVICE_UUID)?.getCharacteristic(LED_CHARACTERISTIC_UUID)?.let { characteristic ->
                    runOnUiThread {
                        findViewById<ImageButton>(R.id.device_action_button).setOnClickListener {
                            toggleLED(characteristic)
                        }
                    }
                }
            }
        }
    }

    private fun toggleLED(characteristic: BluetoothGattCharacteristic) {
        // 연결이 끊어졌으면 아무 작업도 하지 않음
        if (bluetoothGatt == null || !bluetoothGatt!!.connect()) {
            Toast.makeText(this, "연결된 기기가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        val newValue = if (characteristic.value?.getOrNull(0) == 1.toByte()) 0 else 1
        characteristic.value = byteArrayOf(newValue.toByte())

        // BLE 특성값을 장치에 쓰기
        bluetoothGatt?.writeCharacteristic(characteristic)

        // UI 업데이트
        deviceFeatureTextView.text = if (newValue == 1) "LED On" else "LED Off"
        findViewById<LinearLayout>(R.id.device_card).apply {
            setBackgroundColor(if (newValue == 1) Color.parseColor("#43de00") else Color.parseColor("#FFFFFF"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        bluetoothGatt = null
        updateDeviceStatus()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)  // Activity를 백그라운드로 보내고 연결을 유지합니다.
        updateDeviceStatus()
    }
}