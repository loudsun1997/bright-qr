# Bright QR for iOS

This directory contains the iOS 16+ proof of concept for localized maximum
brightness. The ordinary SwiftUI interface stays in SDR while only the test region
uses Apple's Extended Dynamic Range rendering path.

The app never assigns `UIScreen.brightness`. Its maximum-output policy is:

```text
QR black  = 0.0
QR white  = max(1.0, associatedScreen.potentialEDRHeadroom)
iOS       = clips that value to currently available physical output
```

Headroom is a ratio relative to SDR reference white, not a measured-nits value.

## First physical-device test

The first target device is an iPhone 13 running iOS 16 or newer.

1. Install current Xcode and open `BrightQRiOS.xcodeproj`.
2. Select the **BrightQRiOS** target and choose your Apple development team.
3. Connect and trust the iPhone, select it as the run destination, and press Run.
4. Set the physical screen brightness near 10%.
5. For the controlled baseline, turn off Low Power Mode, True Tone, Night Shift,
   and Auto-Brightness, and begin with a cool device.
6. Select **White** and compare ordinary SwiftUI white with maximum EDR white.
7. Continue to **Checker** only if EDR white is visibly brighter while the rest of
   the interface remains dim.
8. Continue to **QR** and scan the movie-ticket code with a second phone.

Screenshots cannot validate emitted luminance. Judge this effect on the physical
display or with luminance-measurement equipment.

## Test modes

| Mode | Purpose |
| --- | --- |
| White | Proves that a localized EDR layer can exceed nearby SDR white |
| Checker | Proves simultaneous absolute black and maximum-EDR white with sharp edges |
| QR | Renders a real module matrix with integer pixels and a four-module EDR quiet zone |

The diagnostics panel updates four times per second and reports the actual window's
screen brightness, current/potential EDR headroom, renderer configuration, module
geometry, Low Power Mode, and thermal state.

## Rendering architecture

```text
SwiftUI SDR screen
└── EDRMetalView
    └── MTKView / CAMetalLayer
        ├── rgba16Float
        ├── extendedLinearDisplayP3
        ├── wantsExtendedDynamicRangeContent = true
        └── preferredDynamicRange = .high on iOS 26+
```

`QRGenerator` uses Apple's Core Image QR encoder to obtain a one-pixel-per-module
binary matrix. It does not pass an SDR QR image to Metal. `EDRRenderer` uploads the
binary modules, and `QRShaders.metal` makes the final per-pixel decision between
black and the display's potential EDR headroom.

The QR renderer:

- calculates physical pixels per module with integer division;
- centers an integer-sized render square inside the drawable;
- reads integer module coordinates directly in the fragment shader;
- uses no texture sampling, interpolation, or antialiasing;
- draws only on content, size, lifecycle, or capability changes; and
- falls back to an ordinary SDR pattern if Metal initialization fails.

## Reproducing the project

The generated Xcode project is committed for convenience. To regenerate it after
changing `project.yml`:

```shell
brew install xcodegen
cd ios
xcodegen generate --spec project.yml
```

Command-line simulator build:

```shell
xcodebuild \
  -project ios/BrightQRiOS.xcodeproj \
  -scheme BrightQRiOS \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The repository's iOS GitHub Actions workflow runs this build on every iOS pull
request. A simulator can validate compilation and SDR fallback behavior, but only
a compatible physical display can validate localized EDR luminance.

## Source layout

```text
BrightQRiOS/
├── App/       SwiftUI application, movie pass, modes, and report copy
├── Debug/     live device and renderer diagnostics
├── EDR/       Metal layer, renderer, uniforms, and shader
└── QR/        encoder-independent matrix and pixel-perfect layout
```

## Current status

Implemented milestones:

- M0: complete Xcode application project
- M1: window-associated live EDR diagnostics
- M2: localized EDR white
- M3: binary EDR checkerboard
- M4: real QR matrix rendered by Metal
- M5: integer physical pixels per module and four-module quiet zone

Physical iPhone 13 validation is still required before claiming the concept works
on iOS. M6–M8 scanner benchmarks, lifecycle/thermal stress testing, and broader
device testing follow that first validation.

## Apple references

- [Displaying HDR content in a Metal layer](https://developer.apple.com/documentation/metal/drawing-hdr-content-in-a-metal-layer)
- [`UIScreen.currentEDRHeadroom`](https://developer.apple.com/documentation/uikit/uiscreen/currentedrheadroom)
- [`UIScreen.potentialEDRHeadroom`](https://developer.apple.com/documentation/uikit/uiscreen/potentialedrheadroom)
- [`CAMetalLayer.wantsExtendedDynamicRangeContent`](https://developer.apple.com/documentation/quartzcore/cametallayer/wantsextendeddynamicrangecontent)
- [`CALayer.preferredDynamicRange`](https://developer.apple.com/documentation/quartzcore/calayer/preferreddynamicrange)
