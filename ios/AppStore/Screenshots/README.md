# App Store screenshots

These files are the current English App Store screenshot set for version 1.0.0.
They come from `KeyJawnUITests` on iOS 17.5 simulators. Do not edit or crop them.

## Device sets

- `en-US/iphone-6.9`: 1290 x 2796 pixels from iPhone 15 Pro Max
- `en-US/ipad-13`: 2064 x 2752 pixels from iPad Pro 13-inch (M4)

Each device set contains the same five screens:

1. `01-remote-boundary.jpg`: states that commands run on a remote SSH server
2. `02-full-access.jpg`: explains that basic typing works without Full Access
3. `03-remote-hosts.jpg`: shows the empty remote-host list
4. `04-settings.jpg`: shows keyboard and SSH settings
5. `05-companion-keyboard.jpg`: shows KeyJawn as a system keyboard in a blank host form

The keyboard screenshot uses Full Access off. The tests restore the original simulator keyboard state after capture. No screenshot contains credentials, keys, terminal history, clipboard data, or a real host name.

Before upload, run `sips -g pixelWidth -g pixelHeight -g hasAlpha` on every file. Confirm the listed dimensions and `hasAlpha: no`. Apple upload is a separate release action and needs explicit approval.
