# KogSense

**KogSense** is an extension for the Hammerhead Karoo that beeps when your electronic drivetrain hits its mechanical limits. It automatically supports both **SRAM AXS** and **Shimano Di2** (Note: Di2 support requires the [Ki2 extension](https://github.com/valterc/ki2) to be installed).

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/jcro001)

## Features

- **End-of-Range Alerts**: Automatic beeps when you reach the first or last available gear in your cassette.
- **Synchro & Sequential Shift Warnings**: Pre-shift warning beeps when entering a boundary gear before an automated synchronized front shift. Uses intuitive directional tones (ascending tone for UP shifts, descending tone for DOWN shifts).
- **Smart Auto-Learning & Negative Evidence**: Automatically detects your drivetrain's synchro boundary cogs as you ride, filtering out false positives from manual front ring operations using negative evidence logic.
- **Multi-Bike Support**: Per-sensor configuration isolation automatically remembers settings, cassette sizes, and synchro boundaries independently for each connected bike.
- **In-Ride Notifications**: Dispatches native Karoo system notifications and on-screen alerts whenever a new synchro boundary cog is auto-learned.
- **Cross-Chain Blocked Gear Support**: Correctly handles systems that block certain gear combinations to prevent cross-chaining (e.g. SRAM 2x locked small-small combos).
- **Tested & Verified**: Fully tested and verified on **SRAM AXS 2x12** and **Shimano Di2 2x12** drivetrains.
- **Smart Detection**: Automatically detects your drivetrain brand and hardware configuration without manual setup.

## Installation

### Karoo 3
1. Download the latest release from the [Releases](https://github.com/jcro001/KogSense/releases) page.
2. Sideload the APK onto your Hammerhead Karoo. You can do this easily by sharing the download link via the [Hammerhead Companion App](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading).
3. Open the **KogSense** app on your Karoo to configure your alerts.
4. Ensure the extension is enabled in your Karoo settings.

### Karoo 2
1. Download the latest release from the [Releases](https://github.com/jcro001/KogSense/releases) page.
2. Set up your Karoo for sideloading. DC Rainmaker has a great [step-by-step guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).
3. Open the **KogSense** app on your Karoo and ensure the extension is enabled.

## Configuration

Note that the extension will work automatically without any setting changes. However, you can adjust the following within the KogSense app on a per-bike basis:
- **Alert Toggles**: Enable or disable Low Gear, High Gear, and Synchro/Sequential Shift alerts independently per bike.
- **Synchro Shift Boundaries**: Set Shift Up and Shift Down boundary cogs to "Auto" (auto-detects as you ride), "Off", or lock to a specific manual cog.
- **Cassette Size**: Manually set your cassette size (10S-13S) or let the app detect it automatically.
- **Drivetrain Mode**: Force SRAM/Shimano modes or use "Auto" detection.

## Support

If you find this extension useful, consider supporting the development:

[![Support me on Ko-fi](https://storage.ko-fi.com/cdn/cup-border.png)](https://ko-fi.com/jcro001)

## License

This project is licensed under the Apache License 2.0.
