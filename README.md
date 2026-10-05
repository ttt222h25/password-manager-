# Vault: an offline password manager for Android

A small private password manager that runs only on your phone. Logins are kept in **folders**
(Steam, Discord, Gmail, Microsoft, Other, plus any folders you add). Tap a folder to see every account
in it, with its username and password.

- **Fully offline.** The app does not request the Internet permission, so Android blocks it from
  going online at all.
- **Encrypted.** Everything is saved in one file, encrypted with AES-256-GCM. The key comes from your
  master password (PBKDF2-SHA256, 600,000 rounds). The master password itself is never stored.
- **Locks automatically** whenever you leave the app.
- **No screenshots.** The screen also shows up blank in the recent-apps view.
- **Clipboard is cleared** 30 seconds after you copy a username or password.
- **No cloud backup.** Android's automatic Google backup is turned off for this app. To keep a copy,
  use *Export backup*, which saves an encrypted file.

## Install it on your phone

1. Open this repository's **Releases** page on your phone (the link is on the right side of the repo
   page) and download **Vault.apk** from the newest release.
   *If there's no release yet:* go to **Actions → Build APK**, open the newest green run and download
   the **Vault-apk** artifact. You'll get a zip file; unzip it to get `Vault.apk`.
2. Open `Vault.apk`. Android will ask you to allow installing apps from your browser or file manager.
   Allow it, then tap **Install**.
3. Open **Vault** and choose a master password. **Don't forget it.** Nobody can reset it, and without
   it your passwords can't be opened.

## Using it

| To do this | Do this |
| --- | --- |
| Add an account | Tap a folder (e.g. **Steam**), then **+**. Fill in a name (e.g. *Main*, *Alt*), the username or email, and the password. |
| See a password | Tap the 👁 eye icon on the account. |
| Copy a username or password | Tap the copy icon next to it. |
| Edit or delete an account | Tap the account. |
| Make a new folder | On the home screen tap **New folder** (e.g. *Epic Games*, *Instagram*). |
| Rename or delete a folder | Open the folder and use the ✏️ or 🗑 icons at the top. |
| Find an account fast | Use the search box on the home screen. |
| Make a strong password | Tap **Generate strong password** when editing an account. |

## Back up your passwords

Your passwords exist **only on this phone**. If you lose the phone, reset it or uninstall the app,
they're gone, unless you have a backup.

- **Settings → Export backup** saves a `vault-backup-DATE.vault` file. It's encrypted with your master
  password, so it's safe to copy to your computer or a USB stick.
- **Settings → Restore from backup** (or *Restore from a backup file* on the first screen of a new
  phone) loads it back. You'll need the master password you had when you made the backup.

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
- `app/`: the Android app (Jetpack Compose, single activity).
- `.github/workflows/build-apk.yml`: runs the tests and builds the release APK on every push. Pushes
  to the default branch also publish a GitHub Release with `Vault.apk` attached.

Build locally with Android Studio, or with `./gradlew :app:assembleRelease` if you have the Android SDK.
