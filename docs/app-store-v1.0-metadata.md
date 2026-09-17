# App Store v1.0 metadata

> **Status:** Apple approved version 1.0 on September 17, 2026. Build 9 is
> selected and valid. Manual release is enabled, and App Store Connect reports
> `PENDING_DEVELOPER_RELEASE`. The app is not publicly available until the
> developer release occurs and Apple publishes the listing.

Use this file as the record of the approved version 1.0 product copy and review
material. Keep protected contact and connection values as placeholders here.
Enter verified values only in App Store Connect. Do not add passwords, private
keys, recovery codes, or other credentials to this file.

## Product page copy

### Name

```text
KeyJawn
```

### Subtitle

```text
Remote terminal and keyboard
```

### Promotional text

```text
Connect to a remote SSH server, work in a terminal, and use a keyboard for terminal-oriented controls and text shortcuts where iOS supports third-party keyboards.
```

### Description

```text
KeyJawn is an SSH client and companion custom keyboard for iPhone and iPad. It is made for people who work with terminal-based tools from a mobile device.

Remote SSH terminal

Connect to a server that you configure. Commands execute on that remote server. KeyJawn sends terminal input over SSH and displays the output returned by the server. It does not provide a local iOS shell or browse files on the iPhone or iPad.

Optional voice input is available in the built-in SSH terminal. When you tap Mic, KeyJawn asks for microphone and speech-recognition permission. Apple's Speech framework can process the audio. KeyJawn does not store audio recordings or send them to a server operated by the developer. The keyboard extension does not use the microphone.

Custom keyboard

Use QWERTY input, terminal-oriented controls, and text shortcuts in apps and text fields that support third-party keyboards. The extension inserts text or control sequences into the focused field. The receiving app decides how to interpret them. The built-in SSH terminal sends terminal bytes directly. Basic typing and built-in text shortcuts work without Allow Full Access. User-created shortcuts require Full Access because the app and extension share them through the App Group.

iOS controls where a custom keyboard can appear. The system keyboard appears for passcodes, secure text fields, and phone-pad fields. An app can also block third-party keyboards.

Optional copied-image upload

Copy an image, then upload it through SFTP to a directory on a remote host that you configure for SSH key authentication. KeyJawn reads the copied image from the system pasteboard, prepares it in memory, and writes a new remote file. The copied-image upload feature does not browse local device files or list, read, or download remote files. Password authentication remains available for terminal connections.

Privacy and control

SSH identity data is stored in the Keychain. Host settings and keyboard preferences are stored in the app's containers. KeyJawn does not provide a local command runtime.
```

### Keywords

```text
ssh,terminal,remote,server,keyboard,cli,developer,commands,esc,tab,ctrl
```

### URLs

```text
Support URL: https://keyjawn.amditis.tech/support
Marketing URL: https://keyjawn.amditis.tech
Privacy policy URL: https://keyjawn.amditis.tech/privacy
```

### Copyright

```text
[copyright year] [legal owner name]
```

## App Review information

### Contact fields

```text
First name: [review contact first name]
Last name: [review contact last name]
Phone number: [review contact phone number]
Email: [review contact email]
```

### Sign-in information

Enter review credentials only in the protected App Store Connect fields. Do
not put them in this repository.

```text
Sign-in required: Yes
Username: [isolated review username; enter only in App Store Connect]
Password: [isolated review password; enter only in App Store Connect]
```

### Review notes

This is the credential-free basis of the review notes submitted with build 9.
The bracketed connection values remain placeholders because the protected
values belong only in App Store Connect.

```text
KeyJawn is a remote SSH client. It does not execute commands locally on iOS. Terminal input is sent over SSH to a server configured by the user. That server executes the command, and KeyJawn displays the output returned over the network.

The Mic action is available only in the built-in SSH terminal. It requests microphone and speech-recognition permission when the reviewer taps it. Apple's Speech framework can process the captured audio. KeyJawn does not store audio recordings or send them to a server operated by the developer. The keyboard extension does not use the microphone.

KeyJawn has no local shell, local command interpreter, document picker, document browser, or file provider. The copied-image upload reads one image that the user has placed on the system pasteboard, prepares it in memory, and writes a new file through SFTP to a configured directory on the remote SSH host. The copied-image upload feature cannot browse local device files or list, read, or download remote files.

The companion keyboard can type and insert built-in text shortcuts without Allow Full Access. User-created shortcuts require Full Access because the app and extension share them through the App Group. The extension inserts text or control sequences into the focused field, and the receiving app decides how to interpret them. The built-in SSH terminal sends terminal bytes directly. Full Access is optional. KeyJawn uses it only for copied-image upload and shared keyboard settings, user-created shortcuts, or clipboard history. Copied-image upload also requires a host configured for SSH key authentication. iOS uses the system keyboard for passcodes, secure text fields, and fields that use the phonePad or namePhonePad keyboard type. Apps can also block third-party keyboards.

Review connection:
Host: [review host]
Port: [review port]
Username: [review username]
Transport: Enable TLS tunnel
Authentication: Password for the initial terminal connection; SSH key after the fresh install's public key is authorized
Expected host-key fingerprint: [fingerprint]
Writable remote directory: [remote directory]

Test steps:
1. Launch KeyJawn and complete or skip onboarding.
2. Open Settings > SSH keys and tap Copy public key. This is the public key generated by this fresh installation. Do not send or copy the private key.
3. Add the review host with the values above, enable TLS tunnel, and select Password authentication.
4. Connect with the protected review password. Confirm that the displayed host-key fingerprint matches the value above.
5. Enter: printf 'keyjawn-review\n'
6. Confirm that the remote host returns keyjawn-review in the terminal.
7. Tap Mic. Allow speech recognition and microphone access if iOS asks. Speak a short phrase, tap Mic again, and confirm that the transcription appears in the terminal input before you send it.
8. To authorize this installation for the optional upload test, enter this prefix without submitting it: mkdir -p ~/.ssh && chmod 700 ~/.ssh && printf '%s\n' '
9. Tap Clip and select the public key copied in step 2. Then enter this suffix and submit the complete command: ' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys
10. Disconnect, edit the review host, and select SSH key authentication.
11. In Copied-image upload, set Upload path to the writable remote directory above, save the host, and reconnect.
12. Copy a small image, tap the SCP key to open Upload copied image, select the review host, and confirm that KeyJawn inserts a new path in the writable remote directory.
13. To test the keyboard, enable KeyJawn in Settings > General > Keyboard > Keyboards, then select it in a text field that supports third-party keyboards. Basic typing and built-in shortcuts work without Allow Full Access. User-created shortcuts, shared clipboard history, and copied-image upload require Full Access. The extension inserts text or control sequences; the receiving app decides how to interpret them.
```

## Reviewer response record

The approved response was included in the App Review notes with the connection
details and test steps. No separate Resolution Center message was sent because
the public App Store Connect API does not provide that message surface.

```text
Hello App Review,

We addressed the guideline 2.5.2 concern in build 9.

KeyJawn does not execute commands locally on iOS. Its terminal sends input over SSH to a remote server configured by the user. The remote server executes the command, and KeyJawn displays the output returned over the network. We removed the session-free Preview terminal so this boundary is clear before a host exists.

KeyJawn does not provide a local file browser, document picker, file provider, or security-scoped file access. Copied-image upload reads one image that the user placed on the system pasteboard, prepares it in memory, and writes a new file through SFTP to a configured directory on the remote SSH host. The copied-image upload feature cannot browse local device files or list, read, or download remote files.

Current SSH identity data is stored in the Keychain. The only direct production file read is a fixed legacy SSH identity file inside KeyJawn's entitled App Group container. This read exists only to migrate older installs to the shared Keychain. KeyJawn deletes the legacy file only after it verifies the Keychain write.

Voice input is available only in the built-in SSH terminal. It requests microphone and speech-recognition permission after the user taps Mic. Apple's Speech framework can process the captured audio. KeyJawn does not store audio recordings or send them to a server operated by the developer. The keyboard extension does not use the microphone.

The keyboard can type and insert built-in text shortcuts without Allow Full Access. User-created shortcuts require Full Access because the app and extension share them through the App Group. It inserts text or control sequences into the focused field; the receiving app decides how to interpret them. The built-in SSH terminal sends terminal bytes directly. Full Access is optional and is used only for copied-image upload and shared keyboard settings, user-created shortcuts, or clipboard history. Copied-image upload requires a host configured for SSH key authentication; password authentication remains available for terminal connections.

Review connection details and test steps are in the App Review notes for this build.

Thank you.
```

## Screenshot captions

```text
Commands run on the remote SSH server you configure
Basic typing and built-in shortcuts work without Full Access
Add the remote SSH hosts you control
Configure keyboard and SSH settings
Use KeyJawn as a companion keyboard in supported text fields
```

## Submission checks

- Confirm that every placeholder has a verified value.
- Confirm that review credentials exist only in App Store Connect.
- Confirm that the selected build number matches both signed targets.
- Confirm that screenshots show the current build and no third-party branding.
- Confirm that the privacy answers match the signed app and extension, including
  the built-in terminal's optional use of Apple's Speech framework. Apple says
  developers do not report data collected only by Apple through Apple
  frameworks or services; verify the current answers against Apple's current
  App Privacy questions instead of assuming that Apple-only processing is
  developer collection.
- Confirm the Mic permission and transcription steps on reviewer-class hardware.
- Get separate user approval for metadata changes, the reviewer response, and
  resubmission.
