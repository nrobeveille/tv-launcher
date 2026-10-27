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
- **Paramètres** (roue dentée en haut à droite) :
  - Applis de l'accueil (cases à cocher) et Réorganiser
  - Fond d'écran, taille des tuiles, affichage du nom des applis, format de l'heure
  - Raccourcis vers les paramètres de la TV, le Wi-Fi et les options développeur
  - Réinitialiser l'accueil

Stack : Kotlin, Jetpack Compose + Compose for TV (`androidx.tv:tv-material`), DataStore.

---

## Récupérer l'APK

Chaque push sur `main` est compilé par GitHub Actions. L'APK est publié dans la release
**« latest »** du dépôt (onglet *Releases* → `tv-launcher.apk`).

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

### 3. En faire l'écran d'accueil

Google TV n'affiche pas de choix d'écran d'accueil : il faut désactiver le sien.

```
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

Appuie sur Home : c'est Launcher TV qui s'ouvre. Si l'accueil Google ou un écran de
configuration revient malgré tout, désactive aussi l'accueil de secours :

```
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

### Revenir en arrière

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```

Garde le débogage activé pendant la période d'essai : c'est ce qui te permet de revenir
en arrière à tout moment depuis le PC.

---

## Mesurer la mémoire

```
adb shell dumpsys meminfo fr.nrobeveille.tvlauncher | grep -E "TOTAL PSS|TOTAL RSS"
```

## Structure

```
app/src/main/java/fr/nrobeveille/tvlauncher/
├── MainActivity.kt          activité Home, réception des installations/désinstallations
├── LauncherViewModel.kt     état, navigation, actions
├── data/                    liste des applis, bannières (cache), réglages (DataStore)
└── ui/
    ├── home/                accueil
    ├── allapps/             toutes les applis
    ├── settings/            paramètres, choix et ordre des applis
    ├── components/          tuiles, menu contextuel, gestion du focus
    └── theme/               couleurs et fonds
```

Note : l'APK de release est signé avec la clé de debug, pour pouvoir l'installer
directement. Un APK compilé sur ta machine et un autre compilé par GitHub n'ont pas la
même clé : pour passer de l'un à l'autre, désinstalle d'abord (`adb uninstall
fr.nrobeveille.tvlauncher`).
