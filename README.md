# NavigateTS

Application Android d'assistance à la navigation pour personnes malvoyantes,

## 📋 Description

NavigateTS est une application mobile Android conçue pour aider les personnes malvoyantes à naviguer dans leur environnement. L'application utilise la vision par ordinateur, la reconnaissance vocale et l'intelligence artificielle pour fournir des informations contextuelles sur l'environnement de l'utilisateur.

## ✨ Fonctionnalités principales

### 🎤 Reconnaissance vocale
- Commandes vocales pour contrôler l'application
- Navigation mains-libres
- Retour vocal (Text-to-Speech) pour les résultats

### 📸 Détection d'objets
- Détection en temps réel via la caméra
- Identification d'objets dans l'environnement
- Recherche d'objets spécifiques

### 🪑 Détection de chaises
- Détection de chaises libres ou occupées
- Guidage vocal pour trouver une chaise disponible
- Indications directionnelles (gauche, droite, devant)

### 🔍 Modes de recherche
- **Recherche en direct** : Analyse en temps réel de la caméra
- **Recherche vidéo** : Analyse d'une séquence vidéo enregistrée
- Choix du mode de recherche via interface ou commande vocale

### 🖼️ Description de scène
- **Description photo** : Capture et analyse d'une image fixe
- **Description vidéo** : Analyse continue de l'environnement
- Utilisation de modèles LLM (OpenAI/Gemini) pour générer des descriptions détaillées

### 🗺️ Navigation
- Interface de navigation intuitive
- Visualisation des résultats de détection
- Retour haptique (vibrations)

## 🛠️ Technologies utilisées

### Framework & Langage
- **Kotlin** - Langage principal
- **Android SDK** (API 33-36)
- **Gradle** - Système de build

### Bibliothèques principales
- **AndroidX Camera** (1.3.1) - Gestion de la caméra
- **TensorFlow Lite** (2.14.0) - Détection d'objets et vision par ordinateur
- **Navigation Component** - Navigation entre fragments
- **Material Design** - Interface utilisateur moderne
- **OkHttp** (4.12.0) - Requêtes réseau pour les API LLM
- **PhotoView** - Visualisation d'images avec zoom

### Intelligence Artificielle
- **TensorFlow Lite Task Vision** - Modèles de vision pré-entraînés
- **OpenAI API** - Génération de descriptions textuelles
- **Gemini API** - Alternative pour la génération de descriptions

## 📦 Structure du projet

```
navigatets-MOISE-TEST3/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/ca/ets/navigatets/
│   │       │   ├── ui/
│   │       │   │   ├── home/          # Écran d'accueil avec reconnaissance vocale
│   │       │   │   ├── navigate/      # Navigation et visualisation
│   │       │   │   ├── search/        # Modes de recherche
│   │       │   │   └── notifications/ # Notifications
│   │       │   ├── describe/          # Description de scène (photo/vidéo)
│   │       │   ├── objectsDetection/  # Détection d'objets et chaises
│   │       │   ├── llm/              # Client pour API LLM
│   │       │   └── MainActivity.kt
│   │       └── res/
│   │           ├── layout/           # Layouts XML
│   │           ├── values/           # Strings, colors, themes
│   │           └── drawable/         # Ressources graphiques
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## 🚀 Installation

### Prérequis
- Android Studio (dernière version recommandée)
- JDK 11 ou supérieur
- Android SDK avec API 33 minimum
- Appareil Android ou émulateur avec API 33+

### Configuration

1. **Cloner le projet**
   ```bash
   git clone <url-du-repo>
   cd navigatets-MOISE-TEST3
   ```

2. **Configurer les clés API**
   
   Copier le fichier exemple et ajouter vos clés :
   ```bash
   cp local.properties.example local.properties
   ```
   
   Puis éditer `local.properties` et remplacer les valeurs par vos vraies clés API :
   ```properties
   sdk.dir=C:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
   OPENAI_API_KEY=votre_clé_openai_ici
   GEMINI_API_KEY=votre_clé_gemini_ici
   ```
   
   **⚠️ IMPORTANT** : Le fichier `local.properties` est déjà dans `.gitignore` et ne sera **jamais** envoyé sur GitHub. Vos clés sont en sécurité.

3. **Synchroniser les dépendances**
   ```bash
   ./gradlew build
   ```

4. **Lancer l'application**
   - Ouvrir le projet dans Android Studio
   - Connecter un appareil Android ou lancer un émulateur
   - Cliquer sur "Run" (▶️)

## 📱 Utilisation

### Permissions requises
L'application demande les permissions suivantes :
- **Caméra** : Pour la détection d'objets et la description de scène
- **Microphone** : Pour la reconnaissance vocale
- **Internet** : Pour les appels aux API LLM
- **Vibration** : Pour le retour haptique

### Navigation dans l'application

1. **Écran d'accueil (Home)**
   - Utiliser la reconnaissance vocale pour des commandes
   - Rechercher des lieux ou des objets
   - Accéder aux différentes fonctionnalités

2. **Mode Navigation**
   - Choisir entre recherche en direct ou vidéo
   - Pointer la caméra vers l'environnement
   - Recevoir des informations vocales

3. **Détection de chaises**
   - Activer la détection depuis le menu
   - Pointer la caméra vers des chaises
   - Suivre les indications vocales pour trouver une chaise libre

4. **Description de scène**
   - Choisir entre photo ou vidéo
   - Capturer l'environnement
   - Écouter la description générée par IA

## 🔧 Configuration avancée

### Modèles TensorFlow Lite
Les modèles de détection doivent être placés dans `app/src/main/assets/`. Les modèles supportés incluent :
- Détection d'objets générique
- Détection de chaises spécifique

### API LLM
L'application supporte deux fournisseurs :
- **OpenAI** (GPT-4 Vision)
- **Gemini** (Google)

Configurer les clés API dans `local.properties` comme indiqué ci-dessus.

## 🧪 Tests

### Exécuter les tests unitaires
```bash
./gradlew test
```

### Exécuter les tests d'instrumentation
```bash
./gradlew connectedAndroidTest
```

## 📄 Licence

Ce projet est développé pour l'École de technologie supérieure (ÉTS).

## 👥 Contributeurs

Projet développé dans le cadre d'un projet de recherche à l'ÉTS.

## 🐛 Problèmes connus

- L'application nécessite un appareil avec caméra fonctionnelle
- Les API LLM nécessitent une connexion Internet active
- Performances optimales sur Android 13 (API 33) et supérieur

## 📞 Support

Pour toute question ou problème, veuillez contacter l'équipe de développement de l'ÉTS.

## 🔒 Sécurité - Publication sur GitHub

### ✅ Fichiers déjà protégés

Le projet est **prêt pour GitHub**. Les fichiers sensibles sont déjà protégés :

- ✅ `local.properties` - Contient vos clés API (déjà dans `.gitignore`)
- ✅ `.gradle/` - Fichiers de build (déjà dans `.gitignore`)
- ✅ `build/` - Fichiers compilés (déjà dans `.gitignore`)

### 📋 Checklist avant de publier

1. **Vérifier qu'aucune clé n'est visible** :
   ```bash
   git status
   ```
   Le fichier `local.properties` ne doit **PAS** apparaître dans la liste.

2. **Vérifier le contenu avant commit** :
   ```bash
   git add .
   git status
   ```
   Assurez-vous que seuls les fichiers souhaités sont ajoutés.

3. **Premier commit** :
   ```bash
   git init
   git add .
   git commit -m "Initial commit - NavigateTS"
   ```

4. **Publier sur GitHub** :
   ```bash
   git remote add origin https://github.com/votre-username/navigatets.git
   git branch -M main
   git push -u origin main
   ```

### 🔑 Pour les autres développeurs

Les nouveaux développeurs devront :
1. Cloner le projet
2. Copier `local.properties.example` vers `local.properties`
3. Ajouter leurs propres clés API OpenAI et Gemini

**Note** : Le fichier `local.properties.example` est inclus dans le dépôt pour documenter les clés nécessaires, mais ne contient aucune vraie clé.

---

**Version** : 1.0  
**Dernière mise à jour** : 2026
