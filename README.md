# S3ARCH

Application Android **Material 3** pour chercher un contenu sur plusieurs trackers torrent
(C411, TR4KER, V3X), lancer le téléchargement directement sur la **Freebox** via l'API
officielle Freebox OS, suivre la progression en temps réel et lire le fichier avec **VLC**.

## Fonctionnalités

- **Recherche multi-trackers** : C411, TR4KER et V3X (protocole Torznab), requêtes en parallèle,
  fusion des résultats.
- **Tri automatique du plus petit au plus grand** (taille inconnue en fin de liste).
- **Clés API demandées au premier démarrage** puis enregistrées localement (modifiables
  dans les réglages, avec test de connexion par tracker).
- **Connexion Freebox au premier démarrage** : découverte de l'API, autorisation officielle
  (appui sur la flèche → du boîtier), session HMAC-SHA1, puis création automatique du
  dossier `/Disque dur/S3ARCH`.
- **Téléchargement en un bouton** : le lien `.torrent` est envoyé au gestionnaire de
  téléchargement de la Freebox, dans le dossier dédié.
- **Progression en temps réel** : pourcentage, débit, temps restant, rafraîchis toutes les 1,2 s.
- **Lecture avec VLC** : VLC lit le fichier de la Freebox à travers un petit proxy HTTP local
  authentifié qui relaie le flux et gère les requêtes `Range` (avance/recule possible).

## Écrans

| Écran | Contenu |
|---|---|
| Configuration (1er lancement) | Étape 1 : clés API C411 / TR4KER / V3X (+ endpoint Torznab editable). Étape 2 : adresse Freebox + dossier + autorisation. |
| Recherche | Barre de recherche, filtres par tracker, résultats triés par taille croissante, bouton de téléchargement. |
| Transferts | Cartes de téléchargement avec progression temps réel, débit, ETA, bouton « Lire avec VLC ». |
| Réglages | Clés API, endpoints, dossier Freebox, session/re-autorisation. |

## Configuration des trackers

Les clés se récupèrent sur chaque tracker (profil / paramètres → clé API). Valeurs par défaut :

| Tracker | Endpoint Torznab | Où trouver la clé |
|---|---|---|
| C411 | `https://c411.org/api` | Profil → Paramètres |
| TR4KER | `https://tr4ker.net/api/torznab` | Mon compte → Paramètres |
| V3X | `https://api.v3x.club/torznab` | Réglages → Intégrations / API (scope `torznab`) |

La recherche utilise `?t=search&apikey=…&q=…&limit=100&extended=1`. Si un tracker expose un
autre chemin, l'URL est modifiable dans l'app (bouton « URL » à côté de chaque clé).

## Connexion Freebox

1. Le téléphone doit être sur le même réseau Wi-Fi que la Freebox.
2. S3ARCH appelle `GET /api_version` sur `mafreebox.freebox.fr`, puis
   `POST /api/vX/login/authorize/` pour obtenir un `app_token`.
3. **Appuyez sur la flèche → de la Freebox** pour autoriser l'application (2 minutes).
4. La session est ouverte via `POST /api/vX/login/session/` (HMAC-SHA1 du challenge avec
   l'`app_token`), puis le dossier est créé avec `/fs/mkdir/`.
5. Les téléchargements partent avec `POST /downloads/add/` (`download_url` + `download_dir`
   encodé en base64) et la progression est lue sur `GET /downloads/`.

Le jeton d'application est conservé pour rouvrir une session silencieusement au prochain
lancement ; la réautorisation n'est nécessaire qu'après révocation côté Freebox.

## Lecture VLC

Le fichier reste sur la Freebox : VLC ne peut pas présenter l'en-tête d'authentification
Freebox. S3ARCH démarre donc un mini-serveur HTTP sur `127.0.0.1` qui :

- reçoit `GET /<fichier>?path=<chemin base64>` ;
- relance la requête vers `GET /api/vX/dl/<chemin base64>` avec `X-Fbx-App-Auth` ;
- relaie le flux (200/206) et les en-têtes `Content-Range` / `Content-Length` vers VLC.

VLC est lancé par Intent (`org.videolan.vlc`) ; si VLC n'est pas installé, le sélecteur de
lecteurs Android est proposé.

## Stack technique

- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3, Navigation Compose
- DataStore Preferences pour les clés/réglages et la liste des transferts
- OkHttp 4.12 pour les appels trackers et Freebox
- kotlinx.serialization pour la persistance locale
- minSdk 26, targetSdk 35, compileSdk 35, JDK 17, AGP 8.7.3 / Gradle 8.9

## Compiler l'APK

```bash
export JAVA_HOME=/chemin/vers/jdk-17
export ANDROID_HOME=/chemin/vers/android-sdk   # platform 35 + build-tools 35.0.0
gradle :app:assembleRelease                    # APK signé dans app/build/outputs/apk/release/
```

Le fichier `keystore.properties` référence `keystore/s3arch-release.jks`, versionné pour que
les mises à jour de l'APK restent installables par-dessus l'ancienne version. **Ce keystore
auto-signé est propre à ce projet** : ne l'utilisez pas pour une application publiée sur le
Play Store, et conservez-le si vous voulez continuer à mettre à jour l'app installée.

## Limites connues

- Les endpoints Torznab de V3X et C411 peuvent évoluer : ils sont modifiables dans l'app.
- L'app n'a pas pu être testée contre une Freebox réelle depuis l'environnement de build ;
  le flux d'autorisation et les endpoints suivent la documentation officielle Freebox OS.
- Les clés API sont stockées en clair dans le DataStore privé de l'application (non chiffré).
