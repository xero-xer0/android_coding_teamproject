package com.example.android_coding_teamproject

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var menubtn: AppCompatImageButton
    private lateinit var addbtn: AppCompatImageButton
    private var connectedDevice: BluetoothDevice? = null
    private lateinit var deviceNameTextView: TextView
    private lateinit var deviceStatusTextView: TextView
    private lateinit var deviceFeatureTextView: TextView
    private lateinit var actionButton: ImageButton
    private var bluetoothGatt: BluetoothGatt? = null
    // 데이터 특성 UUID 정의
    private val ledServiceUUID: UUID = UUID.fromString("de8a5aac-a99b-c315-0c80-60d4cbb51224")
    private val dataCharacteristicUUID: UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb")
    private val ledCharacteristicUUID: UUID = UUID.fromString("5b026510-4088-c297-46d8-be6c736a087a")
    private var tempCharacteristic: BluetoothGattCharacteristic? = null
    private var humCharacteristic: BluetoothGattCharacteristic? = null
    private var dataCharacteristic: BluetoothGattCharacteristic? = null
    // 클래스 내에 추가
    companion object {
        private const val DATA_TYPE_TEMPERATURE = 0x01
        private const val DATA_TYPE_HUMIDITY = 0x02
    }

    private lateinit var handler: Handler
    private var readRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        setupUI()
    }

    private fun setupUI() {
        // TextView를 먼저 초기화합니다.
        deviceNameTextView = findViewById(R.id.device_name)
        deviceStatusTextView = findViewById(R.id.device_status)
        deviceFeatureTextView = findViewById(R.id.device_feature)
        actionButton = findViewById(R.id.device_action_button)

        addbtn = findViewById(R.id.add_device_button)
        addbtn.setOnClickListener {
            startActivity(Intent(this, DeviceListActivity::class.java))
        }
        menubtn = findViewById(R.id.bottom_menu_button)

        updateDeviceStatus()

        menubtn.setOnClickListener { view ->
            val popup = PopupMenu(this, view).apply {
                menuInflater.inflate(R.menu.popup, menu)
                setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.menupo_add -> startActivity(
                            Intent(
                                this@MainActivity,
                                DeviceListActivity::class.java
                            )
                        )
                        R.id.menupo_disconnectall -> {
                            val builder = AlertDialog.Builder(this@MainActivity)
                            builder.setTitle("모든 기기 연결 해제")
                            builder.setMessage("모든 기기의 연결을 해제하시겠습니까?")
                            builder.setPositiveButton("네") { _, _ ->
                                bluetoothGatt?.disconnect()
                                bluetoothGatt?.close()
                                bluetoothGatt = null
                                connectedDevice = null  // 연결된 기기 정보를 null로 설정

                                runOnUiThread {
                                    updateDeviceStatus()  // UI 업데이트
                                }
                            }
                            builder.setNegativeButton("아니오") { dialog, _ -> dialog.dismiss() }
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

        // DeviceListActivity에서 선택한 장치를 가져옵니다
        connectedDevice = intent.getParcelableExtra("connectedDevice")

        if (connectedDevice != null) {
            connectToDevice()
        } else {
            updateDeviceStatus()
        }
    }

    private fun connectToDevice() {
        connectedDevice?.let { device ->
            deviceNameTextView.text = device.name ?: "알 수 없는 기기"
            deviceStatusTextView.text = "연결됨"

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    1
                )
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d("MainActivity", "Connected to GATT server")
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d("MainActivity", "Disconnected from GATT server")
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
                connectedDevice = null
                runOnUiThread {
                    deviceStatusTextView.text = "연결되지 않음"
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                ledServiceUUID.let { serviceUUID ->
                    gatt.getService(serviceUUID)?.getCharacteristic(ledCharacteristicUUID)
                        ?.let { characteristic ->
                            runOnUiThread {
                                findViewById<ImageButton>(R.id.device_action_button).setOnClickListener {
                                    toggleLED(characteristic)
                                }
                            }
                        }
                }
                val serviceUUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb") // Environment Sensing Service
                val environmentService = gatt.getService(serviceUUID)
                if (environmentService != null) {
                    dataCharacteristic = environmentService.getCharacteristic(dataCharacteristicUUID)
                    if (dataCharacteristic != null) {
                        startReadingCharacteristic()
                    } else {
                        Log.e("MainActivity", "데이터 특성을 찾을 수 없습니다")
                    }
                } else {
                    Log.e("MainActivity", "환경 센싱 서비스를 찾을 수 없습니다")
                }
            } else {
                Log.w("MainActivity", "onServicesDiscovered received: $status")
            }
        }

        private fun startReadingCharacteristic() {
            handler = Handler(Looper.getMainLooper())
            readRunnable = object : Runnable {
                override fun run() {
                    if (bluetoothGatt != null && dataCharacteristic != null) {
                        bluetoothGatt?.readCharacteristic(dataCharacteristic)
                        handler.postDelayed(this, 1000) // 1초마다 읽기
                    }
                }
            }
            handler.post(readRunnable!!)
        }

        private fun toggleLED(characteristic: BluetoothGattCharacteristic) {
            // 연결이 끊어졌으면 아무 작업도 하지 않음
//            if (bluetoothGatt == null || !bluetoothGatt!!.connect()) {
//                Toast.makeText(this, "연결된 기기가 없습니다", Toast.LENGTH_SHORT).show()
//                return
//            }

            val newValue = if (characteristic.value?.getOrNull(0) == 1.toByte()) 0 else 1
            characteristic.value = byteArrayOf(newValue.toByte())

            // BLE 특성값을 장치에 쓰기
            bluetoothGatt?.writeCharacteristic(characteristic)

            // UI 업데이트
            deviceFeatureTextView.text = if (newValue == 1) "LED 켜짐" else "LED 꺼짐"
            val deviceCard = findViewById<LinearLayout>(R.id.device_card)
            if(newValue==1)deviceCard.background.setTint(Color.parseColor("#43de00"))
            else deviceCard.background.setTint(Color.parseColor("#D0D0D0"))
        }

        private fun startReadingCharacteristics() {
            // 주기적으로 특성 읽기 시작
            handler = Handler(Looper.getMainLooper())
            readRunnable = object : Runnable {
                override fun run() {
                    if (bluetoothGatt != null) {
                        tempCharacteristic?.let {
                            bluetoothGatt?.readCharacteristic(it)
                        }
                        humCharacteristic?.let {
                            bluetoothGatt?.readCharacteristic(it)
                        }
                        handler.postDelayed(this, 1000) // 1초마다 읽기
                    }
                }
            }
            handler.post(readRunnable!!)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.let {
                    if (it.uuid == dataCharacteristicUUID) {
                        val data = it.value
                        if (data.size >= 3) {
                            val dataType = data[0].toInt() and 0xFF
                            val valueRaw = ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                            val value = valueRaw / 100.0f

                            when (dataType) {
                                DATA_TYPE_TEMPERATURE -> {
                                    runOnUiThread {
                                        findViewById<TextView>(R.id.room_temp).text = "$value"
                                    }
                                }
                                DATA_TYPE_HUMIDITY -> {
                                    runOnUiThread {
                                        findViewById<TextView>(R.id.room_hum).text = "$value"
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Log.e("MainActivity", "특성 읽기에 실패했습니다: $status")
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        readRunnable?.let {
            handler.removeCallbacks(it)
        }
        updateDeviceStatus()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
        updateDeviceStatus()
    }
}