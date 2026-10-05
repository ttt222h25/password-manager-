# Vault: an offline password manager for Android

A small private password manager that runs only on your phone. Logins are kept in **folders**
(Steam, Discord, Gmail, Microsoft, Other, plus any folders you add), each shown with its logo. Tap a
folder to see every account in it, with its username and password.

- **Fully offline.** The app does not request the Internet permission, so Android blocks it from
  going online at all.
- **Opens with your phone's own lock.** Vault asks for the fingerprint, face, PIN or pattern you
  already use to unlock your phone. There's no extra password to remember.
- **Encrypted.** Everything is saved in one file, encrypted with AES-256-GCM. The key is kept in the
  Android Keystore (the phone's secure hardware) and can't be copied off the phone.
- **Locks automatically** whenever you leave the app.
- **No screenshots.** The screen also shows up blank in the recent-apps view.
- **Clipboard is cleared** 30 seconds after you copy a username or password.
- **No cloud backup.** Android's automatic Google backup is turned off for this app. To keep a copy,
  use *Export backup*, which saves a password-protected file.

## Install it on your phone

1. Open this repository's **Releases** page on your phone (the link is on the right side of the repo
   page) and download **Vault.apk** from the newest release.
   *If there's no release yet:* go to **Actions → Build APK**, open the newest green run and download
   the **Vault-apk** artifact. You'll get a zip file; unzip it to get `Vault.apk`.
2. Open `Vault.apk`. Android will ask you to allow installing apps from your browser or file manager.
   Allow it, then tap **Install**.
3. Open **Vault** and unlock it with your fingerprint, face, PIN or pattern. If your phone has no screen
   lock, Vault opens without one and shows a warning. Set one in Android Settings → Security.

## Using it

| To do this | Do this |
| --- | --- |
| Add an account | Tap a folder (e.g. **Steam**), then **+**. Fill in a name (e.g. *Main*, *Alt*), the username or email, and the password. |
| See a password | Tap the 👁 eye icon on the account. |
| Copy a username or password | Tap the copy icon next to it. |
| Edit or delete an account | Tap the account. |
| Make a new folder | On the home screen tap **New folder** (e.g. *Epic Games*, *Instagram*). Well-known names get their logo automatically; other folders get a coloured letter. |
| Rename or delete a folder | Open the folder and use the ✏️ or 🗑 icons at the top. |
| Find an account fast | Use the search box on the home screen. |
| Make a strong password | Tap **Generate strong password** when editing an account. |

## Back up your passwords

Your passwords exist **only on this phone**. If you lose the phone, reset it or uninstall the app,
they're gone, unless you have a backup.

- **Settings → Export backup** asks you to choose a **backup password**, then saves a
  `vault-backup-DATE.vault` file locked with it. It's safe to copy to your computer or a USB stick.
  Write the backup password down; without it the backup can't be opened.
- **Settings → Restore from backup** loads it back. On a new phone, install Vault, unlock it, then
  restore. You'll need that backup's password.

## Coming from the first version (with a master password)

1. In the **old** app: Settings → Export backup. That backup is locked with your old master password.
2. Uninstall the old app and install the new APK.
3. Open Vault, then Settings → Restore from backup, and enter your **old master password**.

## Updating the app

Without a fixed signing key (see below), each build is signed with a different temporary key, and
Android won't install it over the old version. To update:
**Export backup → uninstall the old app → install the new APK → Restore from backup**.

### Optional: a fixed signing key so updates install over the old app

On a computer with Java installed:

```sh
keytool -genkeypair -v -keystore vault.jks -alias vault -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 vault.jks > vault.jks.b64   # on macOS: base64 -i vault.jks -o vault.jks.b64
```

Then, in GitHub, go to **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | contents of `vault.jks.b64` |
| `SIGNING_STORE_PASSWORD` | the keystore password you chose |
| `SIGNING_KEY_ALIAS` | `vault` |
| `SIGNING_KEY_PASSWORD` | the key password (same as the keystore password if you didn't set a different one) |

Keep `vault.jks` private, and never commit it. Builds after this are all signed with the same key.
You'll need to do the backup/uninstall/restore steps one last time to switch to the new key.

## For developers

- `core/`: plain Kotlin library with the vault model, encryption (`VaultCrypto`), file storage
  (`VaultStore`) and the password generator. Unit tests: `./gradlew :core:test`.
- `app/`: the Android app (Jetpack Compose, single activity). `DeviceKey` holds the Keystore key,
  `PhoneLock` shows the fingerprint/PIN prompt, `ui/ServiceIcons.kt` maps folder names to logos.
- Logos come from [Simple Icons](https://simpleicons.org) (CC0). They are trademarks of their
  owners and are only used to label your own folders.
- `.github/workflows/build-apk.yml`: runs the tests and builds the release APK on every push. Pushes
  to the default branch also publish a GitHub Release with `Vault.apk` attached.

Build locally with Android Studio, or with `./gradlew :app:assembleRelease` if you have the Android SDK.
