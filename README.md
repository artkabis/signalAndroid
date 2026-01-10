# Samsung Remote - Application Android

Une application de télécommande pour téléviseur Samsung développée en Kotlin.

## Fonctionnalités

- Découverte automatique des téléviseurs Samsung sur le réseau local
- Appairage sécurisé avec le téléviseur
- Interface de télécommande complète avec :
  - Navigation (haut, bas, gauche, droite, OK)
  - Contrôle du volume et des chaînes
  - Boutons de fonction (Power, Menu, Home, Back, Source)
  - Contrôles média (Play, Pause, Stop, Rewind, Forward)
  - Pavé numérique complet

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

## Installation

1. Clonez ce dépôt
2. Ouvrez le projet dans Android Studio
3. Synchronisez les dépendances Gradle
4. Compilez et installez l'application sur votre appareil Android

## Utilisation

1. Lancez l'application
2. Appuyez sur "Rechercher une TV" pour découvrir les téléviseurs disponibles
3. Sélectionnez votre téléviseur dans la liste
4. Acceptez la demande de connexion sur votre téléviseur
5. Utilisez la télécommande virtuelle pour contrôler votre TV

## Structure du projet

```
com.samsung.remote/
├── model/          # Modèles de données (SamsungTV, RemoteKey)
├── network/        # Services réseau (Discovery, WebSocket)
├── ui/             # Activités Android (MainActivity, PairingActivity, RemoteControlActivity)
├── adapter/        # Adaptateurs RecyclerView
└── util/           # Utilitaires (PreferencesManager)
```

## Protocole Samsung

L'application utilise le protocole WebSocket de Samsung sur le port 8002 :
- URL : `ws://<TV_IP>:8002/api/v2/channels/samsung.remote.control`
- Format des commandes : JSON
- Authentification : Token-based après appairage

## Licence

Ce projet est fourni à titre éducatif et personnel.
