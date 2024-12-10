#include <DHT.h>
#include <math.h>
#include <string.h>

#define SENSOR_ENABLE                                     (PC6)
//*************************************************************************//
// Temperature Sensor
static void rht_temperature_read_cb(sl_bt_evt_gatt_server_user_read_request_t *data);
static void rht_humidity_read_cb(sl_bt_evt_gatt_server_user_read_request_t *data);

static uint16_t rht_humidity = 0;
static int16_t rht_temperature = 0;
static bool send_temperature_next = true;
#define DATA_TYPE_TEMPERATURE 0x01
#define DATA_TYPE_HUMIDITY    0x02

#define DHTPIN 2
#define DHTTYPE DHT22

DHT dht(DHTPIN, DHTTYPE);

//*************************************************************************//
// BLE
static void ble_initialize_gatt_db(void);
static void ble_start_advertising(void);

//*************************************************************************//
// Generic access service
#define ADVERTISE_DEVICE_NAME_LEN_MAX 20

static uint16_t generic_access_service_handle;
static uint16_t name_characteristic_handle;
static const uint8_t advertised_name[] = "Smart Hub #00000";

typedef struct {
  uint8_t flags_length;          // Length of the Flags field
  uint8_t flags_type;            // Type of the Flags field
  uint8_t flags;                 // Flags field
  uint8_t mandatory_data_length; // Length of the mandatory data field
  uint8_t mandatory_data_type;   // Type of the mandatory data field
  uint8_t company_id[2];         // Company ID
  uint8_t firmware_id[2];        // Firmware ID
  uint8_t local_name_length;     // Length of the local name field
  uint8_t local_name_type;       // Type of the local name field
  uint8_t local_name[ADVERTISE_DEVICE_NAME_LEN_MAX]; // Local name field
} advertise_scan_response_t;

static advertise_scan_response_t adv_scan_response;

//*************************************************************************//
// Device information service
static uint16_t device_information_service_handle;
static uint16_t manufacturer_characteristic_handle;
static const uint8_t manufacturer_name[] = "Silicon Laboratories";
static uint16_t model_characteristic_handle;
static const uint8_t model_name[] = "BRD2602A";
static uint16_t serial_characteristic_handle;
static const uint8_t serial_name[] = "4963";
static uint16_t hardware_characteristic_handle;
static const uint8_t hardware_name[] = "A02";
static uint16_t firmware_characteristic_handle;
static const uint8_t firmware_name[] = "4.3.1";
static uint16_t systemid_characteristic_handle;
static const uint8_t systemid_name[] = "0x00";

//*************************************************************************//
static uint16_t gattdb_session_id;

// Environment service
static uint16_t environment_service_handle;
static uint16_t environment_temperature_characteristic_handle;
static uint16_t environment_humidity_characteristic_handle;

void setup()
{
  Serial.begin(115200);

  // Wait for the sensor power supplies to stabilize
  delay(500);

  Serial.println("SmartHub Team AndroidProgramming Project Board");

  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(D3, OUTPUT);
  digitalWrite(LED_BUILTIN, LED_BUILTIN_INACTIVE);
  digitalWrite(D2,LOW);

  dht.begin();
}

void loop()
{

}

static uint16_t blinky_service_handle;
static uint16_t led_control_characteristic_handle;

/**************************************************************************//**
 * Bluetooth stack event handler
 * Called when an event happens on BLE the stack
 *
 * @param[in] evt Event coming from the Bluetooth stack
 *****************************************************************************/
void sl_bt_on_event(sl_bt_msg_t *evt)
{

  switch (SL_BT_MSG_ID(evt->header)) {
    // -------------------------------
    // This event indicates the device has started and the radio is ready.
    // Do not call any stack command before receiving this boot event!
    case sl_bt_evt_system_boot_id:
    {
      Serial.println("BLE stack booted");

      // Initialize the application specific GATT table
      ble_initialize_gatt_db();

      // Start advertising
      ble_start_advertising();
    }
    break;

    // -------------------------------
    // This event indicates that a new connection was opened
    case sl_bt_evt_connection_opened_id:
      Serial.println("BLE connection opened");
      break;

    // -------------------------------
    // This event indicates that a connection was closed
    case sl_bt_evt_connection_closed_id:
      Serial.println("BLE connection closed");

      // Restart the advertisement
      ble_start_advertising();
      Serial.println("BLE advertisement restarted");
      break;

    // -------------------------------
    // This event indicates that the value of an attribute in the local GATT
    // database was changed by a remote GATT client
    case sl_bt_evt_gatt_server_attribute_value_id:
      // Check if the changed characteristic is the LED control
      if (led_control_characteristic_handle == evt->data.evt_gatt_server_attribute_value.attribute) {
        Serial.println("LED control characteristic data received");
        // Check the length of the received data
        if (evt->data.evt_gatt_server_attribute_value.value.len == 0) {
          break;
        }
        // Get the received byte
        uint8_t received_data = evt->data.evt_gatt_server_attribute_value.value.data[0];
        // Turn the LED on/off according to the received data
        // If we receive a '0' - turn the LED off
        // If we receive a '1' - turn the LED on
        if (received_data == 0x00) {
          digitalWrite(LED_BUILTIN, LED_BUILTIN_INACTIVE);
          digitalWrite(D3, LOW);
          Serial.println("\nLED off\n");
        } else if (received_data == 0x01) {
          Serial.println("\nLED on\n");
          digitalWrite(LED_BUILTIN, LED_BUILTIN_ACTIVE);
          digitalWrite(D3, HIGH);
        }
      }
      break;

    // -------------------------------
    // This event is received when a GATT characteristic status changes
    case sl_bt_evt_gatt_server_characteristic_status_id:
      break;

    // -------------------------------
    // Indicates that a remote GATT client is attempting to read a value of an attribute from the local GATT database
    case sl_bt_evt_gatt_server_user_read_request_id:
    {
      sl_bt_evt_gatt_server_user_read_request_t *data = &evt->data.evt_gatt_server_user_read_request;      
          if(send_temperature_next) {
            rht_temperature_read_cb(data);
          } else {
            rht_humidity_read_cb(data);
          }
          send_temperature_next = !send_temperature_next;
    }
    break;

    // -------------------------------
    // Default event handler
    default:
      break;
  }
}

/**************************************************************************//**
 * Starts BLE advertisement
 * Initializes advertising if it's called for the first time
 *****************************************************************************/
static void ble_start_advertising()
{
  static uint8_t advertising_set_handle = 0xff;
  static bool init = true;
  sl_status_t sc;

  if (init) {
    bd_addr address;
    uint8_t address_type;
    uint32_t unique_id;
    const char* device_name_prefix = "Smart Hub #";  // Full device name = "Dev Kit #00000"
    uint8_t device_name_prefix_length = strlen((const char*)device_name_prefix);
    char full_device_name[ADVERTISE_DEVICE_NAME_LEN_MAX];

    // Create advertise scan response data

    // Get unique id
    sl_bt_system_get_identity_address(&address, &address_type);
    unique_id = 0xFFFFFF & *((uint32_t*) address.addr);

    // Update full_device_name = "device_name_prefix + unique id"
    // Copy full_device_name to adv_scan_response.local_name
    strcpy(full_device_name, (const char*)device_name_prefix);
    (void)snprintf((char*)(full_device_name + device_name_prefix_length), 6, "%05d", (int)(unique_id & 0xFFFF));
    strcpy((char*)adv_scan_response.local_name, (const char*)full_device_name);

    adv_scan_response.local_name_length = strlen((const char*)adv_scan_response.local_name) + 1;
    adv_scan_response.local_name_type = 0x09;       // Local name type
    adv_scan_response.flags_length = 2;             // Advertise flags length
    adv_scan_response.flags_type = 0x01;            // Advertise flags type
    adv_scan_response.flags = 0x02 | 0x04;          // Advertise flags LE_GENERAL_DISCOVERABLE and BR_EDR_NOT_SUPPORTED
    adv_scan_response.mandatory_data_length = 5;    // Advertise mandatory data length
    adv_scan_response.mandatory_data_type = 0xFF;   // Advertise mandatory data type manufacturer
    adv_scan_response.company_id[0] = 0x47;         // Company ID = 0x0047
    adv_scan_response.company_id[1] = 0x00;
    adv_scan_response.firmware_id[0] = 0x02;        // Firmware ID = 0x0002
    adv_scan_response.firmware_id[1] = 0x00;

    // Create an advertising set
    sc = sl_bt_advertiser_create_set(&advertising_set_handle);
    app_assert_status(sc);

    // Set advertising interval to 100ms
    sc = sl_bt_advertiser_set_timing(
      advertising_set_handle,
      160,   // minimum advertisement interval (milliseconds * 1.6)
      160,   // maximum advertisement interval (milliseconds * 1.6)
      0,     // advertisement duration
      0);    // maximum number of advertisement events
    app_assert_status(sc);
    init = false;
  }

  // Generate data for advertising
  sc = sl_bt_legacy_advertiser_generate_data(advertising_set_handle, sl_bt_advertiser_general_discoverable);
  app_assert_status(sc);

  // Start advertising and enable connections
  sc = sl_bt_legacy_advertiser_start(advertising_set_handle, sl_bt_advertiser_connectable_scannable);
  app_assert_status(sc);

  Serial.print("Started advertising as '");
  Serial.print((const char*)advertised_name);
  Serial.println("'...");

  // Set user-defined advertising data packet or scan response packet on an advertising set.
  sl_bt_legacy_advertiser_set_data(advertising_set_handle, 0, sizeof(adv_scan_response), (uint8_t*)(&adv_scan_response));

  // Start advertising and enable connections
  sc = sl_bt_legacy_advertiser_start(advertising_set_handle, sl_bt_advertiser_connectable_scannable);
  app_assert_status(sc);
}

/**************************************************************************//**
 * Read the temperature from Si7021 sensor and send the value over BLE
 * @param[in] data sl_bt_evt_gatt_server_user_read_request_t
 * coming from the Bluetooth stack
 *****************************************************************************/
// 온도 읽기 콜백 함수 수정
static void rht_temperature_read_cb(sl_bt_evt_gatt_server_user_read_request_t *data)
{
  // 측정 데이터 업데이트
  float temp = dht.readTemperature();
  rht_temperature = temp * 100; // 소수점 이하 두 자리까지 표현

  // 데이터 버퍼 생성 (데이터 타입 바이트 포함)
  uint8_t buffer[3];
  buffer[0] = DATA_TYPE_TEMPERATURE;
  buffer[1] = (uint8_t)(rht_temperature & 0xFF);
  buffer[2] = (uint8_t)((rht_temperature >> 8) & 0xFF);

  Serial.print("\nTemperature read response; value=");
  Serial.println(temp);

  // 읽기 응답 전송
  sl_status_t sc = sl_bt_gatt_server_send_user_read_response(
    data->connection,
    data->characteristic,
    0,
    sizeof(buffer),
    buffer,
    NULL);

  app_assert_status(sc);
}

// 습도 읽기 콜백 함수 수정
static void rht_humidity_read_cb(sl_bt_evt_gatt_server_user_read_request_t *data)
{
  // 측정 데이터 업데이트
  float humidity = dht.readHumidity();
  rht_humidity = humidity * 100; // 소수점 이하 두 자리까지 표현

  // 데이터 버퍼 생성 (데이터 타입 바이트 포함)
  uint8_t buffer[3];
  buffer[0] = DATA_TYPE_HUMIDITY;
  buffer[1] = (uint8_t)(rht_humidity & 0xFF);
  buffer[2] = (uint8_t)((rht_humidity >> 8) & 0xFF);

  Serial.print("Humidity read response; value=");
  Serial.println(humidity);
  Serial.print("");

  // 읽기 응답 전송
  sl_status_t sc = sl_bt_gatt_server_send_user_read_response(
    data->connection,
    data->characteristic,
    0,
    sizeof(buffer),
    buffer,
    NULL);

  app_assert_status(sc);
}

/**************************************************************************//**
 * Initializes the GATT database
 * Creates a new GATT session and adds certain services and characteristics
 *****************************************************************************/
static void ble_initialize_gatt_db(void)
{
  sl_status_t sc;
  // Create a new GATT database
  sc = sl_bt_gattdb_new_session(&gattdb_session_id);
  app_assert_status(sc);

  //*************************************************************************//
  // Add the Generic Access service to the GATT DB
  const uint8_t generic_access_service_uuid[] = { 0x00, 0x18 };
  sc = sl_bt_gattdb_add_service(gattdb_session_id,
                                sl_bt_gattdb_primary_service,
                                SL_BT_GATTDB_ADVERTISED_SERVICE,
                                sizeof(generic_access_service_uuid),
                                generic_access_service_uuid,
                                &generic_access_service_handle);
  app_assert_status(sc);

  // Add the Device Name characteristic to the Generic Access service
  // The value of the Device Name characteristic will be advertised
  const sl_bt_uuid_16_t device_name_characteristic_uuid = { .data = { 0x00, 0x2A } };
  sc = sl_bt_gattdb_add_uuid16_characteristic(gattdb_session_id,
                                              generic_access_service_handle,
                                              SL_BT_GATTDB_CHARACTERISTIC_READ,
                                              0x00,
                                              0x00,
                                              device_name_characteristic_uuid,
                                              sl_bt_gattdb_fixed_length_value,
                                              sizeof(advertised_name) - 1,
                                              sizeof(advertised_name) - 1,
                                              advertised_name,
                                              &name_characteristic_handle);
  app_assert_status(sc);

  // Start the Generic Access service
  sc = sl_bt_gattdb_start_service(gattdb_session_id, generic_access_service_handle);
  app_assert_status(sc);

  //*************************************************************************//
  // Add the Device Information service to the GATT DB
  const uint8_t device_information_service_uuid[] = { 0x0A, 0x18 };
  sc = sl_bt_gattdb_add_service(gattdb_session_id,
                                sl_bt_gattdb_primary_service,
                                SL_BT_GATTDB_ADVERTISED_SERVICE,
                                sizeof(device_information_service_uuid),
                                device_information_service_uuid,
                                &device_information_service_handle);
  app_assert_status(sc);

  // Add the Manufacturer Name String characteristic to the Device Information service
  // The value of the Manufacturer Name String characteristic will be advertised
  const sl_bt_uuid_16_t manufacturer_name_characteristic_uuid = { .data = { 0x29, 0x2A } };
  sc = sl_bt_gattdb_add_uuid16_characteristic(gattdb_session_id,
                                              device_information_service_handle,
                                              SL_BT_GATTDB_CHARACTERISTIC_READ,
                                              0x00,
                                              0x00,
                                              manufacturer_name_characteristic_uuid,
                                              sl_bt_gattdb_fixed_length_value,
                                              sizeof(manufacturer_name) - 1,
                                              sizeof(manufacturer_name) - 1,
                                              manufacturer_name,
                                              &manufacturer_characteristic_handle);
  app_assert_status(sc);

  // Add the Model Number String characteristic to the Device Information service
  // The value of the Model Number String characteristic will be advertised
  const sl_bt_uuid_16_t model_name_characteristic_uuid = { .data = { 0x24, 0x2A } };
  sc = sl_bt_gattdb_add_uuid16_characteristic(gattdb_session_id,
                                              device_information_service_handle,
                                              SL_BT_GATTDB_CHARACTERISTIC_READ,
                                              0x00,
                                              0x00,
                                              model_name_characteristic_uuid,
                                              sl_bt_gattdb_fixed_length_value,
                                              sizeof(model_name) - 1,
                                              sizeof(model_name) - 1,
                                              model_name,
                                              &model_characteristic_handle);
  app_assert_status(sc);

  // Add the Serial Number String characteristic to the Device Information service
  // The value of the Serial Number String characteristic will be advertised
  const sl_bt_uuid_16_t serial_number_characteristic_uuid = { .data = { 0x25, 0x2A } };
  sc = sl_bt_gattdb_add_uuid16_characteristic(gattdb_session_id,
                                              device_information_service_handle,
                                              SL_BT_GATTDB_CHARACTERISTIC_READ,
                                              0x00,
                                              0x00,
                                              serial_number_characteristic_uuid,
                                              sl_bt_gattdb_fixed_length_value,
                                              sizeof(serial_name) - 1,
                                              sizeof(serial_name) - 1,
                                              serial_name,
                                              &serial_characteristic_handle);
  app_assert_status(sc);

  // Add the Hardware Revision String characteristic to the Device Information service
  // The value of the Hardware Revision String characteristic will be advertised
  const sl_bt_uuid_16_t hardware_revision_characteristic_uuid = { .data = { 0x27, 0x2A } };
  sc = sl_bt_gattdb_add_uuid16_characteristic(gattdb_session_id,
                                              device_information_service_handle,
                                              SL_BT_GATTDB_CHARACTERISTIC_READ,
                                              0x00,
                                              0x00,
                                              hardware_revision_characteristic_uuid,
                                              sl_bt_gattdb_fixed_length_value,
                                              sizeof(hardware_name) - 1,
                                              sizeof(hardware_name) - 1,
                                              hardware_name,
                                              &hardware_characteristic_handle);
  app_assert_status(sc);

  // Add the Firmware Revision String characteristic to the Device Information service
  // The value of the Firmware Revision String characteristic will be advertised
  const sl_bt_uuid_16_t firmware_revision_characteristic_uuid = { .data = { 0x26, 0x2A } };
  sc = sl_bt_gattdb_add_uuid16_characteristic(gattdb_session_id,
                                              device_information_service_handle,
                                              SL_BT_GATTDB_CHARACTERISTIC_READ,
                                              0x00,
                                              0x00,
                                              firmware_revision_characteristic_uuid,
                                              sl_bt_gattdb_fixed_length_value,
                                              sizeof(firmware_name) - 1,
                                              sizeof(firmware_name) - 1,
                                              firmware_name,
                                              &firmware_characteristic_handle);
  app_assert_status(sc);

  // Add the System ID String characteristic to the Device Information service
  // The value of the System ID String characteristic will be advertised
  const sl_bt_uuid_16_t system_id_characteristic_uuid = { .data = { 0x23, 0x2A } };
  sc = sl_bt_gattdb_add_uuid16_characteristic(gattdb_session_id,
                                              device_information_service_handle,
                                              SL_BT_GATTDB_CHARACTERISTIC_READ,
                                              0x00,
                                              0x00,
                                              system_id_characteristic_uuid,
                                              sl_bt_gattdb_fixed_length_value,
                                              sizeof(systemid_name) - 1,
                                              sizeof(systemid_name) - 1,
                                              systemid_name,
                                              &systemid_characteristic_handle);
  app_assert_status(sc);

  // Start the Device Information service
  sc = sl_bt_gattdb_start_service(gattdb_session_id, device_information_service_handle);
  app_assert_status(sc);

  //*************************************************************************//
  // Add the Environment Sensing service to the GATT DB
  const uint8_t environment_service_uuid[] = { 0x1a, 0x18 };

  sc = sl_bt_gattdb_add_service(gattdb_session_id,
                                sl_bt_gattdb_primary_service,
                                SL_BT_GATTDB_ADVERTISED_SERVICE,
                                sizeof(environment_service_uuid),
                                environment_service_uuid,
                                &environment_service_handle);
  app_assert_status(sc);

  // Add the Temperature characteristic to the  Environment Sensing service
  // The value of the Temperature characteristic will be advertised
  const sl_bt_uuid_16_t temperature_characteristic_uuid = { .data = {
                                                              0x6e, 0x2a
                                                            } };

  sc = sl_bt_gattdb_add_uuid16_characteristic(gattdb_session_id,
                                              environment_service_handle,
                                              SL_BT_GATTDB_CHARACTERISTIC_READ,
                                              0x00,
                                              0x00,
                                              temperature_characteristic_uuid,
                                              sl_bt_gattdb_user_managed_value,
                                              0,
                                              0,
                                              NULL,
                                              &environment_temperature_characteristic_handle);
  app_assert_status(sc);

  // Add the Humidity characteristic to the  Environment Sensing service
  // The value of the Humidity characteristic will be advertised
  const sl_bt_uuid_16_t humidity_characteristic_uuid = { .data = {
                                                           0x6f, 0x2a
                                                         } };

  sc = sl_bt_gattdb_add_uuid16_characteristic(gattdb_session_id,
                                              environment_service_handle,
                                              SL_BT_GATTDB_CHARACTERISTIC_READ,
                                              0x00,
                                              0x00,
                                              humidity_characteristic_uuid,
                                              sl_bt_gattdb_user_managed_value,
                                              0,
                                              0,
                                              NULL,
                                              &environment_humidity_characteristic_handle);
  app_assert_status(sc);

  // Start the Environment Sensing service
  sc = sl_bt_gattdb_start_service(gattdb_session_id, environment_service_handle);
  app_assert_status(sc);

//*********************************************************************************//
  // Add the Blinky service to the GATT DB
  // UUID: de8a5aac-a99b-c315-0c80-60d4cbb51224
  const uuid_128 blinky_service_uuid = {
    .data = { 0x24, 0x12, 0xb5, 0xcb, 0xd4, 0x60, 0x80, 0x0c, 0x15, 0xc3, 0x9b, 0xa9, 0xac, 0x5a, 0x8a, 0xde }
  };
  sc = sl_bt_gattdb_add_service(gattdb_session_id,
                                sl_bt_gattdb_primary_service,
                                SL_BT_GATTDB_ADVERTISED_SERVICE,
                                sizeof(blinky_service_uuid),
                                blinky_service_uuid.data,
                                &blinky_service_handle);
  app_assert_status(sc);

  // Add the 'LED Control' characteristic to the Blinky service
  // UUID: 5b026510-4088-c297-46d8-be6c736a087a
  const uuid_128 led_control_characteristic_uuid = {
    .data = { 0x7a, 0x08, 0x6a, 0x73, 0x6c, 0xbe, 0xd8, 0x46, 0x97, 0xc2, 0x88, 0x40, 0x10, 0x65, 0x02, 0x5b }
  };
  uint8_t led_char_init_value = 0;
  sc = sl_bt_gattdb_add_uuid128_characteristic(gattdb_session_id,
                                               blinky_service_handle,
                                               SL_BT_GATTDB_CHARACTERISTIC_READ | SL_BT_GATTDB_CHARACTERISTIC_WRITE,
                                               0x00,
                                               0x00,
                                               led_control_characteristic_uuid,
                                               sl_bt_gattdb_fixed_length_value,
                                               1,                               // max length
                                               sizeof(led_char_init_value),     // initial value length
                                               &led_char_init_value,            // initial value
                                               &led_control_characteristic_handle);

  // Start the Blinky service
  sc = sl_bt_gattdb_start_service(gattdb_session_id, blinky_service_handle);
  app_assert_status(sc);

  // Commit the GATT DB changes
  sc = sl_bt_gattdb_commit(gattdb_session_id);
  app_assert_status(sc);
}
