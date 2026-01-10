# 🚀 Compilation Automatique avec GitHub Actions

Ce projet utilise **GitHub Actions** pour compiler automatiquement l'application Android et générer un fichier APK téléchargeable - **sans avoir besoin d'Android Studio sur votre ordinateur** !

## ✨ Avantages

✅ **Aucune installation requise** - Pas besoin d'Android Studio, SDK Android, ou Java
✅ **Compilation dans le cloud** - GitHub fait tout le travail
✅ **APK téléchargeable** - Disponible directement depuis GitHub
✅ **Automatique** - Se déclenche à chaque push
✅ **Gratuit** - Inclus avec GitHub (2000 minutes/mois sur les repos publics)

---

## 📥 Comment télécharger l'APK compilé ?

### Méthode 1 : Via GitHub Actions (Recommandé)

1. **Allez sur GitHub** : Ouvrez votre repository sur GitHub.com

2. **Cliquez sur l'onglet "Actions"**
   ![Actions Tab](https://docs.github.com/assets/images/help/repository/actions-tab.png)

3. **Sélectionnez le workflow "Build Android APK"**
   Dans la liste de gauche, cliquez sur "Build Android APK"

4. **Choisissez le dernier build réussi** (✅ avec une coche verte)

5. **Téléchargez l'artifact**
   - Faites défiler jusqu'à la section **"Artifacts"** (en bas de page)
   - Cliquez sur **"SamsungRemote-APK"**
   - Un fichier ZIP sera téléchargé

6. **Décompressez et installez**
   - Décompressez le fichier ZIP
   - Vous obtiendrez un fichier `.apk`
   - Transférez-le sur votre téléphone Android
   - Installez-le (autorisez les sources inconnues si demandé)

### Méthode 2 : Via les Releases GitHub

Si vous créez des tags de version (ex: `v1.0.0`), l'APK sera automatiquement attaché aux releases :

1. **Allez sur l'onglet "Releases"** du repository
2. **Cliquez sur la dernière release**
3. **Téléchargez l'APK** directement depuis "Assets"
4. **Installez sur votre appareil**

---

## 🔄 Comment déclencher une nouvelle compilation ?

### Automatique (Push)

La compilation se déclenche automatiquement quand vous :
- **Poussez du code** sur les branches `main`, `develop`, ou `claude/**`
- **Créez une Pull Request**
- **Créez un tag de version**

```bash
# Exemple : pousser du code déclenche automatiquement le build
git add .
git commit -m "Nouvelle fonctionnalité"
git push

# Attendez 3-5 minutes, puis allez sur Actions pour télécharger l'APK
```

### Manuel (Interface GitHub)

Vous pouvez aussi déclencher manuellement une compilation :

1. Allez sur **Actions → Build Android APK**
2. Cliquez sur le bouton **"Run workflow"** (à droite)
3. Sélectionnez la branche
4. Cliquez sur **"Run workflow"** (vert)

---

## 🏷️ Créer une Release avec l'APK

Pour créer une release officielle avec l'APK :

```bash
# 1. Créez un tag avec le numéro de version
git tag v1.0.0

# 2. Poussez le tag sur GitHub
git push origin v1.0.0
```

GitHub Actions va automatiquement :
- ✅ Compiler l'APK
- ✅ Créer une release GitHub
- ✅ Attacher l'APK à la release
- ✅ Générer les notes de version

L'APK sera téléchargeable depuis l'onglet **Releases** !

---

## 📊 Suivre la progression de la compilation

1. Allez sur **Actions**
2. Vous verrez les workflows en cours avec un point orange 🟠
3. Cliquez sur un workflow pour voir les détails en temps réel
4. Les étapes s'affichent avec leur progression

**Temps de compilation :**
- Premier build : ~5-7 minutes
- Builds suivants : ~2-3 minutes (grâce au cache)

---

## 🎯 Utilisation avancée (Ligne de commande)

Si vous avez le **GitHub CLI** (`gh`) installé :

### Télécharger directement l'APK

```bash
# Lister les workflows récents
gh run list --workflow=build-apk.yml

# Télécharger l'artifact du dernier run
gh run download --name SamsungRemote-APK

# L'APK sera dans le dossier courant
```

### Déclencher un build depuis le terminal

```bash
# Lancer le workflow manuellement
gh workflow run build-apk.yml --ref main

# Voir le statut
gh run watch
```

### Voir les logs en temps réel

```bash
# Voir les logs du dernier run
gh run view --log

# Suivre en temps réel
gh run view --log-failed
```

---

## ⚙️ Configuration du workflow

Le workflow est défini dans `.github/workflows/build-apk.yml`

### Déclencheurs actuels

```yaml
on:
  push:
    branches:
      - 'claude/**'      # Toutes branches claude/
      - 'main'           # Branche principale
      - 'master'         # Alternative branche principale
      - 'develop'        # Branche de développement
    tags:
      - 'v*'            # Tags de version (v1.0.0, v2.1.3, etc.)
  pull_request:         # Sur toutes les PRs
  workflow_dispatch:    # Déclenchement manuel
```

### Modifier les déclencheurs

Pour changer les branches qui déclenchent le build, éditez `.github/workflows/build-apk.yml` :

```yaml
on:
  push:
    branches:
      - 'votre-branche-custom'
```

---

## 🔒 APK Debug vs Release

### APK Debug (actuel)

- ✅ Non optimisé, avec symboles de debug
- ✅ Facile à déboguer
- ✅ Taille plus grande (~6-8 MB)
- ✅ **Utilisé par le workflow actuel**

### APK Release (production)

Pour créer un APK optimisé pour la production :

1. Configurez le signing (keystore)
2. Créez un workflow `build-release.yml`
3. Utilisez `assembleRelease` au lieu de `assembleDebug`

---

## 📁 Structure des Artifacts

Quand vous téléchargez l'artifact, vous obtiendrez :

```
SamsungRemote-APK.zip
└── SamsungRemote-1.0-debug.apk
```

Le nom de l'APK inclut automatiquement le numéro de version.

---

## 🐛 Résolution de problèmes

### Le workflow échoue

1. **Cliquez sur le workflow échoué** dans Actions
2. **Examinez les logs** de chaque étape
3. **Erreurs courantes** :
   - Erreur Gradle → Problème dans `build.gradle`
   - Erreur de compilation → Erreur dans le code Kotlin
   - Erreur de dépendances → Problème de réseau GitHub

4. **Solutions** :
   - Relancez le workflow (bouton "Re-run jobs")
   - Supprimez le cache (Settings → Actions → Caches)
   - Vérifiez que le code compile localement

### L'artifact n'apparaît pas

- Vérifiez que le workflow s'est terminé avec succès (✅)
- Les artifacts apparaissent seulement après la fin complète
- Ils sont conservés 30 jours maximum

### Je ne peux pas télécharger l'artifact

- Vous devez être connecté à GitHub
- Sur les repos privés, vous devez avoir accès au repo
- Les artifacts sont au format ZIP, décompressez-les

---

## 💡 Astuces et bonnes pratiques

### Optimiser les temps de build

Le workflow utilise déjà :
- ✅ Cache Gradle (gain de ~3 minutes)
- ✅ JDK optimisé (Temurin)
- ✅ Compilation parallèle

### Recevoir des notifications

GitHub peut vous notifier quand un workflow échoue :
1. **Settings** (votre profil) → **Notifications**
2. Activez **"Actions"**
3. Vous recevrez un email en cas d'échec

### Badge de build

Ajoutez un badge dans votre README.md pour afficher le statut du build :

```markdown
![Build Status](https://github.com/VOTRE_USER/signalAndroid/actions/workflows/build-apk.yml/badge.svg)
```

---

## 🎓 Ressources

- [Documentation GitHub Actions](https://docs.github.com/actions)
- [GitHub CLI](https://cli.github.com/)
- [Workflow Syntax](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions)

---

## 📞 Support

Si vous rencontrez des problèmes :
1. Consultez les logs du workflow
2. Lisez la documentation `.github/workflows/README.md`
3. Ouvrez une issue sur le repository

---

## 🎉 Résumé rapide

```bash
# 1. Poussez votre code
git push

# 2. Allez sur GitHub → Actions
# 3. Attendez 3-5 minutes
# 4. Téléchargez l'artifact "SamsungRemote-APK"
# 5. Décompressez et installez l'APK
# 6. Profitez ! 🎊
```

**C'est tout !** Pas besoin d'Android Studio, le cloud fait tout le travail ! 🚀
