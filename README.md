# S3ARCH

**Version 1.2.0**

Application Android **Material 3 Expressive** (avec **Material You** : couleurs dynamiques issues du
fond d'écran sur Android 12+) pour chercher un contenu sur plusieurs trackers torrent
(C411, TR4KER, V3X et tout endpoint Torznab tiers), lancer le téléchargement directement sur la
**Freebox** via l'API officielle Freebox OS, suivre la progression en temps réel et lire le fichier
dans un **lecteur intégré** (Media3/ExoPlayer) ou avec **VLC**.

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
- **Téléchargement en un bouton, en deux temps** : le téléphone récupère le `.torrent` et
  l'envoie **en fichier** à la Freebox, qui crée une vraie tâche BitTorrent ; à défaut, le lien
  magnet est utilisé. L'app vérifie ensuite que la tâche créée est bien de type `bt`.
  L'URL Torznab du `.torrent` (`?t=get&id=…`) n'est **jamais** transmise à la box : elle
  l'enregistrerait comme un simple fichier au lieu de lancer le torrent.
- **Journaux dans les réglages** : chaque recherche (URL avec clé masquée, code HTTP, nombre de
  résultats) et chaque appel Freebox (endpoint, code d'erreur, réponse) est consigné, avec
  boutons « Copier » et « Effacer ».
- **Progression en temps réel** : pourcentage, débit, temps restant, rafraîchis toutes les 1,2 s.
- **Gestion des transferts** : bouton **Annuler** (arrête la tâche et la retire de la file
  Freebox sans effacer le contenu déjà téléchargé) et bouton **Supprimer du disque**
  (`DELETE /downloads/{id}/erase`, avec confirmation).
- **Lecture** : lecteur intégré (Media3/ExoPlayer) via « Lire dans S3ARCH », ou VLC. S3ARCH lit
  la liste des fichiers de la tâche (`GET /downloads/{id}/files`), garde le plus gros média (pas
  le dossier du torrent ni l'échantillon) et le sert par un proxy HTTP local authentifié.
- **Accès hors du réseau local (VPN WireGuard)** : proposé à la fin de l'appairage et dans les
  réglages, accepté ou refusé librement. L'app crée elle-même la configuration sur la Freebox,
  puis bascule automatiquement sur le tunnel quand la box n'est pas joignable en local.

## Écrans

| Écran | Contenu |
|---|---|
| Configuration (1er lancement) | Étape 1 : clés API C411 / TR4KER / V3X (+ endpoint Torznab editable). Étape 2 : adresse Freebox + dossier + autorisation. |
| Recherche | Barre de recherche, filtres par tracker, sélecteur de tri, résultats filtrés par type, bouton de téléchargement. |
| Transferts | Cartes de téléchargement avec type de tâche (Torrent / Fichier), progression temps réel, débit, ETA, boutons « Lire dans S3ARCH » / « Lire avec VLC », « Annuler », « Supprimer du disque » et « Masquer de la liste ». |
| Réglages | Clés API, trackers tiers Torznab, filtre par types de contenus, dossier Freebox, session/réautorisation, **VPN hors réseau local**, couleurs dynamiques, journaux. |

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

Le `.torrent` est envoyé en **fichier** (`download_file`, `multipart/form-data`) :
la Freebox n'a plus besoin d'atteindre le tracker, ce qui supprime la plupart des refus
`invalid_url` et les blocages de trackers privés. Les liens sont aussi normalisés avant envoi
(URL relative résolue sur le domaine du tracker, espaces encodés, entités HTML, magnets vérifiés).

L'URL du `.torrent` n'est jamais passée en `download_url` : une URL
Torznab de type `?t=get&id=…` n'a pas d'extension `.torrent`, et la Freebox la traite comme un
fichier ordinaire (elle enregistre le `.torrent` dans le dossier sans rien télécharger).
Après création, l'app relit la tâche (`GET /downloads/{id}`) et vérifie `type == "bt"` ;
si la box a créé une tâche `http`, elle la supprime avec ses fichiers (`/downloads/{id}/erase`)
et bascule sur le magnet.

Trois pièges de cette API, déjà traités :

- `/downloads/add/` **n'accepte pas de corps JSON** (réponse `invalid_request`). Contrairement au
  reste de l'API, ce endpoint attend un corps `application/x-www-form-urlencoded`.
- `download_dir` doit **finir par `/`** : `/Disque dur/S3ARCH/`.
- `parent` de `/fs/mkdir/` doit au contraire **ne pas** finir par `/` :
  `b64("/Disque dur")`, pas `b64("/Disque dur/")`. Avec le slash, la Freebox répond
  `path_not_found`, le dossier n'est jamais créé, et le téléchargement accepté échoue ensuite
  en `bt_file_error` (contexte du torrent non inscriptible).

Si la création du dossier échoue quand même (disque absent, dossier système refusé), l'app se
replie sur le dossier de téléchargement par défaut configuré dans la Freebox, puis sur son
sous-dossier `S3ARCH`, et indique le chemin réellement utilisé dans les journaux.

Le jeton d'application est conservé pour rouvrir une session silencieusement au prochain
lancement ; la réautorisation n'est nécessaire qu'après révocation côté Freebox.

## Gestion des transferts

`GET /downloads/{id}` donne l'état d'une tâche. Les deux actions de l'écran Transferts utilisent
les endpoints officiels :

| Bouton | Appel | Effet |
|---|---|---|
| Annuler | `DELETE /downloads/{id}` | Arrête la tâche et la retire de la file, **fichiers conservés** |
| Supprimer du disque | `DELETE /downloads/{id}/erase` | Retire la tâche **et efface les fichiers** |
| Masquer de la liste | aucun | Retire seulement l'entrée de l'app : **tâche et fichiers intacts** |

Dans les trois cas l'entrée disparaît de la liste suivie par l'app, et une confirmation est
demandée avant d'effacer sur le disque comme avant de masquer. Le masquage ne fait aucun appel
à la Freebox : le transfert continue, les fichiers restent sur le disque et sont toujours
visibles dans Freebox Files.

## Lecture (lecteur intégré ou VLC)

Le bouton **Lire dans S3ARCH** ouvre le lecteur intégré (Media3/ExoPlayer) ; **Lire avec VLC**
reste disponible juste en dessous. Les deux consomment exactement la même source : l'URL HTTP
locale du proxy. Le lecteur intégré a l'avantage de dire précisément ce qui échoue (« La Freebox
a répondu HTTP 404 », « codec non pris en charge », « délai dépassé ») et propose un repli VLC
en un tap.

Une tâche BitTorrent porte presque toujours un **dossier** (`name`), pas un fichier : passer
`dossier/nom` à VLC lui donne un répertoire, d'où un lecteur ouvert sur une adresse muette
(« 127.0.0.1 ») et aucune lecture. L'app demande donc à la Freebox la liste réelle des
fichiers de la tâche (`GET /downloads/{id}/files`), puis :

1. garde le **plus gros média** (le film, pas le `.nfo`, les sous-titres ni l'échantillon de
   quelques Mo) ;
2. le sert à VLC via le mini-serveur HTTP local `127.0.0.1`, alimenté par
   `GET /api/vX/dl/<chemin base64>` avec `X-Fbx-App-Auth` — en **LAN**, sans TLS et sans
   passer par l'accès distant de la box ;
3. à défaut, essaie le lien de partage `preview_url` en amont du proxy, puis
   `/fs/download/?path=` ; une session expirée (403) est renouvelée une fois.

**Pourquoi le `preview_url` n'est pas donné tel quel** : il répond bien 200, mais il
pointe vers l'**accès distant** de la Freebox
(`https://<nom>.freeboxos.fr:<port>/api/latest/btpreview/...`) et le lecteur refuse ce flux. Le
proxy présente donc une URL HTTP simple, avec `Content-Type` issu de l'en-tête de la box ou à
défaut de l'extension réelle du fichier, `Accept-Ranges` et `Content-Range` transmis.

**Pourquoi le chargement pouvait être infini** : deux comportements du proxy ont été
corrigés. D'une part il n'invente plus de `Content-Length` — annoncer la taille de la tâche
quand la box renvoie moins d'octets laissait le lecteur attendre une suite qui n'arrivait
jamais ; sans longueur connue, la réponse part désormais en `Transfer-Encoding: chunked`, que
VLC comme Media3 gèrent. D'autre part une page HTML/JSON renvoyée avec un code 200 (erreur
applicative de la box) ne peut plus sortir sous le type d'une vidéo : elle produit un `502`
explicite. Le proxy journalise enfin chaque requête du lecteur (méthode, `Range` demandé) et le
résultat (`status`, octets envoyés, durée, chunked ou taille fixe), ce qui rend un blocage
lisible dans Réglages → Journaux.

L'URL locale a la forme `http://127.0.0.1:<port>/v/<jeton>/<nom-du-fichier>` : le nom réel
termine l'URL (le lecteur affiche donc le film, pas « 127.0.0.1 ») et le flux est testé avant
lancement, ce qui remonte le message d'erreur exact au lieu d'un VLC vide. Le journal affiche
désormais le code HTTP et le `Content-Type` de **chaque** source tentée (`/dl/`, lien de
partage, `/fs/download/`).

**Autre piège corrigé** : ce test de flux et le proxy s'exécutaient sur le thread principal.
Android y interdit toute I/O réseau et lève `NetworkOnMainThreadException`, une exception
**sans message** — le journal affichait donc un « Lecture impossible » sans la moindre piste.
Toute la lecture (liste des fichiers, test du lien, proxy) tourne désormais sur `Dispatchers.IO`,
et les erreurs sont journalisées avec leur type et leur pile. Les tests JVM de
`VlcProxyServerTest` exercent le proxy sur de vrais sockets (relais, `Range`, 404, refus de la
box, chunked correctement encadré, page HTML et erreur JSON transformées en 502) : c'est ce qui
garantit que le flux sort vraiment du serveur local.

VLC est lancé par Intent (`org.videolan.vlc`) ; si VLC n'est pas installé, l'app propose le
lecteur intégré. Le journal (Réglages → Journaux) indique le fichier retenu, l'URL utilisée et
chaque requête servie par le proxy.

## Accès hors du réseau local (VPN)

Hors du Wi-Fi de la maison, `mafreebox.freebox.fr` ne répond plus : l'application propose donc de
créer un **serveur WireGuard** sur la Freebox. La proposition apparaît une seule fois, à la fin de
l'appairage (et reste disponible dans les réglages), avec une explication et deux choix :
**Créer le VPN** ou **Pas maintenant**. Un refus n'est pas définitif : le bouton reste dans les
réglages.

Côté Freebox, tout est automatisé (API officielle, section `vpn`) :

| Étape | Appel |
|---|---|
| Trouver le serveur | `GET /vpn/` → entrée de type `wireguard` |
| L'activer | `PUT /vpn/wireguard/config/` avec `{"enabled": true}` (sauté s'il tourne déjà) |
| Choisir une adresse | `GET /vpn/ip_pool/` → première IPv4 libre entre `ip_start` et `ip_end` |
| Créer l'utilisateur | `POST /vpn/user/` `{login: "s3arch", type: "wireguard", ip_reservation: …}` |
| Récupérer la config | `GET /vpn/download_config/wireguard/s3arch/plain` (wg-quick) |

La configuration obtenue (clé privée, clé publique du serveur, endpoint, DNS, MTU) est conservée
sur l'appareil, et le tunnel est monté par la bibliothèque officielle
`com.wireguard.android:tunnel`. Android demande **une fois** l'autorisation d'établir une connexion
VPN : c'est le seul geste demandé à l'utilisateur.

**Bascule automatique.** À chaque lancement et retour au premier plan, l'app teste le portail de la
box (connexion TCP sur le port 80, 1,5 s) :

- joignable → le tunnel est coupé et les appels repartent sur `mafreebox.freebox.fr` ;
- injoignable → le tunnel est monté et les appels visent l'**IP locale** de la box (mémorisée à
  l'appairage), qui passe par le VPN.

Quand l'application passe en arrière-plan, le tunnel est coupé : le reste du téléphone n'est pas
ralenti. Sur le réseau local, activer le VPN ne monte rien (un tunnel vers soi-même ne sert à
rien) : l'accord est enregistré et le tunnel s'établira de lui-même hors de chez vous.

### Le droit « Paramètres » de la Freebox

L'API `vpn` est protégée par le droit **`settings`** : si l'application ne l'a pas, la box répond
`403 insufficient_rights` avec `missing_right: settings`. L'application lit désormais les droits
réellement accordés (bloc `permissions` renvoyé par `POST /login/session/`) et, quand `settings`
manque, affiche une carte explicite dans l'appairage **et** dans les réglages :

1. Freebox OS (`mafreebox.freebox.fr`) → **Paramètres de la Freebox** → **Gestion des applications** ;
2. application **S3ARCH** → cocher **Paramètres** → **Enregistrer** ;
3. revenir dans S3ARCH → **J'ai autorisé** : la session est rouverte (seul moment où la box
   renvoie les droits) et la création du VPN reprend automatiquement.

La demande d'autorisation transmet aussi la liste des droits nécessaires (`settings`,
`downloader`, `explorer`) ; les firmwares qui refusent ce champ retombent sur la demande standard.
Sans ce droit, la création du VPN ne peut pas fonctionner : c'est une limite de Freebox OS, pas de
l'application.

## Stack technique

- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3, Navigation Compose
- DataStore Preferences pour les clés/réglages et la liste des transferts
- OkHttp 4.12 pour les appels trackers et Freebox
- Media3 (ExoPlayer) 1.5.1 pour le lecteur vidéo intégré
- WireGuard Android `tunnel` 1.0.20230706 pour le VPN hors réseau local
- lifecycle-process pour la bascule LAN/VPN au premier plan
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
- `bt_file_error` signifie que la Freebox n'a pas pu écrire dans le dossier cible (dossier
  absent, disque non monté ou plein) : le journal indique le chemin utilisé et la raison.
- `preview_url` n'est fourni par la Freebox que pour les tâches BitTorrent terminées ; pour les
  autres, l'app passe par le proxy local (d'où l'importance de la liste de fichiers).
- L'app n'a pas pu être testée contre une Freebox réelle depuis l'environnement de build ;
  le flux d'autorisation, le format des requêtes et les endpoints suivent la documentation
  officielle Freebox OS (`dev.freebox.fr/sdk/os/`).
- Le type d'un résultat est déduit des catégories Torznab ; un tracker qui n'en expose pas laisse
  ses résultats en « Autres », donc le filtre par type peut les masquer.
- Les clés API sont stockées en clair dans le DataStore privé de l'application (non chiffré).
