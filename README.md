# CamperTools 🚐

**CamperTools** is a lightweight, ad-free Android utility app designed for RVers, campers, and van-lifers. It combines essential tools into a single, battery-friendly interface to help you park, level, and plan your stay.

## ✨ Features

*   **📏 Leveling Tool:** Precise 2-axis bubble level with visual guides. Calibrate it to your vehicle's unique floor or counter tilt.
*   **🧭 Compass:** Smooth, filtered compass heading.
*   **🌤️ Weather Forecast:**
    *   Instant current conditions.
    *   **Rolling 24-hour forecast** for temperature (min/max), wind gusts, and precipitation.
    *   Detailed "Extra Data" view with Sunrise/Sunset times, Sunshine duration, and Cloud cover.
    *   Powered by [Open-Meteo](https://open-meteo.com/).
*   **🔦 Flashlight:** Quick access to the camera LED with adjustable brightness (Android 13+).
*   **🔴 Night Mode:** Preserves your night vision with a red-light interface and dimmed screen.
*   **📷 Bump Compensation:** Account for your phone's camera bump to get a perfectly flat reading.

## 📱 Screenshots

*(Add screenshots here)*

## 🛠️ Tech Stack

*   **Language:** Java
*   **Minimum SDK:** 21 (Android 5.0)
*   **Target SDK:** 35 (Android 15)
*   **Architecture:** Native Android Activity-based.
*   **APIs:**
    *   **Location:** Google Play Services (FusedLocationProvider) for accurate weather/elevation.
    *   **Weather:** Open-Meteo API (No API key required).
    *   **Sensors:** Accelerometer & Magnetometer (with low-pass filtering).

## 🚀 Getting Started

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/becste/CamperTools.git
    ```
2.  **Open in Android Studio.**
3.  **Build and Run:**
    ```bash
    ./gradlew assembleDebug
    ```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is open-source.

---
*Weather data provided by [Open-Meteo.com](https://open-meteo.com/)*
