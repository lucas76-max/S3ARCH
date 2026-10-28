<div align="center">

# S3ARCH

**Cherchez un contenu, lancez-le sur votre Freebox, regardez-le.**

[![Release](https://img.shields.io/github/v/release/lucas76-max/S3ARCH?label=release&color=4f46e5)](https://github.com/lucas76-max/S3ARCH/releases/latest)
[![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)](#installation)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7f52ff?logo=kotlin&logoColor=white)](#stack)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-4285f4)](#stack)

</div>

Application Android (Kotlin + Jetpack Compose, Material You) qui interroge plusieurs trackers
Torznab, envoie le `.torrent` à la Freebox via l'API Freebox OS, suit la progression en temps réel
et lit le fichier dans un lecteur intégré ou avec VLC.

---

## ✨ Fonctionnalités

- **Multi-trackers** — C411, TR4KER, V3X, AstraTorrent, YGG Reborn et ZXX Club en parallèle, plus
  tout endpoint Torznab personnalisé.
- **Premier lancement guidé** — logos des trackers à activer d'un appui, clés API (testables),
  appairage Freebox avec `app_token`.
- **Recherche fine** — tri (taille, seeders, date, nom), filtre par type de contenu, masquage des
  torrents à 0 seeder (actif par défaut).
- **Téléchargement en un bouton** — le `.torrent` est envoyé **en fichier** à la box ; repli sur le
  magnet si nécessaire. Dossier cible : `/Disque dur/Téléchargements/S3ARCH`.
- **Transferts en direct** — débit, ETA, progression animée, annulation et suppression du disque.
- **Lecture** — lecteur Media3/ExoPlayer intégré ou VLC, via un proxy HTTP local authentifié vers la
  Freebox.
- **Hors du réseau local** — création automatique d'un VPN WireGuard sur la box, bascule LAN/VPN
  transparente.
- **Mises à jour intégrées** — bandeau « Mise à jour disponible. », changelog, téléchargement et
  installation de l'APK depuis GitHub.

## 📥 Installation

L'APK signé est disponible dans les [releases](https://github.com/lucas76-max/S3ARCH/releases/latest)
(`S3ARCH-<version>.apk`). Autorisez l'installation depuis cette source, puis installez normalement.

> Une réinstallation peut être nécessaire si la version précédente a été signée avec une autre clé :
> Android refuse alors la mise à jour par-dessus.

## 🔑 Trackers

Les clés se créent sur chaque site ; l'endpoint est modifiable dans l'app (bouton « URL »).

| Tracker | Endpoint Torznab | Où trouver la clé |
|---|---|---|
| C411 | `https://c411.org/api` | Profil → Paramètres |
| TR4KER | `https://tr4ker.net/api/torznab` | Mon compte → Paramètres |
| V3X | `https://api.v3x.club/torznab` | Compte → Paramètres → Intégrations → clé Torznab |
| AstraTorrent | `https://astratorrent.la/api/torznab.php` | Mon cockpit → Seedbox & *Arr → clé API |
| YGG Reborn | `https://www.yggreborn.org/api` | En haut du site : API → passkey |
| ZXX Club | `https://zxxclub.com/api/torznab` | Compte → API & Applications → clé de lecture |

Requête envoyée : `?t=search&apikey=…&q=…&limit=100&extended=1` (50 résultats pour YGG Reborn).

## 🧱 Stack

Kotlin 2.0.21 · Jetpack Compose (Material 3, couleurs dynamiques + palette d'accent) · OkHttp ·
Coil · DataStore · Media3/ExoPlayer · `wireguard-android` · minSdk 26, targetSdk 35.

## 🛠️ Compiler

```bash
export JAVA_HOME=/chemin/vers/jdk-17
export ANDROID_HOME=/chemin/vers/android-sdk    # platform 35 + build-tools 35.0.0
./gradlew :app:assembleRelease                  # APK dans app/build/outputs/apk/release/
```

La signature de release lit `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) — **volontairement absent du dépôt**, comme le keystore lui-même. Sans ce fichier,
Gradle produit un APK signé en debug, non publiable.

## ⚖️ Avertissement

S3ARCH est un outil de téléchargement pair-à-pair. Elle n'incite ni ne cautionne le piratage :
vous êtes seul responsable de l'usage que vous en faites et du contenu que vous téléchargez ou
partagez. Vérifiez que vous détenez les droits sur les œuvres concernées et que votre usage
respecte la législation en vigueur dans votre pays.

## 🔗 Liens

[Releases](https://github.com/lucas76-max/S3ARCH/releases) ·
[Signaler un problème](https://github.com/lucas76-max/S3ARCH/issues)
