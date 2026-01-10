# GitHub Actions Workflows

Ce dossier contient les workflows d'intégration continue (CI) pour le projet Samsung Remote.

## 📋 Workflows disponibles

### `build-apk.yml` - Compilation automatique de l'APK

Ce workflow compile automatiquement l'application Android et génère un fichier APK téléchargeable.

#### 🚀 Déclencheurs

Le workflow se déclenche dans les cas suivants :

1. **Push sur les branches** :
   - `claude/**` (toutes les branches de développement Claude)
   - `main` / `master`
   - `develop`

2. **Pull Request** vers :
   - `main` / `master`
   - `develop`

3. **Tags de version** :
   - `v*` (ex: `v1.0.0`, `v1.2.3`)

4. **Déclenchement manuel** :
   - Via l'interface GitHub Actions (bouton "Run workflow")

#### 📦 Ce que fait le workflow

1. **Récupération du code** : Clone le repository
2. **Configuration Java** : Installe JDK 17 (Temurin)
3. **Cache Gradle** : Utilise le cache pour accélérer les builds
4. **Compilation** : Exécute `./gradlew assembleDebug`
5. **Renommage APK** : Renomme l'APK avec le numéro de version
6. **Upload Artifact** : Met l'APK à disposition en téléchargement (conservé 30 jours)
7. **Création Release** (si tag) : Crée automatiquement une release GitHub avec l'APK
8. **Commentaire PR** (si pull request) : Ajoute un commentaire avec le lien de téléchargement

#### 📥 Comment télécharger l'APK compilé

##### Méthode 1 : Via les Actions

1. Allez sur l'onglet **Actions** du repository GitHub
2. Cliquez sur le dernier workflow "Build Android APK" terminé (✅ vert)
3. Faites défiler jusqu'à la section **Artifacts**
4. Cliquez sur **SamsungRemote-APK** pour télécharger
5. Décompressez le fichier ZIP
6. Installez l'APK sur votre appareil Android

##### Méthode 2 : Via les Releases (si tag créé)

1. Allez sur l'onglet **Releases** du repository
2. Cliquez sur la dernière release
3. Téléchargez directement le fichier APK sous "Assets"
4. Installez sur votre appareil Android

#### 🏷️ Créer une release avec l'APK

Pour créer une release automatique avec l'APK :

```bash
# Créez un tag avec le numéro de version
git tag v1.0.0

# Poussez le tag sur GitHub
git push origin v1.0.0
```

Le workflow va automatiquement :
- Compiler l'APK
- Créer une release GitHub
- Attacher l'APK à la release
- Générer les notes de release

#### ⚙️ Configuration

Le workflow utilise :
- **JDK** : Temurin 17 (Eclipse Adoptium)
- **Runner** : `ubuntu-latest`
- **Cache** : Gradle dependencies et build outputs
- **Retention** : 30 jours pour les artifacts

#### 🔧 Personnalisation

Pour modifier le comportement du workflow, éditez `.github/workflows/build-apk.yml` :

**Changer les branches surveillées :**
```yaml
on:
  push:
    branches:
      - 'votre-branche'
```

**Changer la durée de rétention des artifacts :**
```yaml
- name: Upload APK as artifact
  uses: actions/upload-artifact@v4
  with:
    retention-days: 90  # Au lieu de 30
```

**Compiler en mode Release au lieu de Debug :**
```yaml
- name: Build with Gradle
  run: ./gradlew assembleRelease --stacktrace
```

#### 📊 Temps de compilation

- **Premier build** : ~5-7 minutes (téléchargement des dépendances)
- **Builds suivants** : ~2-3 minutes (avec cache Gradle)

#### 🐛 Débogage

Si le workflow échoue :

1. **Consultez les logs** : Cliquez sur le workflow échoué et examinez chaque étape
2. **Erreurs courantes** :
   - Erreur Gradle : Vérifiez `build.gradle` et les dépendances
   - Erreur de compilation : Problème dans le code source
   - Erreur de cache : Supprimez le cache Gradle (Settings → Actions → Caches)

3. **Relancer le workflow** : Cliquez sur "Re-run jobs" dans l'interface

#### 💡 Astuces

**Déclencher manuellement un build :**
1. Allez sur Actions → Build Android APK
2. Cliquez sur "Run workflow"
3. Sélectionnez la branche
4. Cliquez sur "Run workflow"

**Télécharger l'APK sans GitHub :**
Utilisez l'API GitHub Actions :
```bash
gh run download <run-id> --name SamsungRemote-APK
```

**Voir tous les artifacts disponibles :**
```bash
gh run list --workflow=build-apk.yml
```

#### 🔐 Permissions requises

Le workflow nécessite les permissions suivantes (déjà configurées par défaut) :
- `contents: write` - Pour créer des releases
- `pull-requests: write` - Pour commenter sur les PRs
- `actions: read` - Pour lire les informations du workflow

#### 📝 Notes

- Les APK générés sont en mode **DEBUG** et ne sont pas optimisés
- Pour une version de production, créez un workflow `build-release.yml` avec signing
- Les artifacts sont automatiquement supprimés après 30 jours
- Le cache Gradle est partagé entre les workflows pour accélérer les builds

---

## 🆘 Support

En cas de problème avec les workflows :
1. Vérifiez les logs dans l'onglet Actions
2. Consultez la documentation GitHub Actions : https://docs.github.com/actions
3. Ouvrez une issue sur le repository
