# KeyJawn implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an Android IME keyboard with a terminal key row, basic QWERTY, and SCP image upload for LLM CLI users.

**Architecture:** A Kotlin Android app that subclasses `InputMethodService`. The keyboard renders an extra row of terminal keys (Esc, Tab, Ctrl, arrows, upload) above a custom QWERTY layout. Key events are sent via `InputConnection`. Image upload uses JSch for SCP. Per-app autocorrect settings stored in SharedPreferences.

**Tech Stack:** Kotlin, Android SDK (min API 26), Gradle (Kotlin DSL), JSch (SCP), EncryptedSharedPreferences, JUnit 4 + Mockito + Robolectric for testing.

---

## Build environment

Android SDK command-line tools are x86_64-only on Linux. Two options:

1. **GitHub Actions CI** (recommended) — build APKs in CI, download to phone
2. **legion2025** — Windows machine with Android Studio, build locally

The Pi (houseofjawn) is the code editing environment. Builds happen elsewhere via CI.

---

## Task 1: Gradle project scaffolding

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/xml/method.xml`

**Step 1: Create root build.gradle.kts**

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}
```

**Step 2: Create settings.gradle.kts**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KeyJawn"
include(":app")
```

**Step 3: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

**Step 4: Create app/build.gradle.kts**

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.keyjawn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.keyjawn"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.github.mwiede:jsch:0.2.21")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
```

**Step 5: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.KeyJawn">

        <service
            android:name=".KeyJawnService"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_INPUT_METHOD"
            android:exported="true">
            <intent-filter>
                <action android:name="android.view.InputMethod" />
            </intent-filter>
            <meta-data
                android:name="android.view.im"
                android:resource="@xml/method" />
        </service>

        <activity
            android:name=".SettingsActivity"
            android:label="@string/settings_title"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Step 6: Create method.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/xml/method.xml -->
<input-method xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.keyjawn.SettingsActivity"
    android:isDefault="false">
    <subtype
        android:imeSubtypeMode="keyboard"
        android:imeSubtypeLocale="en_US"
        android:label="@string/subtype_en_us" />
</input-method>
```

**Step 7: Create strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/values/strings.xml -->
<resources>
    <string name="app_name">KeyJawn</string>
    <string name="settings_title">KeyJawn settings</string>
    <string name="subtype_en_us">English (US)</string>
</resources>
```

**Step 8: Commit**

```bash
git add app/ build.gradle.kts settings.gradle.kts gradle.properties
git commit -m "feat: add Gradle project scaffolding and Android manifest"
```

---

## Task 2: Basic IME service

**Files:**
- Create: `app/src/main/java/com/keyjawn/KeyJawnService.kt`
- Create: `app/src/main/res/layout/keyboard_view.xml`
- Create: `app/src/main/res/layout/extra_row.xml`
- Test: `app/src/test/java/com/keyjawn/KeyJawnServiceTest.kt`

**Step 1: Write failing test**

```kotlin
// app/src/test/java/com/keyjawn/KeyJawnServiceTest.kt
package com.keyjawn

import org.junit.Test
import org.junit.Assert.*

class KeyJawnServiceTest {

    @Test
    fun `service class exists`() {
        val service = KeyJawnService()
        assertNotNull(service)
    }
}
```

**Step 2: Run test — expected FAIL (class not found)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.keyjawn.KeyJawnServiceTest"`

**Step 3: Create KeyJawnService**

```kotlin
// app/src/main/java/com/keyjawn/KeyJawnService.kt
package com.keyjawn

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View

class KeyJawnService : InputMethodService() {

    override fun onCreateInputView(): View {
        return LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
    }
}
```

**Step 4: Create keyboard_view.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/layout/keyboard_view.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="#1E1E1E">

    <include layout="@layout/extra_row" />

    <LinearLayout
        android:id="@+id/qwerty_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:background="#2D2D2D" />

</LinearLayout>
```

**Step 5: Create extra_row.xml placeholder**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/layout/extra_row.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/extra_row"
    android:layout_width="match_parent"
    android:layout_height="42dp"
    android:orientation="horizontal"
    android:background="#181818" />
```

**Step 6: Run test — expected PASS**

**Step 7: Commit**

```bash
git add app/src/
git commit -m "feat: add basic IME service with empty keyboard layout"
```

---

## Task 3: KeySender utility

**Files:**
- Create: `app/src/main/java/com/keyjawn/KeySender.kt`
- Test: `app/src/test/java/com/keyjawn/KeySenderTest.kt`

**Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/keyjawn/KeySenderTest.kt
package com.keyjawn

import android.view.KeyEvent
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.*

class KeySenderTest {

    @Test
    fun `sendKey sends down and up events`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        whenever(ic.sendKeyEvent(any())).thenReturn(true)

        val sender = KeySender()
        sender.sendKey(ic, KeyEvent.KEYCODE_TAB)

        val captor = argumentCaptor<KeyEvent>()
        verify(ic, times(2)).sendKeyEvent(captor.capture())
        assertEquals(KeyEvent.ACTION_DOWN, captor.firstValue.action)
        assertEquals(KeyEvent.ACTION_UP, captor.secondValue.action)
    }

    @Test
    fun `sendKey with ctrl sets meta state`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        whenever(ic.sendKeyEvent(any())).thenReturn(true)

        val sender = KeySender()
        sender.sendKey(ic, KeyEvent.KEYCODE_C, ctrl = true)

        verify(ic).sendKeyEvent(argThat {
            action == KeyEvent.ACTION_DOWN && metaState and KeyEvent.META_CTRL_ON != 0
        })
    }

    @Test
    fun `sendText commits text`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        whenever(ic.commitText(any(), any())).thenReturn(true)

        val sender = KeySender()
        sender.sendText(ic, "hello")

        verify(ic).commitText("hello", 1)
    }
}
```

**Step 2: Run tests — expected FAIL**

**Step 3: Implement KeySender**

```kotlin
// app/src/main/java/com/keyjawn/KeySender.kt
package com.keyjawn

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class KeySender {

    fun sendKey(ic: InputConnection, keyCode: Int, ctrl: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        var metaState = 0
        if (ctrl) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    fun sendText(ic: InputConnection, text: String) {
        ic.commitText(text, 1)
    }
}
```

**Step 4: Run tests — expected PASS**

**Step 5: Commit**

```bash
git add app/src/
git commit -m "feat: add KeySender utility for key events and text input"
```

---

## Task 4: Extra row with terminal keys

**Files:**
- Create: `app/src/main/java/com/keyjawn/ExtraRowConfig.kt`
- Create: `app/src/main/java/com/keyjawn/RepeatTouchListener.kt`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/res/drawable/key_bg.xml`
- Modify: `app/src/main/res/layout/extra_row.xml`
- Modify: `app/src/main/java/com/keyjawn/KeyJawnService.kt`
- Test: `app/src/test/java/com/keyjawn/ExtraRowTest.kt`

Full implementation as described above in the design presentation. Key handler wiring, Ctrl toggle/lock state, RepeatTouchListener for arrow key hold-to-repeat.

**Commit:**

```bash
git add app/src/
git commit -m "feat: add terminal extra row with Ctrl modifier and arrow key repeat"
```

---

## Task 5: QWERTY keyboard with 3 layers

**Files:**
- Create: `app/src/main/java/com/keyjawn/QwertyLayout.kt`
- Create: `app/src/main/java/com/keyjawn/QwertyKeyboard.kt`
- Modify: `app/src/main/java/com/keyjawn/KeyJawnService.kt`
- Test: `app/src/test/java/com/keyjawn/QwertyKeyboardTest.kt`

Layout data, dynamic view rendering, shift/capslock toggle, sym/abc layer switch, special key handling (backspace, enter, space). Ctrl combos delegated to KeySender.

**Commit:**

```bash
git add app/src/
git commit -m "feat: add QWERTY keyboard with lowercase, uppercase, and symbols layers"
```

---

## Task 6: Per-app autocorrect toggle

**Files:**
- Create: `app/src/main/java/com/keyjawn/AppPrefs.kt`
- Modify: `app/src/main/java/com/keyjawn/QwertyKeyboard.kt`
- Modify: `app/src/main/java/com/keyjawn/KeyJawnService.kt`
- Test: `app/src/test/java/com/keyjawn/AppPrefsTest.kt`

SharedPreferences-backed per-app autocorrect preference. Off by default. Long-press spacebar toggles. AC badge on spacebar.

**Commit:**

```bash
git add app/src/
git commit -m "feat: add per-app autocorrect toggle via long-press spacebar"
```

---

## Task 7: Slash command quick-insert

**Files:**
- Create: `app/src/main/java/com/keyjawn/SlashCommandRegistry.kt`
- Create: `app/src/main/java/com/keyjawn/SlashCommandPopup.kt`
- Create: `app/src/main/res/layout/slash_command_popup.xml`
- Create: `app/src/main/res/layout/slash_command_item.xml`
- Create: `app/src/main/assets/commands.json`
- Modify: `app/src/main/java/com/keyjawn/QwertyKeyboard.kt`
- Test: `app/src/test/java/com/keyjawn/SlashCommandRegistryTest.kt`

Bundled JSON command registry with Claude Code, Aider, and general command sets. `/` key in bottom row triggers popup. MRU ordering. User-customizable via settings.

**Default commands.json:**

```json
{
  "claude_code": [
    "/help", "/clear", "/commit", "/review-pr", "/compact", "/cost", "/doctor"
  ],
  "aider": [
    "/add", "/drop", "/run", "/test", "/diff", "/undo", "/commit"
  ],
  "general": [
    "/exit", "/quit", "/help"
  ]
}
```

**Commit:**

```bash
git add app/src/ app/src/main/assets/
git commit -m "feat: add slash command quick-insert popup with bundled command sets"
```

---

## Task 8: Image upload via SCP

**Files:**
- Create: `app/src/main/java/com/keyjawn/HostConfig.kt`
- Create: `app/src/main/java/com/keyjawn/ScpUploader.kt`
- Modify: `app/src/main/java/com/keyjawn/KeyJawnService.kt`
- Test: `app/src/test/java/com/keyjawn/HostConfigTest.kt`

JSch-based SCP upload. Android photo picker integration. Toast confirmation. Path insertion at cursor.

**Commit:**

```bash
git add app/src/
git commit -m "feat: add SCP image upload with path insertion"
```

---

## Task 9: Settings activity

**Files:**
- Create: `app/src/main/java/com/keyjawn/SettingsActivity.kt`
- Create: `app/src/main/res/layout/activity_settings.xml`

Host list management (add/edit/delete), SSH key generation (ed25519), active host selector, EncryptedSharedPreferences for credentials.

**Commit:**

```bash
git add app/src/
git commit -m "feat: add settings activity with host management"
```

---

## Task 10: GitHub Actions CI

**Files:**
- Create: `.github/workflows/build.yml`

Build + test workflow on push/PR. Upload debug APK as artifact.

**Commit:**

```bash
git add .github/
git commit -m "ci: add GitHub Actions build and test workflow"
```

---

## Task 11: Integration test and release

1. Download debug APK from CI artifacts
2. Install on S24 Ultra
3. Enable in Settings > System > Keyboard
4. Test all keys in Cockpit terminal
5. Test image upload
6. Tag v0.1.0 release
