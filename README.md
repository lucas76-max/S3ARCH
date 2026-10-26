# S3ARCH

**Version 1.3.0**

Application Android **Material 3 Expressive** (avec **Material You** : couleurs dynamiques issues du
fond d'écran sur Android 12+) pour chercher un contenu sur plusieurs trackers torrent
(C411, TR4KER, V3X et tout endpoint Torznab tiers), lancer le téléchargement directement sur la
**Freebox** via l'API officielle Freebox OS, suivre la progression en temps réel et lire le fichier
avec **VLC**.

## Fonctionnalités

- **Recherche multi-trackers** : C411, TR4KER et V3X (protocole Torznab), requêtes en parallèle,
  fusion des résultats.
- **Trackers tiers** : ajout d'un endpoint Torznab personnalisé, avec avertissement explicite que
  la compatibilité n'est **pas garantie**.
- **Tri au choix** : taille croissante (par défaut), taille décroissante, seeders décroissants,
  plus récents, nom A → Z. Le tri est mémorisé et s'applique instantanément, sans relancer la
  recherche.
- **Filtre facultatif par type de contenu** (Réglages → Types de contenus) : Films, Séries,
  Musique, Ebooks, Applications, Jeux, XXX, Autres. Aucun type coché = tous les résultats ; le
  nombre de résultats masqués est indiqué sous la barre de recherche.
- **Transferts détaillés** : nombre de seeders du torrent et débit courant en **Mbps**, en plus du
  pourcentage, du volume et du temps restant.
- **Clés API demandées au premier démarrage** puis enregistrées localement (modifiables
  dans les réglages, avec test de connexion par tracker).
- **Connexion Freebox au premier démarrage** : découverte de l'API, autorisation officielle
  (appui sur la flèche → du boîtier), session HMAC-SHA1, puis création automatique du
  dossier `/Disque dur/S3ARCH`.
- **Téléchargement en un bouton, en trois temps** : le téléphone récupère d'abord le `.torrent`
  et l'envoie **en fichier** à la Freebox (aucune URL à faire résoudre par la box) ; en cas
  d'échec, le lien est transmis au gestionnaire Freebox puis, en dernier recours, le magnet.
- **Journaux dans les réglages** : chaque recherche (URL avec clé masquée, code HTTP, nombre de
  résultats) et chaque appel Freebox (endpoint, code d'erreur, réponse) est consigné, avec
  boutons « Copier » et « Effacer ».
- **Progression en temps réel** : pourcentage, débit, temps restant, rafraîchis toutes les 1,2 s.
- **Lecture avec VLC** : VLC lit le fichier de la Freebox à travers un petit proxy HTTP local
  authentifié qui relaie le flux et gère les requêtes `Range` (avance/recule possible).

## Écrans

| Écran | Contenu |
|---|---|
| Configuration (1er lancement) | Étape 1 : clés API C411 / TR4KER / V3X (+ endpoint Torznab editable). Étape 2 : adresse Freebox + dossier + autorisation. |
| Recherche | Barre de recherche, filtres par tracker, sélecteur de tri, résultats filtrés par type, bouton de téléchargement. |
| Transferts | Cartes de téléchargement avec progression temps réel, débit, ETA, bouton « Lire avec VLC ». |
| Réglages | Clés API, trackers tiers Torznab, filtre par types de contenus, dossier Freebox, session/re-autorisation, couleurs dynamiques, journaux. |

## Trackers tiers (Torznab)

Un texte discret « Ajouter un tracker Torznab tiers » est disponible à l'étape 1 du premier
lancement et dans les réglages. Il ouvre un formulaire (nom, endpoint Torznab, clé) précédé d'un
message clair : S3ARCH ne se base que sur le protocole Torznab standard, donc **la compatibilité
avec un tracker tiers n'est pas garantie**. Le tracker ajouté apparaît ensuite dans les filtres de
recherche et peut être testé ou retiré depuis les réglages.

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

Depuis la 1.3.0, le `.torrent` est envoyé en **fichier** (`download_file`, `multipart/form-data`) :
la Freebox n'a plus besoin d'atteindre le tracker, ce qui supprime la plupart des refus
`invalid_url` et les blocages de trackers privés. Les liens sont aussi normalisés avant envoi
(URL relative résolue sur le domaine du tracker, espaces encodés, entités HTML, magnets vérifiés).

Deux pièges de cette API, corrigés depuis la 1.2.0 :

- `/downloads/add/` **n'accepte pas de corps JSON** (réponse `invalid_request`). Contrairement au
  reste de l'API, ce endpoint attend un corps `application/x-www-form-urlencoded`.
- les chemins de l'API Freebox sont absolus et doivent **finir par `/`** (`download_dir` comme
  `parent` de `/fs/mkdir/`) : `/Disque dur/S3ARCH/` et non `/Disque dur/S3ARCH`.

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
- Le refus `invalid_url` dépend du lien fourni par chaque tracker : le journal des réglages
  affiche l'URL exacte envoyée (passkey masquée) pour diagnostiquer un cas restant.
- L'app n'a pas pu être testée contre une Freebox réelle depuis l'environnement de build ;
  le flux d'autorisation, le format des requêtes et les endpoints suivent la documentation
  officielle Freebox OS (`dev.freebox.fr/sdk/os/`).
- Le type d'un résultat est déduit des catégories Torznab ; un tracker qui n'en expose pas laisse
  ses résultats en « Autres », donc le filtre par type peut les masquer.
- Les clés API sont stockées en clair dans le DataStore privé de l'application (non chiffré).
