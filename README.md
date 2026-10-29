# Launcher TV

Écran d'accueil léger et personnalisable pour Google TV / Android TV, pensé pour le
**Xiaomi TV Stick 4K (2e génération)**. Pas de pub, pas de recommandations, pas de
service en arrière-plan : juste tes applis.

- **Accueil** : l'heure, la date, et une rangée avec les applis que tu choisis, dans
  l'ordre que tu veux. La dernière tuile, **Toutes les applis**, ouvre la liste complète.
- **Appui long sur OK** sur une appli de l'accueil : *Déplacer*, *Retirer de l'accueil*,
  *Infos de l'appli*. En mode déplacement : ◀ ▶ pour bouger, OK pour valider.
- **Toutes les applis** : grille de toutes les applis installées ; appui long pour
  ajouter ou retirer de l'accueil (une pastille ✓ indique celles déjà présentes).
- **Fond « Suit la météo »** (par défaut) : ciel de jour ou de nuit, étoiles, nuages,
  pluie, neige, brouillard ou orage animés selon la météo d'Angers.
- **Mode soirée** : appli à part (« Mode soirée » dans *Toutes les applis*, épinglable sur
  l'accueil). L'écran s'illumine au rythme de la musique, couleurs concentrées sur les
  bords pour être reprises par un boîtier LED HDMI. Voir plus bas.
- **Paramètres** (roue dentée en haut à droite) :
  - Applis de l'accueil (cases à cocher) et Réorganiser
  - Fond d'écran, taille des tuiles, affichage du nom des applis, format de l'heure
  - Raccourcis vers les paramètres de la TV, le Wi-Fi et les options développeur
  - Réinitialiser l'accueil
  - **Mise à jour** : vérifie la dernière release GitHub, télécharge et installe la
    nouvelle version. Une pastille bleue sur la roue dentée signale une mise à jour.

Stack : Kotlin, Jetpack Compose + Compose for TV (`androidx.tv:tv-material`), DataStore.

---

## Récupérer l'APK

Chaque push sur `main` est compilé par GitHub Actions et publié comme release
`v1.0.N` (N = numéro du build), avec l'APK `tv-launcher.apk`. Les 5 dernières versions
sont conservées. Les autres branches sont compilées mais pas publiées.

Pour compiler toi-même : ouvre le dossier dans Android Studio, ou lance
`./gradlew assembleRelease` (l'APK sort dans `app/build/outputs/apk/release/`).

## Installer sur le stick

### 1. Activer le débogage

Sur le stick : **Paramètres → Système → À propos**, puis appuie 7 fois sur
**Build de l'OS Android TV**. Ensuite **Paramètres → Système → Options pour les
développeurs** :

- **Débogage sans fil** (Google TV récent) : active-le, puis *Associer l'appareil avec un
  code d'association*. Sur le PC :
  ```
  adb pair IP_DU_STICK:PORT_D_ASSOCIATION     # saisir le code affiché
  adb connect IP_DU_STICK:PORT_AFFICHÉ        # port indiqué sur l'écran Débogage sans fil
  ```
- ou **Débogage USB / réseau** (versions plus anciennes) : `adb connect IP_DU_STICK:5555`
  et accepte l'autorisation qui s'affiche sur la TV.

### 2. Installer et tester

```
adb install -r tv-launcher.apk
adb shell am start -n fr.nrobeveille.tvlauncher/.MainActivity
```

« Launcher TV » apparaît aussi dans la liste des applis du stick. **Teste-le d'abord**
avant d'en faire ton accueil.

### 3. Autoriser les mises à jour

Le launcher doit avoir le droit d'installer des applis pour se mettre à jour lui-même :

```
adb shell appops set fr.nrobeveille.tvlauncher REQUEST_INSTALL_PACKAGES allow
```

(Sinon, à la première mise à jour, il ouvre l'écran système où l'autoriser.)

### 4. En faire l'écran d'accueil

Déclare-le comme appli d'accueil par défaut, puis appuie sur Home :

```
adb shell cmd role add-role-holder --user 0 android.app.role.HOME fr.nrobeveille.tvlauncher
adb shell cmd role get-role-holders --user 0 android.app.role.HOME
```

Ton launcher s'ouvre alors avec le bouton Home.

**Ne désactive pas l'accueil Google TV** (`com.google.android.apps.tv.launcherx`) : sur le
Xiaomi TV Stick 4K (2e gén.), le stick gèle dès qu'on appuie sur Home s'il est désactivé.
Il reste installé mais ne s'affiche plus. Si tu l'as désactivé et que tout est figé :
`adb shell pm enable com.google.android.apps.tv.launcherx`.

### Revenir en arrière

```
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx
```

Garde le débogage activé : c'est ce qui te permet de revenir en arrière à tout moment
depuis le PC.

---

## Mode soirée

Ouvre « Mode soirée » (ou épingle-le sur l'accueil depuis *Toutes les applis*, appui long).

| Touche | Action |
|---|---|
| ◀ ▶ | Source du rythme : **Son du stick**, **Micro**, **Tempo manuel** |
| ▲ ▼ | Effet : **Arc-en-ciel**, **Flash couleur**, **Pulsation** |
| OK | Source Micro : test du micro · Tempo manuel : appuie au rythme pour caler le tempo |
| Retour | Quitter |

- **Son du stick** : analyse le son joué par le stick lui-même (musique lancée sur la TV,
  par exemple une appli qui continue en arrière-plan). Peut ne rien capter si le son part
  en passthrough (Dolby) vers une barre de son.
- **Micro** (par défaut) : micro de la télécommande ou micro USB. Avec la source Micro,
  **OK lance un test** (≈ 1 min, avec de la musique) : il essaie toutes les entrées audio
  du stick, vérifie que le son tient 45 s d'affilée, puis garde la meilleure. Si le micro
  de la télécommande se coupe, le Mode soirée le rouvre automatiquement. Le détail du test
  est aussi dans `adb logcat -s MicTest`.
- **Tempo manuel** : marche toujours, sans aucun son capté.

La première fois, autorise l'accès au micro (demandé à l'écran). Si une source ne capte
rien pendant quelques secondes, un message propose d'en essayer une autre. Les éclairs
sont limités à 2,5 par seconde.

## Mises à jour

Dans **Paramètres → Mise à jour**, le launcher interroge l'API GitHub
(`/repos/nrobeveille/tv-launcher/releases/latest`). Si le numéro de la release est plus
grand que celui de la version installée, il télécharge l'APK, vérifie qu'il s'agit bien
du launcher, puis l'installe. L'appli redémarre toute seule. Il vérifie aussi en silence
au démarrage (au plus toutes les 6 h) pour afficher la pastille.

Le dépôt doit être **public** pour que le launcher lise les releases sans jeton. Pour un
dépôt privé, crée un jeton GitHub en lecture seule et ajoute-le en secret `UPDATE_TOKEN` :
il sera intégré à l'APK.

### Clé de signature

Une mise à jour n'est acceptée par Android que si elle est signée avec la même clé que
la version installée. Les builds de `main` sont signés avec la clé fournie par deux
secrets du dépôt (*Settings → Secrets and variables → Actions*) :

- `SIGNING_KEYSTORE_BASE64` : le fichier `.jks` encodé en base64 ;
- `SIGNING_PASSWORD` : son mot de passe (alias de la clé : `tvlauncher`).

Sans ces secrets, la publication échoue volontairement. **Garde une copie de la clé** :
si elle est perdue, il faudra désinstaller puis réinstaller le launcher une fois.

### Passer d'une ancienne version à une version signée

Les premières versions (avant le système de mise à jour) étaient signées avec une clé
temporaire. Pour passer à la version signée, une réinstallation est nécessaire une seule
fois :

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb uninstall fr.nrobeveille.tvlauncher
adb install tv-launcher.apk
adb shell appops set fr.nrobeveille.tvlauncher REQUEST_INSTALL_PACKAGES allow
adb shell cmd role add-role-holder --user 0 android.app.role.HOME fr.nrobeveille.tvlauncher
```

Les mises à jour suivantes se font depuis les Paramètres.

---

## Mesurer la mémoire

```
adb shell dumpsys meminfo fr.nrobeveille.tvlauncher | grep -E "TOTAL PSS|TOTAL RSS"
```

## Structure

```
app/src/main/java/fr/nrobeveille/tvlauncher/
├── MainActivity.kt          activité Home, réception des installations/désinstallations
├── LauncherViewModel.kt     état, navigation, actions, mise à jour
├── data/                    liste des applis, bannières (cache), réglages (DataStore)
├── update/                  recherche, téléchargement et installation des mises à jour
├── party/                   Mode soirée : analyse du son, effets lumineux
└── ui/
    ├── home/                accueil
    ├── allapps/             toutes les applis
    ├── settings/            paramètres, choix et ordre des applis
    ├── components/          tuiles, menu contextuel, gestion du focus
    └── theme/               couleurs et fonds
```
