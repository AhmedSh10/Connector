# 🔌 Connector - Android Bluetooth Controller

<div align="center">
  
  ![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
  ![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
  ![Arduino](https://img.shields.io/badge/Hardware-Arduino-00979D?style=for-the-badge&logo=arduino&logoColor=white)
  ![Bluetooth](https://img.shields.io/badge/Protocol-Bluetooth-0082FC?style=for-the-badge&logo=bluetooth&logoColor=white)
  
</div>

## 📋 Overview

**Connector** is a powerful Android application that enables seamless Bluetooth communication between Android devices and Arduino microcontrollers using the **HC-05 Bluetooth module**. This app serves as a universal controller for IoT projects, robotics, home automation, and any Arduino-based system requiring wireless control.

## ✨ Key Features

- **📡 Bluetooth Connectivity**: Easy pairing and connection with HC-05 modules
- **🎮 Real-Time Control**: Send commands instantly to Arduino devices
- **📥 Data Reception**: Receive and display data from Arduino sensors
- **🔄 Bidirectional Communication**: Full duplex communication support
- **📱 User-Friendly Interface**: Intuitive UI for device management
- **🔍 Device Discovery**: Automatic scanning and listing of available Bluetooth devices
- **💾 Connection History**: Remember previously connected devices
- **⚡ Low Latency**: Optimized for real-time control applications
- **🛡️ Error Handling**: Robust connection management with retry mechanisms

## 🛠️ Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **UI Framework**: XML Layouts with Material Design
- **Bluetooth API**: Android Bluetooth Classic API
- **Coroutines**: For asynchronous Bluetooth operations
- **ViewBinding**: For type-safe view access
- **LiveData/StateFlow**: For reactive data updates

## 🔌 Supported Hardware

### HC-05 Bluetooth Module Specifications
- **Protocol**: Bluetooth 2.0 + EDR
- **Frequency**: 2.4GHz ISM band
- **Range**: Up to 10 meters (Class 2)
- **Baud Rate**: 9600 (default), configurable up to 1382400
- **Operating Voltage**: 3.3V - 5V
- **Default PIN**: 1234 or 0000

### Compatible Arduino Boards
- Arduino Uno
- Arduino Mega
- Arduino Nano
- ESP32 (with HC-05)
- Any Arduino-compatible board with UART

## 📱 App Features

### Connection Management
- **Scan Devices**: Discover nearby Bluetooth devices
- **Pair Devices**: Easy pairing process with PIN entry
- **Auto-Connect**: Automatically connect to last used device
- **Connection Status**: Real-time connection state indicator

### Data Communication
- **Send Commands**: Text-based command transmission
- **Receive Data**: Display incoming data from Arduino
- **Custom Protocols**: Support for custom communication protocols
- **Data Logging**: Save communication logs for debugging

## 🚀 Getting Started

### Prerequisites

**Android Side:**
- Android Studio Arctic Fox or later
- Android device with Bluetooth (SDK 21+)
- Bluetooth permissions granted

**Arduino Side:**
- Arduino board (Uno, Mega, Nano, etc.)
- HC-05 Bluetooth module
- Jumper wires for connections

### Hardware Setup

#### HC-05 to Arduino Connections:
```
HC-05 VCC  → Arduino 5V
HC-05 GND  → Arduino GND
HC-05 TXD  → Arduino RX (Pin 0) or SoftwareSerial RX
HC-05 RXD  → Arduino TX (Pin 1) or SoftwareSerial TX (through voltage divider)
```

**⚠️ Important**: HC-05 RXD operates at 3.3V. Use a voltage divider (1kΩ and 2kΩ resistors) when connecting to Arduino's 5V TX pin.

### Software Installation

1. **Clone the repository**:
```bash
git clone https://github.com/AhmedSh10/Connector.git
```

2. **Open in Android Studio**

3. **Sync Gradle dependencies**

4. **Build and install on your Android device**

### Arduino Setup

Basic Arduino sketch for testing:

```cpp
#include <SoftwareSerial.h>

SoftwareSerial BTSerial(10, 11); // RX, TX

void setup() {
  Serial.begin(9600);
  BTSerial.begin(9600);
  Serial.println("Bluetooth Ready!");
}

void loop() {
  // Receive data from Android app
  if (BTSerial.available()) {
    char command = BTSerial.read();
    Serial.println(command);
    
    // Process command
    if (command == '1') {
      digitalWrite(LED_BUILTIN, HIGH);
      BTSerial.println("LED ON");
    } else if (command == '0') {
      digitalWrite(LED_BUILTIN, LOW);
      BTSerial.println("LED OFF");
    }
  }
  
  // Send data to Android app
  if (Serial.available()) {
    BTSerial.write(Serial.read());
  }
}
```

## 📂 Project Structure

```
app/
├── bluetooth/      # Bluetooth communication logic
├── ui/             # Activities and fragments
├── viewmodel/      # ViewModels for UI logic
├── data/           # Data models and repositories
├── utils/          # Utility classes and helpers
└── res/            # Resources (layouts, drawables, strings)
```

## 🎯 Use Cases

- **🤖 Robotics Control**: Control robot movements wirelessly
- **🏠 Home Automation**: Control lights, fans, and appliances
- **🌡️ IoT Monitoring**: Receive sensor data (temperature, humidity, etc.)
- **🚗 RC Car Control**: Build and control remote-controlled vehicles
- **🎮 Custom Controllers**: Create custom game controllers
- **📊 Data Logging**: Collect and log sensor data on your phone
- **💡 LED Control**: Control LED strips and displays
- **🔊 Audio Projects**: Trigger sounds and music playback

## 🔧 Configuration

### Bluetooth Permissions (AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### HC-05 Configuration (AT Commands)
```
AT+NAME=MyDevice    // Set device name
AT+PSWD=1234        // Set pairing password
AT+UART=9600,0,0    // Set baud rate
```

## 🐛 Troubleshooting

### Connection Issues
- Ensure HC-05 is powered and LED is blinking
- Check if device is paired in Android Bluetooth settings
- Verify correct baud rate (default: 9600)
- Ensure proper wiring and voltage levels

### Data Not Received
- Check Serial Monitor on Arduino side
- Verify baud rates match on both sides
- Ensure proper RX/TX connections
- Check for voltage divider on HC-05 RXD

## 🤝 Contributing

Contributions are welcome! Whether it's bug fixes, new features, or documentation improvements.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is available for educational and personal use.

## 👨‍💻 Developer

**Ahmed Shaaban**

- GitHub: [@AhmedSh10](https://github.com/AhmedSh10)
- LinkedIn: [Ahmed Shaaban](https://linkedin.com/in/ahmed-shaaban)

## 🙏 Acknowledgments

- Arduino community for extensive documentation
- HC-05 module manufacturers for reliable hardware
- Android Bluetooth API documentation

## 📚 Resources

- [HC-05 Datasheet](https://www.electronicwings.com/sensors-modules/hc-05-bluetooth-module)
- [Arduino Bluetooth Tutorial](https://www.arduino.cc/en/Guide/ArduinoBT)
- [Android Bluetooth Guide](https://developer.android.com/guide/topics/connectivity/bluetooth)

---

<div align="center">
  
  **⭐ If you find this project useful, please consider giving it a star!**
  
  **🔌 Connect your world wirelessly!**
  
</div>
