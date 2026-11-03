# Releasing Ember

Everyday builds (every push) are signed with the throwaway key in `app/debug.keystore`, so anyone can install them for
testing. Official releases are signed with the owner's private key, which never goes in the repository.

## One-time setup: the signing key

1. Make the key (JDK 17 has `keytool`). Keep `ember-release.jks` and its password safe and backed up: if it's lost,
   existing installs can't be updated, only uninstalled and reinstalled.

   ```
   keytool -genkeypair -v -keystore ember-release.jks -alias ember -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Give it to GitHub as secrets (Settings › Secrets and variables › Actions), or with the GitHub CLI:

   ```
   certutil -encodehex -f ember-release.jks ember-release.b64 0x40000001
   gh secret set EMBER_KEYSTORE_BASE64 -R harsharede/Ember < ember-release.b64
   gh secret set EMBER_KEYSTORE_PASSWORD -R harsharede/Ember
   gh secret set EMBER_KEY_ALIAS -R harsharede/Ember --body ember
   del ember-release.b64
   ```

## Every release

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`, and add a short changelog for that version code in
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Commit, then tag and push the tag:

   ```
   git tag v1.1.0
   git push origin v1.1.0
   ```

3. The Release workflow tests, builds, signs and publishes `ember-v1.1.0.apk` under Releases.

## F-Droid

F-Droid builds Ember from source and signs it with its own key, using the metadata in `fastlane/metadata/android/` and
the recipe in `fdroid/com.harsharede.ember.yml`. To submit, fork https://gitlab.com/fdroid/fdroiddata, copy that recipe
to `metadata/com.harsharede.ember.yml`, and open a merge request. New tags are picked up automatically after that.
