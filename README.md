# Samsung Remote - Application Android

Une application de télécommande pour téléviseur Samsung développée en Kotlin avec reconnaissance vocale.

## 📥 Télécharger l'APK

[![Build Status](https://github.com/VOTRE_USER/signalAndroid/actions/workflows/build-apk.yml/badge.svg)](https://github.com/VOTRE_USER/signalAndroid/actions)

**Pas besoin d'Android Studio !** Téléchargez directement l'APK compilé :

1. Allez sur [Actions](../../actions/workflows/build-apk.yml)
2. Cliquez sur le dernier workflow réussi (✅)
3. Téléchargez l'artifact "SamsungRemote-APK"
4. Décompressez et installez l'APK sur votre Android

📖 **Guide détaillé** : [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md)

## ✨ Fonctionnalités

- 🔍 **Découverte automatique** des téléviseurs Samsung sur le réseau local
- 🔐 **Appairage sécurisé** avec le téléviseur
- 🎙️ **Saisie vocale** pour la recherche (reconnaissance vocale français)
- 🎮 **Interface de télécommande complète** :
  - Navigation (haut, bas, gauche, droite, OK)
  - Contrôle du volume et des chaînes
  - Boutons de fonction (Power, Menu, Home, Back, Source, Info)
  - Contrôles média (Play, Pause, Stop, Rewind, Forward)
  - Pavé numérique complet (0-9)
  - Bouton microphone pour saisie vocale

## Technologies utilisées

- **Langage** : Kotlin
- **UI** : Android XML Layouts avec Material Design
- **Architecture** : MVVM
- **Réseau** :
  - NSD (Network Service Discovery) pour la découverte des téléviseurs
  - WebSocket (OkHttp) pour la communication avec le téléviseur
- **Protocole** : Samsung SmartThings API v2

## Prérequis

- Android 7.0 (API level 24) ou supérieur
- Le téléphone et le téléviseur Samsung doivent être sur le même réseau Wi-Fi
- Téléviseur Samsung compatible (modèles 2016 et ultérieurs)

## 📦 Installation

### Option 1 : Télécharger l'APK pré-compilé (Recommandé)

**Le plus simple !** Pas besoin d'Android Studio :

1. Allez sur [GitHub Actions](../../actions/workflows/build-apk.yml)
2. Téléchargez le dernier artifact "SamsungRemote-APK"
3. Transférez l'APK sur votre téléphone
4. Installez l'application (autorisez les sources inconnues si demandé)

📖 Voir [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md) pour plus de détails

### Option 2 : Compiler vous-même

Si vous voulez modifier le code :

1. Clonez ce dépôt
   ```bash
   git clone <URL_REPO>
   cd signalAndroid
   ```
2. Ouvrez le projet dans Android Studio
3. Synchronisez les dépendances Gradle
4. Compilez : `Build → Build APK`

📖 Voir [BUILD.md](BUILD.md) pour le guide complet de compilation

## 🎮 Utilisation

### Première connexion

1. **Lancez l'application** sur votre téléphone Android
2. **Assurez-vous** que votre téléphone et TV sont sur le même Wi-Fi
3. **Appuyez sur "Rechercher une TV"** pour découvrir les téléviseurs
4. **Sélectionnez votre téléviseur** dans la liste
5. **Acceptez la connexion** sur votre téléviseur Samsung (popup qui apparaît)
6. **Utilisez la télécommande** virtuelle pour contrôler votre TV

### Saisie vocale 🎙️

**Pour saisir du texte facilement dans les barres de recherche :**

1. Sur l'écran de télécommande, **appuyez sur le bouton microphone** (FAB en haut à droite)
2. **Autorisez** l'accès au microphone (première fois seulement)
3. **Parlez** clairement en français (ex: "Stranger Things", "Documentaire nature")
4. Le texte apparaît **en temps réel**
5. **Appuyez sur "Envoyer"** pour transmettre à la TV
6. Le texte est automatiquement saisi dans la barre de recherche active !

**Cas d'usage** : YouTube, Netflix, navigateur web, recherche d'applications, etc.

## 📁 Structure du projet

```
com.samsung.remote/
├── model/          # Modèles de données (SamsungTV, RemoteKey)
├── network/        # Services réseau (TVDiscoveryService, SamsungWebSocketClient)
├── ui/             # Activités Android (MainActivity, PairingActivity, RemoteControlActivity)
├── adapter/        # Adaptateurs RecyclerView (TVListAdapter)
└── util/           # Utilitaires (PreferencesManager, VoiceInputManager)
```

### Fichiers principaux

- `SamsungWebSocketClient.kt` - Communication WebSocket avec la TV
- `VoiceInputManager.kt` - Reconnaissance vocale Android
- `TVDiscoveryService.kt` - Découverte NSD des téléviseurs
- `RemoteControlActivity.kt` - Interface principale de télécommande
- `RemoteKey.kt` - Énumération de toutes les touches disponibles

## Protocole Samsung

L'application utilise le protocole WebSocket de Samsung sur le port 8002 :
- URL : `ws://<TV_IP>:8002/api/v2/channels/samsung.remote.control`
- Format des commandes : JSON
- Authentification : Token-based après appairage

## Licence

Ce projet est fourni à titre éducatif et personnel.
