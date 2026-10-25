# Camera and barcodes

Two capabilities give a plugin the device camera. `barcode` opens the Kiosk Satellite scanner and returns the code it reads, so a plugin can scan a product or a QR code without bundling a decoder. `camera` delivers raw frames for plugins that look at the picture themselves, such as a custom detector or a viewfinder of their own.

Both share the camera with motion detection, face detection, hand gestures and the RTSP stream instead of fighting them for it. Both need the camera turned on in **Settings > Camera**.

```json
"capabilities": ["barcode", "camera"]
```

Declare only the one the plugin uses. Neither requires another capability. Kiosk Satellite versions without them refuse to install a plugin that declares either.

## Scan a code

`host.scanBarcode(options, callback)` shows the scanner over whatever is on screen, the screensaver and settings included. It wakes the screen first. The callback runs once, when the scanner reads a code or closes.

```java
Map<String, Object> options = new HashMap<>();
options.put("formats", Arrays.asList("ean_13", "qr_code"));
options.put("timeoutSeconds", 30);
options.put("title", "Scan a product");
host.scanBarcode(options, (ok, data, error) -> {
    if (ok) {
        Map<?, ?> code = (Map<?, ?>) data;
        host.publishTextSensor("last_code", "Last code", (String) code.get("value"));
    } else if (!"canceled".equals(error)) {
        host.log("Scan ended: " + error);
    }
});
```

| Option | Contract |
| --- | --- |
| `formats` | A list from `qr_code`, `aztec`, `data_matrix`, `pdf417`, `ean_13`, `ean_8`, `upc_a`, `upc_e`, `code_128`, `code_39`, `code_93`, `codabar` and `itf`. Every format when left out. Fewer formats read faster, but the scanner ignores a code in a format the list leaves out and keeps waiting |
| `facing` | `default` for the lens picked in the Camera settings, `front` or `back`. The person can flip the camera in the scanner. A device with one camera uses it whatever this says |
| `timeoutSeconds` | 5 to 120. 30 when left out |
| `title` | Up to 80 characters. "Scan a code" when left out |
| `message` | Up to 200 characters under the title. "Hold the code inside the frame." when left out |

The scanner always names the plugin that opened it. The person can close it, flip the camera and turn on the flashlight where the device has one. Back closes it too. The screen light button turns the screen white around a smaller preview at full brightness to light the code for a front camera. The kiosk remembers it for the next scan, whichever plugin opens the scanner.

On success `data` is `{value, format}`. `value` is the code's text as read and `format` is one of the format names above. On failure `error` and `data.code` are the same code:

| Code | Meaning |
| --- | --- |
| `canceled` | The person closed the scanner, or the plugin called `cancelBarcodeScan()` |
| `timeout` | Nothing was read in time |
| `busy` | Another scan is open. One plugin scans at a time |
| `disabled` | The camera is off in the Camera settings |
| `permission` | The person did not give Kiosk Satellite the camera permission |
| `no_camera` | The device has no camera, or the plugin runs headless |
| `camera_failed` | The camera did not start |
| `unavailable` | The kiosk screen is not showing, for example during the setup wizard |

`host.cancelBarcodeScan()` closes this plugin's scanner. Disabling the plugin closes it too, and its callback never runs.

While the scanner is open, motion detection, face detection, hand gestures and the RTSP stream pause and camera snapshots fail. They come back when it closes. A plugin holding the camera through `openCamera` loses it with `busy`.

### Start a scan from Home Assistant

Declare a command that scans and expose it as an ESPHome button from the plugin's subpage. Then a Home Assistant automation presses the button and listens for the result, which the plugin publishes as a text sensor, fires as an event through the `ha` capability, or both. Hello World does both.

## Camera frames

`host.openCamera(options, listener)` delivers frames from the device camera until the plugin closes it or the time is up.

```java
Map<String, Object> options = new HashMap<>();
options.put("size", "640x480");
options.put("fps", 5);
options.put("timeoutSeconds", 60);
host.openCamera(options, new PluginHost.CameraListener() {
    @Override public void onFrame(CameraFrame frame) {
        byte[] luminance = frame.data();
        // Look at the picture here.
    }
    @Override public void onClosed(String reason) {
        host.log("Camera closed: " + reason);
    }
});
```

| Option | Contract |
| --- | --- |
| `size` | `320x240`, `640x480` or `1280x720`. 640x480 when left out. Larger costs more on slow devices |
| `fps` | 1 to 15. 5 when left out |
| `facing` | `default` for the lens picked in the Camera settings, `front` or `back`. Motion detection and the other camera features use the same lens while the plugin holds the camera |
| `format` | `luminance` or `nv21`. `luminance` when left out |
| `timeoutSeconds` | 5 to 600. 60 when left out. The camera closes when the time is up |

Call `openCamera` again to change the options or to start the time over. That replaces the plugin's own claim without closing it. Only one plugin holds the camera at a time. While another one does, the listener hears `onClosed("busy")` right away.

Frames arrive one at a time on a camera thread that belongs to the plugin. A frame that arrives while `onFrame` still runs is skipped, so slow work lowers the rate and never queues up. An exception thrown from either listener method stops the plugin like any failed callback. Frames pause during a voice interaction.

| `CameraFrame` | Contract |
| --- | --- |
| `width()`, `height()` | The frame as the camera delivered it, in sensor orientation. It can differ from the size asked for |
| `rotationDegrees()` | Clockwise degrees that turn the frame upright on this display: 0, 90, 180 or 270 |
| `frontFacing()` | True for a camera that faces the user. Flip its picture to show it like a mirror |
| `timestampNanos()` | The capture time, in nanoseconds from an unspecified starting point |
| `format()` | `luminance` or `nv21`, as asked for |
| `data()` | A copy the plugin owns. `luminance` is width times height bytes, one brightness value per pixel, row by row. `nv21` adds interleaved V and U bytes at half the width and half the height |

`host.closeCamera()` stops the frames and the listener hears `onClosed("closed")`. The other reasons are `timeout`, `busy`, `disabled`, `permission`, `no_camera` and `camera_failed`. After `camera_failed` the plugin can open the camera again. Disabling the plugin closes the camera without calling the listener.

The camera stays open while the plugin holds it, screen off included, but many Android versions stop the camera while the screen is off. The listener then hears `camera_failed`.

## Try it in Hello World

**Scan a code** opens the scanner for every kind of barcode and QR code. The code and its format show under **Last code** and **Last code format** in the readings. Home Assistant hears a `hello_world_barcode` event with `value` and `format`. **Show camera preview** opens the camera for 30 seconds and draws its `nv21` frames in color in a small card at the bottom right, with **Camera brightness** in the readings. **Hide camera preview** and back close it. See [BarcodeDemo.java](../src/me/jxl/kiosk/plugins/hello/BarcodeDemo.java) and [CameraDemo.java](../src/me/jxl/kiosk/plugins/hello/CameraDemo.java).
