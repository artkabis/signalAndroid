# Guide de Compilation - Samsung Remote App

Ce document explique comment compiler l'application Samsung Remote pour Android et obtenir un fichier APK installable.

## 📋 Prérequis

### Option 1 : Android Studio (Recommandé pour les débutants)
- **Android Studio** : [Télécharger ici](https://developer.android.com/studio)
- **Java JDK 17+** : Inclus avec Android Studio
- **Connexion Internet** : Pour télécharger les dépendances

### Option 2 : Ligne de commande
- **Java JDK 17+** : [Télécharger ici](https://adoptium.net/)
- **Android SDK** : [Télécharger ici](https://developer.android.com/studio#command-tools)
- **Git** : [Télécharger ici](https://git-scm.com/)

---

## 🚀 Méthode 1 : Compilation avec Android Studio

### Étape 1 : Récupérer le code source

```bash
# Cloner le dépôt
git clone <URL_DU_DEPOT>
cd signalAndroid

# Se placer sur la branche de développement
git checkout claude/android-samsung-remote-app-frQCQ
```

### Étape 2 : Ouvrir le projet

1. Lancez **Android Studio**
2. Cliquez sur **File → Open** (ou **Open an existing Android Studio project**)
3. Naviguez vers le dossier `signalAndroid` et sélectionnez-le
4. Cliquez sur **OK**

### Étape 3 : Synchronisation Gradle

- Android Studio va automatiquement détecter le projet et lancer la synchronisation Gradle
- Une barre de progression apparaîtra en bas de l'écran
- **Première compilation** : Cela peut prendre 5-10 minutes (téléchargement des dépendances)
- **Compilations suivantes** : Beaucoup plus rapide (1-2 minutes)

> ⚠️ Si des erreurs apparaissent, vérifiez que vous avez bien installé le SDK Android niveau 34 (Android 14)

### Étape 4 : Compiler l'APK Debug

**Option A : Via le menu**
1. Cliquez sur **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Attendez la fin de la compilation
3. Une notification apparaîtra en bas à droite : **"APK(s) generated successfully"**
4. Cliquez sur **locate** pour ouvrir le dossier contenant l'APK

**Option B : Via le terminal intégré**
1. Ouvrez le terminal dans Android Studio (en bas)
2. Exécutez :
   ```bash
   ./gradlew assembleDebug
   ```

### Étape 5 : Récupérer l'APK

L'APK se trouve à cet emplacement :
```
app/build/outputs/apk/debug/app-debug.apk
```

**Taille approximative** : 5-8 MB

---

## 🛠️ Méthode 2 : Compilation en ligne de commande

### Configuration de l'environnement (première fois)

**Sur Linux/macOS :**
```bash
# Installer Java 17
sudo apt install openjdk-17-jdk  # Ubuntu/Debian
brew install openjdk@17           # macOS

# Définir JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Définir ANDROID_HOME (adapter le chemin)
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$PATH
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
```

**Sur Windows (PowerShell) :**
```powershell
# Définir JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Définir ANDROID_HOME
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:PATH"
```

### Compilation

```bash
# 1. Cloner le projet
git clone <URL_DU_DEPOT>
cd signalAndroid
git checkout claude/android-samsung-remote-app-frQCQ

# 2. Donner les permissions d'exécution (Linux/macOS uniquement)
chmod +x gradlew

# 3. Compiler l'APK debug
./gradlew assembleDebug          # Linux/macOS
gradlew.bat assembleDebug        # Windows

# 4. L'APK est généré ici :
# app/build/outputs/apk/debug/app-debug.apk
```

### Commandes Gradle utiles

```bash
# Nettoyer le projet
./gradlew clean

# Compiler en mode release (APK signé nécessaire pour production)
./gradlew assembleRelease

# Lancer les tests
./gradlew test

# Voir toutes les tâches disponibles
./gradlew tasks
```

---

## 📱 Installation sur votre téléphone

### Méthode 1 : Via câble USB (ADB)

```bash
# Activer le mode développeur sur votre téléphone Android :
# Paramètres → À propos du téléphone → Appuyez 7 fois sur "Numéro de build"

# Activer le débogage USB :
# Paramètres → Options pour les développeurs → Débogage USB

# Installer l'APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Ou via Android Studio : Run → Run 'app'
```

### Méthode 2 : Transfert manuel

1. Copiez le fichier `app-debug.apk` sur votre téléphone (via USB, Bluetooth, email, etc.)
2. Sur le téléphone, ouvrez le gestionnaire de fichiers
3. Naviguez vers le fichier APK
4. Appuyez dessus pour l'installer

> ⚠️ Vous devrez peut-être autoriser l'installation d'applications depuis des sources inconnues :
> **Paramètres → Sécurité → Sources inconnues** (ou **Installer des apps inconnues**)

---

## 🐛 Résolution de problèmes

### Erreur : "SDK location not found"

**Solution :**
Créez un fichier `local.properties` à la racine du projet :

```properties
sdk.dir=/chemin/vers/votre/Android/Sdk
```

Exemples de chemins :
- **Windows** : `C\:\\Users\\VotreNom\\AppData\\Local\\Android\\Sdk`
- **macOS** : `/Users/VotreNom/Library/Android/sdk`
- **Linux** : `/home/VotreNom/Android/Sdk`

### Erreur : "Java version incompatible"

**Solution :**
Vérifiez votre version de Java :
```bash
java -version  # Doit afficher Java 17 ou supérieur
```

Si vous avez plusieurs versions, sélectionnez Java 17 :
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

### Erreur : "Failed to download dependencies"

**Solution :**
1. Vérifiez votre connexion Internet
2. Essayez de nettoyer et reconstruire :
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug --refresh-dependencies
   ```

### Erreur : "Gradle sync failed"

**Solution :**
Dans Android Studio :
1. **File → Invalidate Caches / Restart**
2. Redémarrer Android Studio
3. Réessayer la synchronisation

---

## 📦 Structure des fichiers de sortie

Après compilation, voici la structure des fichiers générés :

```
app/build/
├── outputs/
│   ├── apk/
│   │   ├── debug/
│   │   │   └── app-debug.apk           ← APK à installer
│   │   └── release/
│   │       └── app-release-unsigned.apk
│   └── logs/
└── intermediates/
```

---

## 🔐 Compilation en mode Release (pour publication)

Pour créer une APK signée pour le Play Store :

### 1. Créer un keystore

```bash
keytool -genkey -v -keystore samsung-remote.keystore \
  -alias samsung-remote -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Configurer le signing dans `app/build.gradle`

```gradle
android {
    signingConfigs {
        release {
            storeFile file("../samsung-remote.keystore")
            storePassword "VOTRE_MOT_DE_PASSE"
            keyAlias "samsung-remote"
            keyPassword "VOTRE_MOT_DE_PASSE"
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

### 3. Compiler

```bash
./gradlew assembleRelease
```

L'APK signée sera dans : `app/build/outputs/apk/release/app-release.apk`

---

## 📊 Informations sur le build

- **Taille APK Debug** : ~6-8 MB
- **Taille APK Release** : ~4-5 MB (avec minification)
- **Minimum Android** : Android 7.0 (API 24)
- **Target Android** : Android 14 (API 34)
- **Langage** : Kotlin
- **Build tools** : Gradle 8.2, Android Gradle Plugin 8.2.0

---

## ✅ Vérification de l'APK

Pour vérifier les informations de votre APK :

```bash
# Voir les informations de l'APK
aapt dump badging app/build/outputs/apk/debug/app-debug.apk

# Voir les permissions
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk

# Voir la taille
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

---

## 📞 Support

Si vous rencontrez des problèmes :
1. Vérifiez que vous avez la dernière version du code
2. Assurez-vous que toutes les dépendances sont à jour
3. Consultez les logs d'erreur dans Android Studio
4. Nettoyez et reconstruisez le projet

---

## 🎉 Félicitations !

Vous avez maintenant compilé votre propre version de l'application Samsung Remote !

Pour toute question ou problème, n'hésitez pas à ouvrir une issue sur le dépôt Git.
